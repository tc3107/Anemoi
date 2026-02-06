package com.example.anemoi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun Modifier.glassmorphism(alpha: Float = 0.15f) = this
    .clip(CircleShape)
    .background(Color.White.copy(alpha = alpha))
    .padding(2.dp)
