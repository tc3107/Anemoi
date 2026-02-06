package com.example.anemoi.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File

@Composable
fun MapBackground(lat: Double, lon: Double, zoom: Float, blurStrength: Float, tintAlpha: Float) {
    val geoPoint = remember(lat, lon) { GeoPoint(lat, lon) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier
            .fillMaxSize()
            .blur(blurStrength.dp)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val mapCacheDir = File(ctx.cacheDir, "osmdroid")
                    Configuration.getInstance().osmdroidTileCache = mapCacheDir
                    
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(false)
                        setBuiltInZoomControls(false)
                        isClickable = false
                        isFocusable = false
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(zoom.toDouble())
                        controller.setCenter(geoPoint)
                        setOnTouchListener { _, _ -> true }
                        
                        val colorMatrix = ColorMatrix(floatArrayOf(
                            -0.6f, 0.0f, 0.0f, 0.0f, 150.0f, 
                            0.0f, -0.7f, 0.0f, 0.0f, 180.0f, 
                            0.0f, 0.0f, -1.0f, 0.0f, 255.0f, 
                            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
                        ))
                        val satMatrix = ColorMatrix()
                        satMatrix.setSaturation(0.5f)
                        colorMatrix.postConcat(satMatrix)
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(colorMatrix))
                    }
                },
                update = { view ->
                    view.controller.setZoom(zoom.toDouble())
                    view.controller.animateTo(geoPoint)
                }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08203E).copy(alpha = tintAlpha))
        )
    }
}
