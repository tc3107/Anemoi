package com.tudorc.anemoi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.tudorc.anemoi.ui.MainContent
import com.tudorc.anemoi.ui.theme.AnemoiTheme
import com.tudorc.anemoi.viewmodel.WeatherViewModel
import com.tudorc.anemoi.viewmodel.WeatherViewModelFactory

class MainActivity : ComponentActivity() {
    private lateinit var weatherViewModel: WeatherViewModel

    companion object {
        const val EXTRA_WIDGET_LOCATION_LAT = "com.tudorc.anemoi.extra.WIDGET_LOCATION_LAT"
        const val EXTRA_WIDGET_LOCATION_LON = "com.tudorc.anemoi.extra.WIDGET_LOCATION_LON"
        const val EXTRA_WIDGET_LOCATION_IS_CURRENT = "com.tudorc.anemoi.extra.WIDGET_LOCATION_IS_CURRENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        weatherViewModel = ViewModelProvider(
            this,
            WeatherViewModelFactory(applicationContext)
        )[WeatherViewModel::class.java]
        handleWidgetLocationIntent(intent)
        
        enableEdgeToEdge()
        
        setContent {
            AnemoiTheme {
                MainContent(weatherViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetLocationIntent(intent)
    }

    private fun handleWidgetLocationIntent(intent: Intent?) {
        val launchIntent = intent ?: return
        val openAsCurrentLocationPage = launchIntent.getBooleanExtra(EXTRA_WIDGET_LOCATION_IS_CURRENT, false)
        if (!launchIntent.hasExtra(EXTRA_WIDGET_LOCATION_LAT) || !launchIntent.hasExtra(EXTRA_WIDGET_LOCATION_LON)) {
            if (openAsCurrentLocationPage) {
                weatherViewModel.openCurrentLocationPageFromWidget(applicationContext)
            }
            return
        }

        val lat = launchIntent.getDoubleExtra(EXTRA_WIDGET_LOCATION_LAT, Double.NaN)
        val lon = launchIntent.getDoubleExtra(EXTRA_WIDGET_LOCATION_LON, Double.NaN)
        if (lat.isNaN() || lon.isNaN()) {
            return
        }

        weatherViewModel.openLocationFromWidget(
            lat = lat,
            lon = lon,
            context = applicationContext,
            openAsCurrentLocationPage = openAsCurrentLocationPage
        )
    }
}
