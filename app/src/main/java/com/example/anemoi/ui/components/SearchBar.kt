package com.example.anemoi.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.data.LocationItem
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<LocationItem>,
    searchStatusMessage: String?,
    favorites: List<LocationItem>,
    onLocationSelected: (LocationItem) -> Unit,
    onSettingsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onToggleFavorite: (LocationItem) -> Unit,
    selectedLocation: LocationItem?,
    isLocating: Boolean,
    isFollowMode: Boolean,
    hasErrors: Boolean,
    hasWarnings: Boolean,
    onLocateClick: () -> Unit,
    onLocateLongClick: () -> Unit,
    tintAlpha: Float = 0.15f,
    blurStrength: Float = 0f
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    var wasSearchFieldFocused by remember { mutableStateOf(false) }
    val isKeyboardVisible = WindowInsets.isImeVisible
    var hadVisibleKeyboardThisFocusSession by remember { mutableStateOf(false) }
    var suppressNextKeyboardDismissClear by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            expanded = true
        } else {
            delay(200)
            expanded = false
        }
    }

    LaunchedEffect(isFocused, isKeyboardVisible) {
        if (!isFocused) {
            hadVisibleKeyboardThisFocusSession = false
            suppressNextKeyboardDismissClear = false
            return@LaunchedEffect
        }

        if (isKeyboardVisible) {
            hadVisibleKeyboardThisFocusSession = true
        } else if (hadVisibleKeyboardThisFocusSession) {
            if (suppressNextKeyboardDismissClear) {
                suppressNextKeyboardDismissClear = false
                hadVisibleKeyboardThisFocusSession = false
                return@LaunchedEffect
            }
            // Keyboard was open for this focus session and is now dismissed -> clear and deselect search.
            onQueryChange("")
            focusManager.clearFocus()
            hadVisibleKeyboardThisFocusSession = false
        }
    }

    val cornerSize by animateDpAsState(
        targetValue = if (expanded) 24.dp else 28.dp,
        label = "cornerAnimation"
    )
    val surfaceShape = RoundedCornerShape(cornerSize)

    val shouldShowDropdown = isFocused && expanded && query.isNotEmpty() && suggestions.isNotEmpty()
    val shouldShowStatusMessage =
        isFocused && expanded && query.isNotEmpty() && !searchStatusMessage.isNullOrBlank()
    val dividerHeight = 1.dp
    val suggestionRowHeight = 56.dp
    val maxDropdownHeight = 300.dp
    val targetRowsHeight = if (shouldShowDropdown) {
        val unclamped = suggestionRowHeight * suggestions.size
        if (unclamped > maxDropdownHeight - dividerHeight) maxDropdownHeight - dividerHeight else unclamped
    } else {
        0.dp
    }
    val targetDropdownHeight = if (targetRowsHeight > 0.dp) dividerHeight + targetRowsHeight else 0.dp
    val animatedDropdownHeight by animateDpAsState(
        targetValue = targetDropdownHeight,
        animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
        label = "searchDropdownHeight"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
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
        // Background blur and tint
        Box(
            modifier = Modifier
                .matchParentSize()
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

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMenuClick()
                }) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = { onQueryChange(it) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused && !wasSearchFieldFocused) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            wasSearchFieldFocused = focusState.isFocused
                        },
                    interactionSource = interactionSource,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            suppressNextKeyboardDismissClear = true
                            focusManager.clearFocus()
                        },
                        onDone = {
                            suppressNextKeyboardDismissClear = true
                            focusManager.clearFocus()
                        },
                        onGo = {
                            suppressNextKeyboardDismissClear = true
                            focusManager.clearFocus()
                        },
                        onSend = {
                            suppressNextKeyboardDismissClear = true
                            focusManager.clearFocus()
                        }
                    ),
                    cursorBrush = SolidColor(Color.White),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = if (isFollowMode) "Current Location" else (selectedLocation?.displayName ?: "Search location..."),
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSettingsClick()
                }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = when {
                            hasErrors -> Color.Red
                            hasWarnings -> Color(0xFFFFC857)
                            else -> Color.White
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(animatedDropdownHeight)
                    .clipToBounds()
            ) {
                if (animatedDropdownHeight > 0.dp || shouldShowDropdown) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        suggestions.forEachIndexed { index, location ->
                            val requiredHeight = dividerHeight + (suggestionRowHeight * (index + 1))
                            val rowAlpha by animateFloatAsState(
                                targetValue = if (animatedDropdownHeight >= requiredHeight) 1f else 0f,
                                animationSpec = tween(durationMillis = 140),
                                label = "suggestionRowAlpha$index"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(suggestionRowHeight)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onLocationSelected(location)
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 8.dp)
                                    .graphicsLayer(alpha = rowAlpha),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleFavorite(location)
                                }) {
                                    Icon(
                                        imageVector = if (location.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Favorite",
                                        tint = if (location.isFavorite) Color(0xFFFFD700) else Color.White
                                    )
                                }
                                Text(
                                    text = location.displayName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = shouldShowStatusMessage,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Text(
                        text = searchStatusMessage.orEmpty(),
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
