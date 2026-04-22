package com.example.deckbridge.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DOT_SIZE_ACTIVE = 7.dp
private val DOT_SIZE_INACTIVE = 5.dp
private val DOT_ALPHA_ACTIVE = 1.0f
private val DOT_ALPHA_INACTIVE = 0.35f
private val DOT_COLOR = Color.White
private val DOT_SPACING = 8.dp
// Minimum touch target per dot: 36×36 dp
private val DOT_TOUCH_TARGET = 36.dp

/**
 * Row (portrait) or Column (landscape) of circular page indicator dots wrapped in a
 * semi-transparent pill container.
 *
 * Each dot is tappable to navigate directly to that page. A long press anywhere on the
 * container fires [onLongPress] to open the page management bottom sheet.
 *
 * Always rendered (even with a single page) so the user can discover page management.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageIndicatorDots(
    pageCount: Int,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    onLongPress: () -> Unit,
    isVertical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val arrangement = Arrangement.spacedBy(DOT_SPACING)
    // Pill shape adapts to orientation
    val pillShape = if (isVertical) RoundedCornerShape(8.dp) else RoundedCornerShape(12.dp)
    val pillPadH = if (isVertical) 10.dp else 12.dp
    val pillPadV = if (isVertical) 12.dp else 8.dp

    @Composable
    fun dot(page: Int) {
        val active = page == currentPage
        val size by animateDpAsState(
            targetValue = if (active) DOT_SIZE_ACTIVE else DOT_SIZE_INACTIVE,
            animationSpec = tween(durationMillis = 200),
            label = "dot_size_$page",
        )
        val alpha by animateFloatAsState(
            targetValue = if (active) DOT_ALPHA_ACTIVE else DOT_ALPHA_INACTIVE,
            animationSpec = tween(durationMillis = 200),
            label = "dot_alpha_$page",
        )
        val interactionSource = remember { MutableInteractionSource() }
        // Large invisible touch target with visual dot centered inside
        Box(
            modifier = Modifier
                .size(DOT_TOUCH_TARGET)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = false, radius = 16.dp),
                    onClick = { onPageSelected(page) },
                    onLongClick = onLongPress,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .alpha(alpha)
                    .background(DOT_COLOR),
            )
        }
    }

    if (isVertical) {
        Column(
            modifier = modifier
                .clip(pillShape)
                .background(Color.White.copy(alpha = 0.07f))
                .padding(horizontal = pillPadH, vertical = pillPadV),
            verticalArrangement = Arrangement.spacedBy(0.dp), // touch targets butt up; spacing is baked in
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(pageCount) { page -> dot(page) }
        }
    } else {
        Row(
            modifier = modifier
                .clip(pillShape)
                .background(Color.White.copy(alpha = 0.07f))
                .padding(horizontal = pillPadH, vertical = pillPadV),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { page -> dot(page) }
        }
    }
}
