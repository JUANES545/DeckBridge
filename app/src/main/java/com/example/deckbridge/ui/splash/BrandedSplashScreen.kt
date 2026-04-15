package com.example.deckbridge.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deckbridge.R
import kotlin.math.cos
import kotlin.math.sin

private val SplashBg0 = Color(0xFF06030C)
private val SplashBg1 = Color(0xFF14082A)
private val PentagramColor = Color(0x668B6BC4)
private val PentagramInner = Color(0x444A3566)
private val KeyDot = Color(0xFFF7F2FF)
private val Horn = Color(0xFFE8DCFF)
private val TaglineColor = Color(0xFFB8A4E8)
private val TitleColor = Color(0xFFFFFFFF)

/**
 * Cold-start splash: pentagram + deck grid + horns, “Superior” then app name, subtle motion.
 */
@Composable
fun BrandedSplashScreen(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "splash")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pentagram",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SplashBg0, SplashBg1, SplashBg0))),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation },
        ) {
            val c = Offset(size.width * 0.5f, size.height * 0.42f)
            val r = size.minDimension * 0.38f
            val star = pentagramPath(c, r)
            drawPath(star, PentagramColor, style = Stroke(width = size.minDimension * 0.012f))
            val inner = pentagramPath(c, r * 0.55f)
            drawPath(inner, PentagramInner, style = Stroke(width = size.minDimension * 0.008f))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DeckBridgeSplashMark(
                modifier = Modifier
                    .size(132.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    },
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                color = TaglineColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = TitleColor,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun pentagramPath(center: Offset, radius: Float): Path {
    val toRad = kotlin.math.PI.toFloat() / 180f
    val pts = Array(5) { i ->
        val ang = (-90f + i * 72f) * toRad
        Offset(
            center.x + radius * cos(ang),
            center.y + radius * sin(ang),
        )
    }
    val p = Path()
    val order = intArrayOf(0, 2, 4, 1, 3, 0)
    p.moveTo(pts[order[0]].x, pts[order[0]].y)
    for (i in 1 until order.size) {
        p.lineTo(pts[order[i]].x, pts[order[i]].y)
    }
    p.close()
    return p
}

@Composable
private fun DeckBridgeSplashMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        // Horns
        val hornPath = Path().apply {
            moveTo(cx - w * 0.22f, h * 0.28f)
            lineTo(cx - w * 0.12f, h * 0.02f)
            lineTo(cx - w * 0.02f, h * 0.28f)
            close()
        }
        drawPath(hornPath, Horn)
        val hornPathR = Path().apply {
            moveTo(cx + w * 0.02f, h * 0.28f)
            lineTo(cx + w * 0.12f, h * 0.02f)
            lineTo(cx + w * 0.22f, h * 0.28f)
            close()
        }
        drawPath(hornPathR, Horn)

        val rDot = w * 0.065f
        val xs = listOf(-0.28f, -0.09f, 0.09f, 0.28f)
        val ys = listOf(0.42f, 0.58f, 0.74f)
        for (y in ys) {
            for (x in xs) {
                drawCircle(KeyDot, rDot, Offset(cx + w * x, h * y))
            }
        }
    }
}
