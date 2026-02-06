package com.example.anemoi.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun SegmentedSelector(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var componentWidth by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val outerPadding = 8.dp
    val internalSpacing = 2.dp
    val outerPaddingPx = with(density) { outerPadding.toPx() }
    val internalSpacingPx = with(density) { internalSpacing.toPx() }

    val itemWidthPx = remember(componentWidth, options.size) {
        if (options.isEmpty()) 0f
        else maxOf(0f, (componentWidth - 2 * outerPaddingPx - (options.size - 1) * internalSpacingPx) / options.size)
    }

    val animOffset = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Tracks the raw finger position relative to the track start
    var rawFingerX by remember { mutableFloatStateOf(0f) }

    val anchors = remember(options.size, itemWidthPx) {
        options.indices.map { it * (itemWidthPx + internalSpacingPx) }
    }

    // Resistance and Snapping Logic
    val visualTargetOffset = remember(rawFingerX, anchors, isDragging, selectedIndex) {
        if (!isDragging) {
            if (selectedIndex in anchors.indices) anchors[selectedIndex] else 0f
        } else {
            val closestIndex = anchors.minByOrNull { abs(it - rawFingerX) }
                ?.let { anchors.indexOf(it) } ?: selectedIndex
            val anchor = anchors[closestIndex]
            val distance = rawFingerX - anchor
            // Resistance: visual movement is only 30% of the distance from the nearest anchor point
            anchor + distance * 0.3f
        }
    }

    LaunchedEffect(visualTargetOffset, isDragging) {
        if (isDragging) {
            animOffset.snapTo(visualTargetOffset)
        } else {
            animOffset.animateTo(
                targetValue = visualTargetOffset,
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .onGloballyPositioned { componentWidth = it.size.width.toFloat() }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .pointerInput(options.size, itemWidthPx) {
                if (itemWidthPx <= 0) return@pointerInput
                detectTapGestures { offset ->
                    val index = ((offset.x - outerPaddingPx) / (itemWidthPx + internalSpacingPx))
                        .toInt()
                        .coerceIn(0, options.size - 1)
                    onOptionSelected(index)
                }
            }
            .pointerInput(options.size, itemWidthPx) {
                if (itemWidthPx <= 0) return@pointerInput
                detectDragGestures(
                    onDragStart = { 
                        isDragging = true
                        rawFingerX = animOffset.value 
                    },
                    onDragEnd = {
                        isDragging = false
                        val closestIndex = anchors.minByOrNull { abs(it - rawFingerX) }
                            ?.let { anchors.indexOf(it) } ?: selectedIndex
                        onOptionSelected(closestIndex)
                    },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, amount ->
                        change.consume()
                        rawFingerX += amount.x
                        // Visual snapping: notify parent immediately when midpoint is crossed
                        val closestIndex = anchors.minByOrNull { abs(it - rawFingerX) }
                            ?.let { anchors.indexOf(it) } ?: selectedIndex
                        if (closestIndex != selectedIndex) {
                            onOptionSelected(closestIndex)
                        }
                    }
                )
            }
    ) {
        // Separators
        if (itemWidthPx > 0) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = outerPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEachIndexed { index, _ ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (index < options.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentWidth(Alignment.End)
                                    .padding(end = (internalSpacing / 2))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(16.dp)
                                        .background(Color.White.copy(alpha = 0.15f))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sliding Indicator (White box)
        if (itemWidthPx > 0) {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .graphicsLayer {
                        translationX = outerPaddingPx + animOffset.value
                    }
                    .width(with(density) { itemWidthPx.toDp() })
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
            )
        }

        // Text Labels Layer 1 (White text - background)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = outerPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { _, label ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Text Labels Layer 2 (Black text - clipped to indicator for inversion)
        // Fixed: Using float-based graphicsLayer translation instead of IntOffset to prevent sub-pixel shaking
        if (itemWidthPx > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = outerPaddingPx + animOffset.value
                        clip = true
                        shape = RoundedCornerShape(8.dp)
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = -(outerPaddingPx + animOffset.value)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = outerPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        options.forEachIndexed { _, label ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
