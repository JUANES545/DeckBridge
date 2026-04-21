package com.example.deckbridge.ui.onboarding

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deckbridge.R
import com.example.deckbridge.ui.connect.ConnectHelpBottomSheet
import kotlinx.coroutines.launch

/**
 * Three-step first-run onboarding. Call [onFinished] when the user completes or skips the flow.
 */
@Composable
fun OnboardingFlow(
    onFinished: () -> Unit,
    onRequestAddComputer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    var connectHelpVisible by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingTheme.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onFinished) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = OnboardingTheme.textSecondary,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isLandscape) 12.dp else 20.dp)
                .padding(
                    top = if (isLandscape) 36.dp else 48.dp,
                    bottom = if (isLandscape) 8.dp else 28.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = if (isLandscape) Alignment.Top else Alignment.CenterVertically,
            ) { page ->
                key(page) {
                    val pageScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (!isLandscape) Modifier.verticalScroll(pageScroll) else Modifier),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (page) {
                            0 -> OnboardingPageWelcome(
                                isLandscape = isLandscape,
                                onPrimary = { scope.launch { pagerState.animateScrollToPage(1) } },
                            )
                            1 -> OnboardingPageDesktop(
                                isLandscape = isLandscape,
                                onShare = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            context.getString(R.string.onboarding_download_placeholder_url),
                                        )
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            send,
                                            context.getString(R.string.onboarding_share_chooser_title),
                                        ),
                                    )
                                },
                                onContinue = { scope.launch { pagerState.animateScrollToPage(2) } },
                            )
                            else -> OnboardingPageConnect(
                                isLandscape = isLandscape,
                                onAddComputer = onRequestAddComputer,
                                onGetHelp = { connectHelpVisible = true },
                                onEnterApp = onFinished,
                            )
                        }
                    }
                }
            }

            if (!isLandscape) {
                Spacer(Modifier.height(16.dp))
                OnboardingPagerIndicator(
                    pageCount = 3,
                    currentPage = pagerState.currentPage,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                OnboardingPagerIndicator(
                    pageCount = 3,
                    currentPage = pagerState.currentPage,
                )
            }
        }

        if (connectHelpVisible) {
            ConnectHelpBottomSheet(onDismiss = { connectHelpVisible = false })
        }
    }
}

// ── Page 1: Welcome ───────────────────────────────────────────────────────────

