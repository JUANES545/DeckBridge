package com.example.deckbridge.ui.home

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.deckbridge.update.UpdateState

private val BannerBackground = Color(0xFF13131F)
private val BannerBorder = Color.White.copy(alpha = 0.08f)
private val BannerShape = RoundedCornerShape(16.dp)

private val ColorBlue   = Color(0xFF3D62FF)
private val ColorGreen  = Color(0xFF2EE6A0)
private val ColorAmber  = Color(0xFFFFB020)

@Composable
fun UpdateBanner(
    updateState: UpdateState,
    onUpdate: () -> Unit,
    onPermission: () -> Unit,
    onInstall: (Uri) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve per-state visuals
    val dotColor: Color
    val title: String
    val subtitle: String
    val ctaLabel: String?
    val ctaColor: Color
    val ctaAction: () -> Unit
    val progress: Float?  // null = no bar, -1f = indeterminate, 0..1 = determinate

    when (updateState) {
        is UpdateState.Available -> {
            dotColor   = ColorBlue
            title      = "Nueva versión disponible"
            subtitle   = "v${updateState.info.latestVersion} — toca para actualizar"
            ctaLabel   = "Actualizar"
            ctaColor   = ColorBlue
            ctaAction  = onUpdate
            progress   = null
        }
        is UpdateState.NeedsPermission -> {
            dotColor   = ColorAmber
            title      = "Permiso requerido"
            subtitle   = "Activa «Instalar apps desconocidas»"
            ctaLabel   = "Permitir"
            ctaColor   = ColorAmber
            ctaAction  = onPermission
            progress   = null
        }
        is UpdateState.Downloading -> {
            dotColor   = ColorBlue
            title      = "Descargando v${updateState.info.latestVersion}…"
            subtitle   = if (updateState.progress >= 0f)
                "${(updateState.progress * 100).toInt()} %"
            else
                "Preparando descarga…"
            ctaLabel   = null
            ctaColor   = ColorBlue
            ctaAction  = {}
            progress   = updateState.progress
        }
        is UpdateState.ReadyToInstall -> {
            dotColor   = ColorGreen
            title      = "v${updateState.info.latestVersion} lista para instalar"
            subtitle   = "Toca Instalar para aplicar la actualización"
            ctaLabel   = "Instalar"
            ctaColor   = ColorGreen
            ctaAction  = { onInstall(updateState.apkUri) }
            progress   = null
        }
        else -> return   // Idle / Dismissed — render nothing
    }

    // Pulsing dot for indeterminate download
    val dotAlpha = if (updateState is UpdateState.Downloading && (progress ?: 0f) < 0f) {
        val pulse by rememberInfiniteTransition(label = "update_dot_pulse").animateFloat(
            initialValue = 0.4f,
            targetValue  = 1.0f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
            label = "update_dot_alpha",
        )
        pulse
    } else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(BannerShape)
            .background(BannerBackground.copy(alpha = 0.97f))
            .border(1.dp, BannerBorder, BannerShape),
    ) {
        // ── Main row ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start  = 14.dp,
                    end    = 4.dp,
                    top    = 10.dp,
                    bottom = if (progress != null && progress >= 0f) 4.dp else 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status dot
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = dotAlpha)),
            )
            Spacer(Modifier.width(10.dp))

            // Text block
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = title,
                    style    = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color    = Color.White.copy(alpha = 0.90f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text     = subtitle,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = Color.White.copy(alpha = 0.50f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // CTA button (hidden while downloading)
            if (ctaLabel != null) {
                TextButton(
                    onClick        = ctaAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        text       = ctaLabel,
                        color      = ctaColor,
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Dismiss
            IconButton(
                onClick  = onDismiss,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Cerrar notificación de actualización",
                    tint     = Color.White.copy(alpha = 0.38f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // ── Progress bar (determinate only) ──────────────────────────────────
        if (progress != null && progress >= 0f) {
            LinearProgressIndicator(
                progress    = { progress },
                modifier    = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 10.dp),
                color       = ColorBlue,
                trackColor  = Color.White.copy(alpha = 0.10f),
                strokeCap   = StrokeCap.Round,
            )
        }
    }
}
