package com.example.anemoi.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.example.anemoi.R
import com.example.anemoi.data.LocationItem
import com.example.anemoi.util.dataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class WeatherWidgetConfigureActivity : ComponentActivity() {
    private val json = Json { ignoreUnknownKeys = true }

    private val favoritesKey = stringPreferencesKey("favorites_json")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_widget_configure)
        applySystemBarInsets()

        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val titleView = findViewById<TextView>(R.id.widget_config_title)
        val listView = findViewById<ListView>(R.id.widget_config_locations)
        titleView.text = getString(R.string.widget_config_title)

        lifecycleScope.launch {
            val options = buildOptions()
            val adapter = object : ArrayAdapter<WidgetConfigOption>(
                this@WeatherWidgetConfigureActivity,
                android.R.layout.simple_list_item_1,
                options
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val text = view.findViewById<TextView>(android.R.id.text1)
                    val option = getItem(position)
                    text.text = option?.label.orEmpty()
                    text.setTypeface(null, if (option?.isCurrent == true) Typeface.BOLD else Typeface.NORMAL)
                    return view
                }
            }
            listView.adapter = adapter
            listView.setOnItemClickListener { _, _, position, _ ->
                val selected = options.getOrNull(position) ?: return@setOnItemClickListener
                if (selected.isCurrent) {
                    WidgetLocationStore.saveCurrentLocationSelection(this@WeatherWidgetConfigureActivity, appWidgetId)
                } else {
                    val fixed = selected.location ?: return@setOnItemClickListener
                    WidgetLocationStore.saveLocation(this@WeatherWidgetConfigureActivity, appWidgetId, fixed)
                }
                WeatherWidgetProvider.requestUpdate(this@WeatherWidgetConfigureActivity)

                val resultIntent = android.content.Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.widget_config_root) ?: return
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = initialLeft + bars.left,
                top = initialTop + bars.top,
                right = initialRight + bars.right,
                bottom = initialBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private suspend fun buildOptions(): List<WidgetConfigOption> {
        val options = mutableListOf(
            WidgetConfigOption(
                label = getString(R.string.widget_current_location_option),
                location = null,
                isCurrent = true
            )
        )

        val fixedLocations = loadCandidateLocations()
        options += fixedLocations.map { location ->
            WidgetConfigOption(
                label = location.displayName,
                location = location,
                isCurrent = false
            )
        }

        return options
    }

    private suspend fun loadCandidateLocations(): List<LocationItem> {
        val prefs = dataStore.data.firstOrNull() ?: return emptyList()
        val encodedFavorites = prefs[favoritesKey] ?: return emptyList()
        return runCatching { json.decodeFromString<List<LocationItem>>(encodedFavorites) }
            .getOrDefault(emptyList())
    }
}

private data class WidgetConfigOption(
    val label: String,
    val location: LocationItem?,
    val isCurrent: Boolean
)
