package com.example.anemoi.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import com.example.anemoi.util.ObfuscationMode
import kotlinx.coroutines.delay
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.*

@Composable
fun MapBackground(
    lat: Double,
    lon: Double,
    zoom: Float,
    blurStrength: Float,
    tintAlpha: Float,
    obfuscationMode: ObfuscationMode = ObfuscationMode.PRECISE,
    gridKm: Double = 5.0,
    lastResponseCoords: Pair<Double, Double>? = null,
    responseAnimTrigger: Long = 0L,
    shouldAnimate: Boolean = true
) {
    // Current coords actually applied to MapView
    var appliedLat by remember { mutableDoubleStateOf(lat) }
    var appliedLon by remember { mutableDoubleStateOf(lon) }
    val appliedGeoPoint = remember(appliedLat, appliedLon) { GeoPoint(appliedLat, appliedLon) }
    
    // Animation cycle for the double-blip (2 seconds)
    val responseProgress = remember { Animatable(0f) }
    LaunchedEffect(responseAnimTrigger) {
        if (responseAnimTrigger > 0) {
            responseProgress.snapTo(0f)
            responseProgress.animateTo(1f, tween(2000, easing = LinearEasing))
        }
    }

    // Transition state for smooth location switching
    var showSnapshot by remember { mutableStateOf(false) }
    var blurTarget by remember { mutableFloatStateOf(0f) }
    var previousBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var isInitialLoad by remember { mutableStateOf(true) }
    val currentObfuscationMode by rememberUpdatedState(obfuscationMode)
    val currentGridKm by rememberUpdatedState(gridKm)
    val currentLastResponseCoords by rememberUpdatedState(lastResponseCoords)
    
    val snapshotAlpha by animateFloatAsState(
        targetValue = if (showSnapshot) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "snapshotAlpha"
    )
    
    val extraBlur by animateFloatAsState(
        targetValue = blurTarget,
        animationSpec = tween(durationMillis = 220),
        label = "extraBlur"
    )

    var isMapMoving by remember { mutableStateOf(false) }
    val movementHandler = remember { Handler(Looper.getMainLooper()) }
    val resetMovingRunnable = remember { Runnable { isMapMoving = false } }

    DisposableEffect(Unit) {
        onDispose {
            previousBitmap?.recycle()
            previousBitmap = null
        }
    }

    // Sequential Transition Logic: Snapshot/Blur In -> Move Hidden -> Wait for Tiles -> Snapshot Out -> Blur Out
    LaunchedEffect(lat, lon) {
        if (isInitialLoad) {
            // Avoid expensive startup transition effects on first render.
            blurTarget = 0f
            showSnapshot = false
            isInitialLoad = false
            return@LaunchedEffect
        }

        val currentView = mapViewInstance
        
        // 1. Snapshot of map is taken and overlaid to keep background the same.
        if (currentView != null && currentView.width > 0 && currentView.height > 0 && !isInitialLoad) {
            try {
                val bmp = Bitmap.createBitmap(currentView.width, currentView.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                currentView.draw(canvas)
                previousBitmap?.recycle()
                previousBitmap = bmp
                showSnapshot = true
            } catch (e: Throwable) {
                Log.e("MapBackground", "Snapshot capture failed", e)
            }
        }
        
        // 2. Apply a short, light blur to smooth transitions without large frame cost.
        blurTarget = 14f
        delay(220)
        
        // 3. The new map location is loaded (not in view)
        appliedLat = lat
        appliedLon = lon
        
        // 4. Actively check if tiles are fully loaded (visible ones, at least) and new map rendered.
        delay(180) // Grace period for map to start requesting tiles
        var ready = false
        var attempts = 0
        while (!ready && attempts < 20) { // Max ~4 seconds
            val tilesOverlay = mapViewInstance?.overlayManager?.tilesOverlay
            if (tilesOverlay != null) {
                // Parsing TileStates string as properties are often private in certain OSMDroid builds
                val s = tilesOverlay.tileStates.toString().lowercase()
                // Logic: NOT currently loading anything AND has at least one result (success or 404)
                val isLoading = (s.contains("loading=") && !s.contains("loading=0")) || 
                                (s.contains("(l)") && !s.contains("0(l)"))
                
                val hasResults = (s.contains("uptodate=") && !s.contains("uptodate=0")) || 
                                 (s.contains("notfound=") && !s.contains("notfound=0")) ||
                                 (s.contains("(u)") && !s.contains("0(u)")) || 
                                 (s.contains("(n)") && !s.contains("0(n)"))
                
                if (!isLoading && hasResults) {
                    ready = true
                }
            }
            if (!ready) {
                delay(200)
                attempts++
            }
        }
        delay(120) // Extra time for the final rendering cycle to complete
        
        // 5. The snapshot image is removed, revealing the new map image.
        showSnapshot = false
        delay(220)
        
        // 6. The blur is removed, resuming regular clarity.
        blurTarget = 0f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier
            .fillMaxSize()
            .blur((blurStrength + extraBlur).dp)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(false)
                        @Suppress("DEPRECATION")
                        setBuiltInZoomControls(false)
                        isClickable = false
                        isFocusable = false
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(zoom.toDouble())
                        controller.setCenter(appliedGeoPoint)
                        
                        val colorMatrix = ColorMatrix(floatArrayOf(
                            -0.6f, 0.0f, 0.0f, 0.0f, 150.0f, 
                            0.0f, -0.7f, 0.0f, 0.0f, 180.0f, 
                            0.0f, 0.0f, -1.0f, 0.0f, 255.0f, 
                            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
                        ))
                        val satMatrix = ColorMatrix()
                        satMatrix.setSaturation(0.5f)
                        colorMatrix.postConcat(satMatrix)
                        overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(colorMatrix))

                        addMapListener(object : MapListener {
                            private fun onMove() {
                                isMapMoving = true
                                movementHandler.removeCallbacks(resetMovingRunnable)
                                movementHandler.postDelayed(resetMovingRunnable, 500)
                            }
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                onMove()
                                return false
                            }
                            override fun onZoom(event: ZoomEvent?): Boolean {
                                onMove()
                                return false
                            }
                        })

                        overlays.add(object : Overlay() {
                            private val linePaint = Paint().apply {
                                color = android.graphics.Color.WHITE
                                isAntiAlias = true
                                strokeWidth = 3f
                                style = Paint.Style.STROKE
                            }

                            override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
                                if (shadow || isMapMoving || mapView.isAnimating || currentObfuscationMode != ObfuscationMode.GRID) return

                                val progress = responseProgress.value
                                val factor = if (progress > 0f && progress < 1f) sin(progress * PI.toFloat()) else 0f

                                linePaint.alpha = (factor * 150).toInt()
                                if (linePaint.alpha <= 0) return

                                val projection = mapView.projection
                                val bounds = projection.boundingBox
                                val degreesPerKmLat = 1.0 / 110.574
                                val deltaLatDeg = currentGridKm * degreesPerKmLat
                                val latRad = appliedLat * PI / 180.0
                                val cosLat = max(abs(cos(latRad)), 0.1)
                                val degreesPerKmLon = 1.0 / (111.320 * cosLat)
                                val deltaLonDeg = currentGridKm * degreesPerKmLon
                                val originLat = -90.0
                                val originLon = -180.0

                                val startI = floor((bounds.latSouth - originLat) / deltaLatDeg).toInt()
                                val endI = ceil((bounds.latNorth - originLat) / deltaLatDeg).toInt()
                                val startJ = floor((bounds.lonWest - originLon) / deltaLonDeg).toInt()
                                val endJ = ceil((bounds.lonEast - originLon) / deltaLonDeg).toInt()

                                val p1 = Point()
                                val p2 = Point()
                                for (i in startI..endI) {
                                    val l = originLat + i * deltaLatDeg
                                    projection.toPixels(GeoPoint(l, bounds.lonWest), p1)
                                    projection.toPixels(GeoPoint(l, bounds.lonEast), p2)
                                    canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), linePaint)
                                }
                                for (j in startJ..endJ) {
                                    val l = originLon + j * deltaLonDeg
                                    projection.toPixels(GeoPoint(bounds.latSouth, l), p1)
                                    projection.toPixels(GeoPoint(bounds.latNorth, l), p2)
                                    canvas.drawLine(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), linePaint)
                                }
                            }
                        })

                        overlays.add(object : Overlay() {
                            private val blipPaint = Paint().apply {
                                color = "#A5D6A7".toColorInt()
                                isAntiAlias = true
                                style = Paint.Style.FILL
                            }

                            override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
                                if (shadow) return
                                val coords = currentLastResponseCoords ?: return

                                val progress = responseProgress.value
                                if (progress <= 0f || progress >= 1f) return

                                val (bLat, bLon) = coords
                                val center = Point()
                                mapView.projection.toPixels(GeoPoint(bLat, bLon), center)

                                val p1 = (progress / 0.6f).coerceIn(0f, 1f)
                                if (p1 > 0 && p1 < 1f) {
                                    val alpha = (255 * (1f - p1)).toInt()
                                    val radius = 102f * p1
                                    blipPaint.alpha = alpha
                                    canvas.drawCircle(center.x.toFloat(), center.y.toFloat(), radius, blipPaint)
                                }

                                val start2 = 0.3f
                                val end2 = 0.9f
                                val p2 = ((progress - start2) / (end2 - start2)).coerceIn(0f, 1f)
                                if (p2 > 0 && p2 < 1f) {
                                    val alpha = (255 * (1f - p2)).toInt()
                                    val radius = 127.5f * p2
                                    blipPaint.alpha = alpha
                                    canvas.drawCircle(center.x.toFloat(), center.y.toFloat(), radius, blipPaint)
                                }
                            }
                        })
                        mapViewInstance = this
                    }
                },
                update = { view ->
                    view.controller.setZoom(zoom.toDouble())
                    
                    // Use instant move if we are transitioning (covered by snapshot or heavy blur)
                    val isTransitioning = showSnapshot || blurTarget > 0f
                    if (isTransitioning) {
                        view.controller.setCenter(appliedGeoPoint)
                    } else if (shouldAnimate) {
                        view.controller.animateTo(appliedGeoPoint)
                    } else {
                        view.controller.setCenter(appliedGeoPoint)
                        isMapMoving = false
                    }
                    
                    if (!view.isAnimating) {
                        isMapMoving = false
                    }

                    val isResponseAnimating = responseProgress.value > 0f && responseProgress.value < 1f
                    if (isResponseAnimating || view.isAnimating || isMapMoving || showSnapshot) {
                        view.postInvalidateOnAnimation()
                    }
                }
            )

            // Fading snapshot overlay to hide tile loading
            if (snapshotAlpha > 0f && previousBitmap != null) {
                Image(
                    bitmap = previousBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(snapshotAlpha),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
        
        // Final tint overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08203E).copy(alpha = tintAlpha))
        )
    }
}
