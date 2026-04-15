package com.example.deckbridge.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Very subtle grid texture for the control-surface home (not a literal copy of any third-party asset).
 */
@Composable
fun DashboardHexBackground(modifier: Modifier = Modifier) {
    val base = Color(0xFF06060A)
    val stroke = Color.White.copy(alpha = 0.035f)
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(base)
        val step = 32.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = stroke,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 0.6.dp.toPx(),
            )
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = stroke,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.6.dp.toPx(),
            )
            y += step
        }
    }
}
