package com.example.anemoi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.anemoi.ui.MainContent
import com.example.anemoi.ui.theme.AnemoiTheme
import com.example.anemoi.viewmodel.WeatherViewModel
import com.example.anemoi.viewmodel.WeatherViewModelFactory
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var weatherViewModel: WeatherViewModel

    companion object {
        const val EXTRA_WIDGET_LOCATION_LAT = "com.example.anemoi.extra.WIDGET_LOCATION_LAT"
        const val EXTRA_WIDGET_LOCATION_LON = "com.example.anemoi.extra.WIDGET_LOCATION_LON"
        const val EXTRA_WIDGET_LOCATION_IS_CURRENT = "com.example.anemoi.extra.WIDGET_LOCATION_IS_CURRENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // OSMDroid configuration
        val conf = Configuration.getInstance()
        conf.userAgentValue = "AnemoiForecast/1.0"
        
        // Boost cache settings for nearly instant tile loading
        conf.cacheMapTileCount = 500 // Increased memory cache
        conf.tileFileSystemCacheMaxBytes = 1024L * 1024 * 1000 // 1GB disk cache
        conf.tileFileSystemCacheTrimBytes = 1024L * 1024 * 800 // Trim to 800MB
        
        // Ensure the cache directory is properly set and exists
        val cacheDir = File(cacheDir, "osmdroid_tiles")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        conf.osmdroidTileCache = cacheDir

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
