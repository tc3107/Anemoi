package com.example.anemoi.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.data.LocationItem
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<LocationItem>,
    favorites: List<LocationItem>,
    onLocationSelected: (LocationItem) -> Unit,
    onSettingsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onToggleFavorite: (LocationItem) -> Unit,
    selectedLocation: LocationItem?,
    isLocating: Boolean,
    isFollowMode: Boolean,
    hasErrors: Boolean,
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
            return@LaunchedEffect
        }

        if (isKeyboardVisible) {
            hadVisibleKeyboardThisFocusSession = true
        } else if (hadVisibleKeyboardThisFocusSession) {
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

    val shouldShowDropdown = isFocused && expanded && query.isNotEmpty() && suggestions.isNotEmpty()
    val dropdownVisibleState = remember { MutableTransitionState(false) }
    var renderedSuggestions by remember { mutableStateOf<List<LocationItem>>(emptyList()) }

    LaunchedEffect(shouldShowDropdown) {
        dropdownVisibleState.targetState = shouldShowDropdown
    }

    LaunchedEffect(shouldShowDropdown, suggestions) {
        if (shouldShowDropdown) {
            renderedSuggestions = suggestions
        }
    }

    LaunchedEffect(dropdownVisibleState.currentState, dropdownVisibleState.targetState) {
        if (!dropdownVisibleState.currentState && !dropdownVisibleState.targetState) {
            renderedSuggestions = emptyList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerSize))
            .animateContentSize(animationSpec = tween(300))
    ) {
        // Background blur and tint
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(blurStrength.dp)
                .background(Color.White.copy(alpha = tintAlpha))
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
                                    text = if (isFollowMode) "Current Location" else (selectedLocation?.name ?: "Search location..."),
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
                        tint = if (hasErrors) Color.Red else Color.White
                    )
                }
            }

            AnimatedVisibility(
                visibleState = dropdownVisibleState,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
            ) {
                Column {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        // Using a combination of index and name to ensure key uniqueness during search
                        items(renderedSuggestions, key = { "${it.name}_${it.lat}_${it.lon}" }) { location ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(
                                        fadeInSpec = tween(300),
                                        fadeOutSpec = tween(300),
                                        placementSpec = tween(300)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onLocationSelected(location)
                                        focusManager.clearFocus()
                                    }
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
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
                                    text = location.name,
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
        }
    }
}
