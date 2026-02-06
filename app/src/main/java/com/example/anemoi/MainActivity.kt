package com.example.anemoi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.anemoi.ui.MainContent
import com.example.anemoi.ui.theme.AnemoiTheme
import com.example.anemoi.viewmodel.WeatherViewModel
import com.example.anemoi.viewmodel.WeatherViewModelFactory
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {
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
        
        enableEdgeToEdge()
        
        setContent {
            val context = LocalContext.current
            val viewModel: WeatherViewModel = viewModel(
                factory = WeatherViewModelFactory(context.applicationContext)
            )
            
            AnemoiTheme {
                MainContent(viewModel)
            }
        }
    }
}
