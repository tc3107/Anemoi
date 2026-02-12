package com.example.anemoi.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.anemoi.data.LocationItem
import com.example.anemoi.ui.components.GlassEntryCard

@Composable
fun OrganizerOverlay(
    favorites: List<LocationItem>,
    onReorder: (Int, Int) -> Unit,
    onToggleFavorite: (LocationItem) -> Unit,
    onRenameLocation: (LocationItem, String?) -> Unit,
    onSelect: (LocationItem) -> Unit,
    onClose: () -> Unit,
    blurStrength: Float,
    tintAlpha: Float
) {
    val listState = rememberLazyListState()
    val items = remember { mutableStateListOf<LocationItem>().apply { addAll(favorites) } }
    val haptic = LocalHapticFeedback.current
    val surfaceShape = RoundedCornerShape(32.dp)
    
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var renameTarget by remember { mutableStateOf<LocationItem?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    
    LaunchedEffect(favorites) {
        if (draggingIndex == null) {
            if (favorites != items.toList()) {
                items.clear()
                items.addAll(favorites)
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(surfaceShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.32f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = surfaceShape
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurStrength.dp)
                    .background(Color.White.copy(alpha = tintAlpha))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.26f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Organizer",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val item = listState.layoutInfo.visibleItemsInfo.find {
                                        offset.y.toInt() in it.offset..it.offset + it.size
                                    }
                                    if (item != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggingIndex = item.index
                                    }
                                },
                                onDragEnd = { draggingIndex = null; dragOffset = 0f },
                                onDragCancel = { draggingIndex = null; dragOffset = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val currentDraggedIdx = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                    dragOffset += dragAmount.y
                                    
                                    val layoutInfo = listState.layoutInfo
                                    val draggedItem = layoutInfo.visibleItemsInfo.find { it.index == currentDraggedIdx } 
                                        ?: return@detectDragGesturesAfterLongPress
                                    
                                    val center = draggedItem.offset + draggedItem.size / 2 + dragOffset
                                    val targetItem = layoutInfo.visibleItemsInfo.find { item ->
                                        center.toInt() in item.offset..item.offset + item.size &&
                                        item.index != currentDraggedIdx
                                    }
                                    
                                    if (targetItem != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val newIndex = targetItem.index
                                        val offsetDelta = draggedItem.offset - targetItem.offset
                                        
                                        items.add(newIndex, items.removeAt(currentDraggedIdx))
                                        onReorder(currentDraggedIdx, newIndex)
                                        
                                        dragOffset += offsetDelta
                                        draggingIndex = newIndex
                                    }
                                }
                            )
                        },
                    userScrollEnabled = draggingIndex == null
                ) {
                    itemsIndexed(items, key = { _, item -> "${item.lat}_${item.lon}" }) { index, location ->
                        val isDragging = draggingIndex == index
                        
                        GlassEntryCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .let { 
                                    if (isDragging) it 
                                    else it.animateItem(
                                        fadeInSpec = null,
                                        fadeOutSpec = null,
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationY = dragOffset
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                        alpha = 0.9f
                                    }
                                }
                                .zIndex(if (isDragging) 1f else 0f)
                                .clickable(enabled = draggingIndex == null) { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelect(location) 
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = location.displayName,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 16.sp
                                )
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        renameTarget = location
                                        renameDraft = location.displayName
                                    },
                                    enabled = draggingIndex == null
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        tint = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleFavorite(location)
                                        val itemIdx = items.indexOfFirst { it.lat == location.lat && it.lon == location.lon }
                                        if (itemIdx != -1) {
                                            items[itemIdx] = items[itemIdx].copy(isFavorite = !items[itemIdx].isFavorite)
                                        }
                                    },
                                    enabled = draggingIndex == null
                                ) {
                                    Icon(
                                        imageVector = if (location.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Favorite",
                                        tint = if (location.isFavorite) Color(0xFFFFD700) else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClose()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename location") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        singleLine = true,
                        label = { Text("Display name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Original: ${target.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameLocation(target, renameDraft)
                        renameTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onRenameLocation(target, null)
                            renameTarget = null
                        }
                    ) {
                        Text("Revert")
                    }
                    TextButton(onClick = { renameTarget = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
