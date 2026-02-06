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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
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

    val isKeyboardVisible = WindowInsets.isImeVisible

    LaunchedEffect(isFocused, query, isKeyboardVisible) {
        if (isFocused || query.isNotEmpty()) {
            if (isKeyboardVisible || query.isNotEmpty()) {
                expanded = true
            } else {
                delay(100)
                expanded = false
                focusManager.clearFocus()
            }
        } else {
            delay(200)
            expanded = false
        }
    }

    val cornerSize by animateDpAsState(
        targetValue = if (expanded) 24.dp else 28.dp,
        label = "cornerAnimation"
    )

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
                IconButton(onClick = onMenuClick) {
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
                        .fillMaxHeight(),
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

                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = if (hasErrors) Color.Red else Color.White
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && query.isNotEmpty(),
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
            ) {
                if (suggestions.isNotEmpty()) {
                    Column {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        ) {
                            // Using a combination of index and name to ensure key uniqueness during search
                            items(suggestions, key = { "${it.name}_${it.lat}_${it.lon}" }) { location ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(
                                            fadeInSpec = tween(300),
                                            fadeOutSpec = tween(300),
                                            placementSpec = tween(300)
                                        )
                                        .clickable {
                                            onLocationSelected(location)
                                            focusManager.clearFocus()
                                        }
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { onToggleFavorite(location) }) {
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
}
