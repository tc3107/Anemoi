package com.example.anemoi.ui.components

import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
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
import java.util.concurrent.atomic.AtomicBoolean
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
    shouldAnimate: Boolean = true,
    freezeCameraUpdates: Boolean = false,
    interactionEnabled: Boolean = true
) {
    val mapSwitchDebounceMs = 120L
    val mapSwitchMaskTailMs = 120L
    val centerRecoveryToleranceDeg = 6e-5
    val minCenterRecoveryIntervalMs = 220L

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

    // Lightweight transition state for smooth location switching
    var transitionMaskTarget by remember { mutableFloatStateOf(0f) }
    var isInitialLoad by remember { mutableStateOf(true) }
    var lastCenterTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var lastCenterCommandAtMs by remember { mutableLongStateOf(0L) }
    var lastInteractionEnabled by remember { mutableStateOf(interactionEnabled) }
    val currentObfuscationMode by rememberUpdatedState(obfuscationMode)
    val currentGridKm by rememberUpdatedState(gridKm)
    val currentLastResponseCoords by rememberUpdatedState(lastResponseCoords)

    val transitionMaskAlpha by animateFloatAsState(
        targetValue = transitionMaskTarget,
        animationSpec = tween(durationMillis = 180),
        label = "transitionMaskAlpha"
    )

    val isMapMoving = remember { AtomicBoolean(false) }
    val movementHandler = remember { Handler(Looper.getMainLooper()) }
    val resetMovingRunnable = remember { Runnable { isMapMoving.set(false) } }

    DisposableEffect(movementHandler, resetMovingRunnable) {
        onDispose {
            movementHandler.removeCallbacks(resetMovingRunnable)
        }
    }

    // Coalesce rapid location changes and only move the map for the latest target.
    LaunchedEffect(lat, lon) {
        if (isInitialLoad) {
            // Avoid startup transition effects on first render.
            transitionMaskTarget = 0f
            isInitialLoad = false
            return@LaunchedEffect
        }

        // If user keeps switching quickly, previous coroutine is canceled and this target wins.
        transitionMaskTarget = 0.12f
        delay(mapSwitchDebounceMs)
        appliedLat = lat
        appliedLon = lon
        delay(mapSwitchMaskTailMs)
        transitionMaskTarget = 0f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier
            .fillMaxSize()
            .blur(blurStrength.dp)
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
                        setOnTouchListener { _, _ -> !interactionEnabled }
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
                                isMapMoving.set(true)
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
                                if (shadow || isMapMoving.get() || mapView.isAnimating || currentObfuscationMode != ObfuscationMode.GRID) return

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
                    }
                },
                update = { view ->
                    if (interactionEnabled != lastInteractionEnabled) {
                        view.setOnTouchListener { _, _ -> !interactionEnabled }
                        lastInteractionEnabled = interactionEnabled
                    }

                    if (freezeCameraUpdates) {
                        if (!view.isAnimating) {
                            isMapMoving.set(false)
                        }
                        return@AndroidView
                    }

                    val currentZoom = view.zoomLevelDouble
                    if (abs(currentZoom - zoom.toDouble()) > 1e-3) {
                        view.controller.setZoom(zoom.toDouble())
                    }
                    
                    val currentTarget = appliedLat to appliedLon
                    val targetChanged = lastCenterTarget != currentTarget
                    val center = view.mapCenter
                    val centerMismatch = center == null ||
                        abs(center.latitude - appliedLat) > centerRecoveryToleranceDeg ||
                        abs(center.longitude - appliedLon) > centerRecoveryToleranceDeg
                    val nowMs = SystemClock.uptimeMillis()
                    val canRecoverCenter = nowMs - lastCenterCommandAtMs >= minCenterRecoveryIntervalMs
                    fun commandCenter() {
                        view.controller.setCenter(appliedGeoPoint)
                        lastCenterTarget = currentTarget
                        lastCenterCommandAtMs = nowMs
                    }

                    // Use instant move while transition pulse is active.
                    val isTransitioning = transitionMaskTarget > 0f
                    if (isTransitioning) {
                        if (targetChanged) {
                            commandCenter()
                        }
                    } else if (shouldAnimate) {
                        if (targetChanged) {
                            // Snap instead of pan to avoid CPU-heavy camera animations while switching locations.
                            commandCenter()
                        } else if (!view.isAnimating && centerMismatch && canRecoverCenter) {
                            // Recovery path with a small cooldown to avoid repeated center commands.
                            commandCenter()
                        }
                    } else {
                        if (targetChanged || (centerMismatch && canRecoverCenter) || view.isAnimating) {
                            commandCenter()
                        }
                        isMapMoving.set(false)
                    }
                    
                    if (!view.isAnimating) {
                        isMapMoving.set(false)
                    }

                    val isResponseAnimating = responseProgress.value > 0f && responseProgress.value < 1f
                    if (isResponseAnimating || isTransitioning) {
                        view.postInvalidateOnAnimation()
                    }
                }
            )

            if (transitionMaskAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF08203E).copy(alpha = transitionMaskAlpha))
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