@Composable
private fun OnboardingPageWelcome(
    isLandscape: Boolean,
    onPrimary: () -> Unit,
) {
    if (isLandscape) {
        OnboardingCard(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left — illustration
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(OnboardingTheme.cardElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    IllustrationWelcome(compact = true)
                }
                VerticalDivider(color = OnboardingTheme.background, thickness = 1.dp)
                // Right — text + CTA
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_p1_kicker),
                        color = OnboardingTheme.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.onboarding_p1_title),
                        color = OnboardingTheme.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 26.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.onboarding_p1_body),
                        color = OnboardingTheme.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.onboarding_p1_footer),
                        color = OnboardingTheme.textMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(20.dp))
                    OnboardingPrimaryCta(
                        text = stringResource(R.string.onboarding_p1_cta),
                        onClick = onPrimary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    } else {
        OnboardingCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IllustrationWelcome()
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.onboarding_p1_kicker),
                    color = OnboardingTheme.textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.onboarding_p1_title),
                    color = OnboardingTheme.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.onboarding_p1_body),
                    color = OnboardingTheme.textSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.onboarding_p1_footer),
                    color = OnboardingTheme.textMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                OnboardingPrimaryCta(
                    text = stringResource(R.string.onboarding_p1_cta),
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun IllustrationWelcome(compact: Boolean = false) {
    val boxHeight = if (compact) 160.dp else 200.dp
    val circleSize = if (compact) 64.dp else 76.dp
    val iconSize = if (compact) 34.dp else 40.dp
    val iconNavSize = if (compact) 40.dp else 48.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(boxHeight),
        contentAlignment = Alignment.Center,
    ) {
        val icons = listOf(
            Triple(Icons.Default.Apps, (-72).dp, (-12).dp),
            Triple(Icons.Default.ContentCopy, (-24).dp, 28.dp),
            Triple(Icons.Default.MusicNote, 32.dp, (-20).dp),
            Triple(Icons.Default.FolderOpen, 76.dp, 22.dp),
        )
        icons.forEach { (icon, ox, oy) ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OnboardingTheme.textSecondary.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(iconNavSize)
                    .offset(ox, oy),
            )
        }
        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(CircleShape)
                .background(OnboardingTheme.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

// ── Page 2: Desktop agent ─────────────────────────────────────────────────────

@Composable
private fun OnboardingPageDesktop(
    isLandscape: Boolean,
    onShare: () -> Unit,
    onContinue: () -> Unit,
) {
    if (isLandscape) {
        OnboardingCard(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left — icon
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(OnboardingTheme.cardElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(OnboardingTheme.card),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Usb,
                            contentDescription = null,
                            tint = OnboardingTheme.accent,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
                VerticalDivider(color = OnboardingTheme.background, thickness = 1.dp)
                // Right — text + buttons
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_p2_title),
                        color = OnboardingTheme.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.onboarding_p2_body),
                        color = OnboardingTheme.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.onboarding_p2_link_line),
                        color = OnboardingTheme.textMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.onboarding_download_placeholder_url),
                        color = OnboardingTheme.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(20.dp))
                    OnboardingSecondaryCta(
                        text = stringResource(R.string.onboarding_p2_share),
                        onClick = onShare,
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = OnboardingTheme.textPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    OnboardingPrimaryCta(
                        text = stringResource(R.string.onboarding_p2_cta),
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    } else {
        OnboardingCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(OnboardingTheme.cardElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Usb,
                        contentDescription = null,
                        tint = OnboardingTheme.accent,
                        modifier = Modifier.size(52.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.onboarding_p2_title),
                    color = OnboardingTheme.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.onboarding_p2_body),
                    color = OnboardingTheme.textSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.onboarding_p2_link_line),
                    color = OnboardingTheme.textMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.onboarding_download_placeholder_url),
                    color = OnboardingTheme.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                OnboardingSecondaryCta(
                    text = stringResource(R.string.onboarding_p2_share),
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint = OnboardingTheme.textPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                OnboardingPrimaryCta(
                    text = stringResource(R.string.onboarding_p2_cta),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Page 3: Connect ───────────────────────────────────────────────────────────

@Composable
private fun OnboardingPageConnect(
    isLandscape: Boolean,
    onAddComputer: () -> Unit,
    onGetHelp: () -> Unit,
    onEnterApp: () -> Unit,
) {
    if (isLandscape) {
        OnboardingCard(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left — icons + headline
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(OnboardingTheme.cardElevated)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(OnboardingTheme.card),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Wifi, null, tint = OnboardingTheme.accent, modifier = Modifier.size(26.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(OnboardingTheme.card),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Usb, null, tint = OnboardingTheme.accent, modifier = Modifier.size(26.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.onboarding_p3_title),
                        color = OnboardingTheme.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.onboarding_p3_subtitle),
                        color = OnboardingTheme.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )
                }
                VerticalDivider(color = OnboardingTheme.background, thickness = 1.dp)
                // Right — list + CTAs
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_p3_list_header),
                        color = OnboardingTheme.textMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(OnboardingTheme.listRow),
                    ) {
                        Text(
                            text = stringResource(R.string.connect_no_hosts_yet),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            color = OnboardingTheme.textMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                        HorizontalDivider(color = OnboardingTheme.background, thickness = 1.dp)
                        TextButton(
                            onClick = onAddComputer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.QrCode2, null, tint = OnboardingTheme.textPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.onboarding_p3_add_computer),
                                color = OnboardingTheme.textPrimary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.onboarding_p3_hint),
                        color = OnboardingTheme.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    OnboardingSecondaryCta(
                        text = stringResource(R.string.onboarding_p3_help),
                        onClick = onGetHelp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OnboardingPrimaryCta(
                        text = stringResource(R.string.onboarding_p3_enter),
                        onClick = onEnterApp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    } else {
        OnboardingCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(OnboardingTheme.cardElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Wifi, null, tint = OnboardingTheme.accent, modifier = Modifier.size(30.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(OnboardingTheme.cardElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Usb, null, tint = OnboardingTheme.accent, modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    text = stringResource(R.string.onboarding_p3_title),
                    color = OnboardingTheme.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.onboarding_p3_subtitle),
                    color = OnboardingTheme.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    text = stringResource(R.string.onboarding_p3_list_header),
                    modifier = Modifier.fillMaxWidth(),
                    color = OnboardingTheme.textMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OnboardingTheme.listRow),
                ) {
                    Text(
                        text = stringResource(R.string.connect_no_hosts_yet),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        color = OnboardingTheme.textMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                    HorizontalDivider(color = OnboardingTheme.background, thickness = 1.dp)
                    TextButton(
                        onClick = onAddComputer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.QrCode2, null, tint = OnboardingTheme.textPrimary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.onboarding_p3_add_computer),
                            color = OnboardingTheme.textPrimary,
                            fontSize = 15.sp,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.onboarding_p3_hint),
                    color = OnboardingTheme.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                OnboardingSecondaryCta(
                    text = stringResource(R.string.onboarding_p3_help),
                    onClick = onGetHelp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OnboardingPrimaryCta(
                    text = stringResource(R.string.onboarding_p3_enter),
                    onClick = onEnterApp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
