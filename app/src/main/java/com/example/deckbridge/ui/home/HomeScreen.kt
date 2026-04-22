@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.deckbridge.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ripple
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.deckbridge.R
import com.example.deckbridge.data.mock.MockAppStateFactory
import com.example.deckbridge.domain.model.AnimatedBackgroundMode
import com.example.deckbridge.domain.model.AnimatedBackgroundTheme
import com.example.deckbridge.domain.model.AppState
import com.example.deckbridge.domain.model.HostDeliveryChannel
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.domain.model.PlatformSlotState
import com.example.deckbridge.update.UpdateState
import com.example.deckbridge.ui.hardware.HardwareMirrorPanel
import com.example.deckbridge.ui.hardware.MirrorLayoutDensity
import com.example.deckbridge.ui.hardware.MirrorPadSlot
import com.example.deckbridge.ui.hardware.MirrorPanelChrome
import com.example.deckbridge.ui.theme.DeckBridgeTheme
@Composable
fun HomeScreen(
    state: AppState,
    onDeckButtonTapped: (String) -> Unit,
    onDeckButtonLongPress: (String) -> Unit = {},
    onMirrorKnobTouchRotate: (Int, Boolean) -> Unit = { _, _ -> },
    /** Long press on a mirror knob (index 0..2) opens its editor. */
    onMirrorKnobLongPress: (Int) -> Unit = {},
    onHostPlatformSelected: (HostPlatform) -> Unit,
    onOpenSettings: () -> Unit = {},
    onGoToConnect: () -> Unit = {},
    updateState: UpdateState = UpdateState.Idle,
    onUpdateTapped: () -> Unit = {},
    onUpdatePermissionTapped: () -> Unit = {},
    onInstallTapped: (android.net.Uri) -> Unit = {},
    onUpdateDismissed: () -> Unit = {},
    onSetActivePage: (Int) -> Unit = {},
    onAddPage: () -> Unit = {},
    onDuplicatePage: () -> Unit = {},
    onDeletePage: () -> Unit = {},
    onReorderPage: (List<Int>) -> Unit = {},
    onUpdatePageName: (index: Int, name: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val surface = Color(0xFF050508)

    // ── Page navigation state ─────────────────────────────────────────────────
    val pageCount = state.deckPageCount.coerceAtLeast(1)
    var displayedPage by rememberSaveable { mutableStateOf(state.activeDeckPageIndex.coerceIn(0, pageCount - 1)) }
    // true = last change came from knob/dot/PAGE_NAV → use crossfade
    // false = last change came from swipe gesture → use slide
    var lastPageChangeProgrammatic by remember { mutableStateOf(false) }
    var swipeDirection by remember { mutableStateOf(1) } // +1 → next, -1 → prev
    val haptic = LocalHapticFeedback.current

    // Programmatic page change (knob, PAGE_NAV, dot tap, page add/delete) → crossfade.
    LaunchedEffect(state.activeDeckPageIndex) {
        val target = state.activeDeckPageIndex.coerceIn(0, pageCount - 1)
        if (displayedPage != target) {
            lastPageChangeProgrammatic = true
            displayedPage = target
        }
    }

    // Persist swipe-driven changes to the repository + haptic tick on every page settle.
    LaunchedEffect(displayedPage) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (displayedPage != state.activeDeckPageIndex) {
            onSetActivePage(displayedPage)
        }
    }

    // ── Page management sheet ─────────────────────────────────────────────────
    var showPageSheet by rememberSaveable { mutableStateOf(false) }
    val pageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val padSlots = remember(state.macroButtons) { mirrorPadSlotsFromButtons(state.macroButtons) }
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }
    val showBanner = !bannerDismissed &&
        state.hostDeliveryChannel == HostDeliveryChannel.LAN && (
            state.lanServerHost.isBlank() ||
            !state.lanTrustOk ||
            state.lanHealthOk == false
        )
    val showUpdateBanner = updateState !is UpdateState.Idle && updateState !is UpdateState.Dismissed
    val charging = rememberDeviceCharging(state.animatedBackgroundMode == AnimatedBackgroundMode.WHEN_CHARGING)
    val showEnergyPulses = when (state.animatedBackgroundMode) {
        AnimatedBackgroundMode.ALWAYS -> true
        AnimatedBackgroundMode.WHEN_CHARGING -> charging
        AnimatedBackgroundMode.OFF -> false
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = surface,
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            DashboardHexBackground(Modifier.fillMaxSize())
            // Animated overlay at the root Box level → covers the full screen including
            // the landscape side-chrome panel.  All UI layers above are transparent or
            // semi-transparent so the animation shows through everywhere.
            if (showEnergyPulses) {
                DashboardAnimatedOverlay(
                    theme = state.animatedBackgroundTheme,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(pageCount) {
                                val threshold = 80.dp.toPx()
                                var accumulated = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { accumulated = 0f },
                                    onDragEnd = { accumulated = 0f },
                                    onDragCancel = { accumulated = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulated += dragAmount
                                        when {
                                            accumulated < -threshold && displayedPage < pageCount - 1 -> {
                                                lastPageChangeProgrammatic = false
                                                swipeDirection = 1
                                                displayedPage++
                                                accumulated = 0f
                                            }
                                            accumulated > threshold && displayedPage > 0 -> {
                                                lastPageChangeProgrammatic = false
                                                swipeDirection = -1
                                                displayedPage--
                                                accumulated = 0f
                                            }
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = displayedPage,
                            transitionSpec = {
                                if (lastPageChangeProgrammatic) {
                                    fadeIn(tween(80)) togetherWith fadeOut(tween(80))
                                } else {
                                    val dir = swipeDirection
                                    slideInHorizontally(tween(220)) { w -> dir * w } togetherWith
                                        slideOutHorizontally(tween(220)) { w -> -dir * w }
                                }
                            },
                            label = "deck-page",
                        ) { page ->
                            val pageButtons = state.deckPages.getOrNull(page) ?: state.macroButtons
                            val pagePadSlots = remember(pageButtons) { mirrorPadSlotsFromButtons(pageButtons) }
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                HardwareMirrorPanel(
                                    calibration = state.hardwareCalibration,
                                    highlight = state.hardwareMirrorHighlight,
                                    knobMirrorRotation = state.knobMirrorRotation,
                                    diagSummary = state.hardwareDiagSummary,
                                    padSlots = pagePadSlots,
                                    deckKnobs = state.deckKnobs,
                                    hostPlatform = state.hostPlatform,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    maxContentWidth = null,
                                    showKnobRoleHints = false,
                                    layoutDensity = MirrorLayoutDensity.DashboardLandscape,
                                    chrome = MirrorPanelChrome.Dashboard,
                                    deckHighlight = state.deckHighlight,
                                    onPadCellTapped = onDeckButtonTapped,
                                    onPadCellLongPress = onDeckButtonLongPress,
                                    onMirrorKnobTouchRotate = onMirrorKnobTouchRotate,
                                    onMirrorKnobLongPress = onMirrorKnobLongPress,
                                )
                            }
                        }
                        if (showUpdateBanner) {
                            UpdateBanner(
                                updateState = updateState,
                                onUpdate = onUpdateTapped,
                                onPermission = onUpdatePermissionTapped,
                                onInstall = onInstallTapped,
                                onDismiss = onUpdateDismissed,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                    }
                    DashboardLandscapeChrome(
                        state = state,
                        onOpenSettings = onOpenSettings,
                        onHostPlatformSelected = onHostPlatformSelected,
                        pageIndicator = {
                            PageIndicatorDots(
                                pageCount = pageCount,
                                currentPage = displayedPage,
                                onPageSelected = { page -> onSetActivePage(page) },
                                onLongPress = { showPageSheet = true },
                                isVertical = true,
                            )
                        },
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                ) {
                    DashboardTopChrome(
                        state = state,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (showUpdateBanner) {
                        UpdateBanner(
                            updateState = updateState,
                            onUpdate = onUpdateTapped,
                            onPermission = onUpdatePermissionTapped,
                            onInstall = onInstallTapped,
                            onDismiss = onUpdateDismissed,
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(pageCount) {
                                val threshold = 80.dp.toPx()
                                var accumulated = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { accumulated = 0f },
                                    onDragEnd = { accumulated = 0f },
                                    onDragCancel = { accumulated = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulated += dragAmount
                                        when {
                                            accumulated < -threshold && displayedPage < pageCount - 1 -> {
                                                lastPageChangeProgrammatic = false
                                                swipeDirection = 1
                                                displayedPage++
                                                accumulated = 0f
                                            }
                                            accumulated > threshold && displayedPage > 0 -> {
                                                lastPageChangeProgrammatic = false
                                                swipeDirection = -1
                                                displayedPage--
                                                accumulated = 0f
                                            }
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = displayedPage,
                            transitionSpec = {
                                if (lastPageChangeProgrammatic) {
                                    fadeIn(tween(80)) togetherWith fadeOut(tween(80))
                                } else {
                                    val dir = swipeDirection
                                    slideInHorizontally(tween(220)) { w -> dir * w } togetherWith
                                        slideOutHorizontally(tween(220)) { w -> -dir * w }
                                }
                            },
                            label = "deck-page",
                        ) { page ->
                            val pageButtons = state.deckPages.getOrNull(page) ?: state.macroButtons
                            val pagePadSlots = remember(pageButtons) { mirrorPadSlotsFromButtons(pageButtons) }
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                HardwareMirrorPanel(
                                    calibration = state.hardwareCalibration,
                                    highlight = state.hardwareMirrorHighlight,
                                    knobMirrorRotation = state.knobMirrorRotation,
                                    diagSummary = state.hardwareDiagSummary,
                                    padSlots = pagePadSlots,
                                    deckKnobs = state.deckKnobs,
                                    hostPlatform = state.hostPlatform,
                                    modifier = Modifier.fillMaxWidth(),
                                    maxContentWidth = null,
                                    showKnobRoleHints = false,
                                    layoutDensity = MirrorLayoutDensity.DashboardPortrait,
                                    chrome = MirrorPanelChrome.Dashboard,
                                    deckHighlight = state.deckHighlight,
                                    onPadCellTapped = onDeckButtonTapped,
                                    onPadCellLongPress = onDeckButtonLongPress,
                                    onMirrorKnobTouchRotate = onMirrorKnobTouchRotate,
                                    onMirrorKnobLongPress = onMirrorKnobLongPress,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    PageIndicatorDots(
                        pageCount = pageCount,
                        currentPage = displayedPage,
                        onPageSelected = { page -> onSetActivePage(page) },
                        onLongPress = { showPageSheet = true },
                        isVertical = false,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(6.dp))
                    PlatformSegmentedControl(
                        selected = state.hostPlatform,
                        onSelect = onHostPlatformSelected,
                        compact = true,
                        windowsSlot = state.windowsSlot,
                        macSlot = state.macSlot,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
            }
            if (showBanner) {
                NoConnectionBanner(
                    state = state,
                    onGoToConnect = onGoToConnect,
                    onDismiss = { bannerDismissed = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        if (showPageSheet) {
            PageManagementSheet(
                sheetState = pageSheetState,
                pageCount = pageCount,
                currentPage = displayedPage,
                onDismiss = { showPageSheet = false },
                onAddPage = {
                    showPageSheet = false
                    onAddPage()
                },
                onDuplicatePage = {
                    showPageSheet = false
                    onDuplicatePage()
                },
                onDeletePage = {
                    showPageSheet = false
                    onDeletePage()
                },
                onReorderPage = onReorderPage,
                pageNames = state.deckPageNames,
                onUpdatePageName = onUpdatePageName,
            )
        }
    }
}

@Composable
private fun PageManagementSheet(
    sheetState: androidx.compose.material3.SheetState,
    pageCount: Int,
    currentPage: Int,
    onDismiss: () -> Unit,
    onAddPage: () -> Unit,
    onDuplicatePage: () -> Unit,
    onDeletePage: () -> Unit,
    onReorderPage: (List<Int>) -> Unit,
    pageNames: List<String?> = emptyList(),
    onUpdatePageName: (index: Int, name: String?) -> Unit = { _, _ -> },
) {
    val canAdd = pageCount < com.example.deckbridge.domain.deck.DeckPagesPersisted.MAX_PAGES
    val canDelete = pageCount > 1
    val sheetBg = Color(0xFF12121C)

    // ── Drag-to-reorder state ─────────────────────────────────────────────────
    val pageItems = remember(pageCount) { androidx.compose.runtime.snapshots.SnapshotStateList<Int>().also { list ->
        repeat(pageCount) { list.add(it) }
    }}
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Only move page rows (Int keys); ignore header/footer (String keys)
        if (from.key is Int && to.key is Int) {
            val fromIdx = pageItems.indexOf(from.key as Int)
            val toIdx = pageItems.indexOf(to.key as Int)
            if (fromIdx >= 0 && toIdx >= 0) pageItems.add(toIdx, pageItems.removeAt(fromIdx))
        }
    }

    // ── Inline rename state ───────────────────────────────────────────────────
    var editingPageIdx by remember { mutableStateOf<Int?>(null) }
    var editingNameText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val commitEdit: () -> Unit = {
        val idx = editingPageIdx
        if (idx != null) {
            val clean = editingNameText.ifBlank { null }
            if (clean != pageNames.getOrNull(idx)?.ifBlank { null }) onUpdatePageName(idx, clean)
            editingPageIdx = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
            )
        },
    ) {
        // Single LazyColumn for the entire sheet: header + page rows (reorderable) + action footer.
        // This lets the sheet scroll naturally in landscape without nested-scroll conflicts.
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // ── Header ───────────────────────────────────────────────────────
            item(key = "header") {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = "Pages",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.90f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                }
            }

            // ── Draggable page rows ───────────────────────────────────────────
            items(pageItems.toList(), key = { it }) { pageIdx ->
                ReorderableItem(reorderState, key = pageIdx) { isDragging ->
                    val isActive = pageIdx == currentPage
                    val rowBg = when {
                        isDragging -> Color(0xFF1E1E2E)
                        isActive   -> Color.White.copy(alpha = 0.04f)
                        else       -> Color.Transparent
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) Color(0xFF3D62FF)
                                    else Color.White.copy(alpha = 0.12f)
                                ),
                        )
                        Spacer(Modifier.width(14.dp))
                        val displayName = pageNames.getOrNull(pageIdx)?.ifBlank { null }
                        val isEditing = editingPageIdx == pageIdx
                        val focusRequester = remember { FocusRequester() }
                        // True only after the field has actually received focus — prevents
                        // onFocusChanged(false) on first composition from calling commitEdit().
                        var wasFocused by remember { mutableStateOf(false) }
                        LaunchedEffect(isEditing) {
                            if (isEditing) {
                                kotlinx.coroutines.delay(80)
                                try { focusRequester.requestFocus() } catch (_: Exception) { }
                            } else {
                                wasFocused = false
                            }
                        }
                        if (isEditing) {
                            // ── Inline text field ─────────────────────────────
                            BasicTextField(
                                value = editingNameText,
                                onValueChange = { editingNameText = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { commitEdit(); focusManager.clearFocus() },
                                ),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    color = Color.White.copy(alpha = 0.90f),
                                ),
                                cursorBrush = SolidColor(Color(0xFF3D62FF)),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { fs ->
                                        if (fs.isFocused) wasFocused = true
                                        else if (wasFocused) { wasFocused = false; commitEdit() }
                                    },
                                decorationBox = { innerTextField ->
                                    Column(Modifier.fillMaxWidth()) {
                                        Box(Modifier.padding(bottom = 3.dp)) {
                                            if (editingNameText.isEmpty()) {
                                                Text(
                                                    text = "Name this page…",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = Color.White.copy(alpha = 0.25f),
                                                )
                                            }
                                            innerTextField()
                                        }
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(1.5.dp)
                                                .background(Color(0xFF3D62FF).copy(alpha = 0.70f)),
                                        )
                                    }
                                },
                            )
                        } else {
                            // ── Tappable name display ─────────────────────────
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .pointerInput(pageIdx) {
                                        detectTapGestures(onTap = {
                                            editingNameText = pageNames.getOrNull(pageIdx) ?: ""
                                            editingPageIdx = pageIdx
                                        })
                                    },
                            ) {
                                Text(
                                    text = displayName ?: "Page ${pageIdx + 1}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    color = Color.White.copy(alpha = if (isActive) 0.90f else 0.60f),
                                )
                                if (displayName != null) {
                                    Text(
                                        text = "Page ${pageIdx + 1}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.30f),
                                    )
                                } else {
                                    Text(
                                        text = "Tap to name",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.22f),
                                    )
                                }
                            }
                        }
                        if (pageCount > 1) {
                            IconButton(
                                onClick = {},
                                modifier = Modifier
                                    .size(44.dp)
                                    .draggableHandle(
                                        onDragStopped = {
                                            val newOrder = pageItems.toList()
                                            val original = (0 until pageCount).toList()
                                            if (newOrder != original) onReorderPage(newOrder)
                                        },
                                    ),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Reorder page",
                                    tint = Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Action footer ─────────────────────────────────────────────────
            item(key = "footer") {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(Modifier.height(4.dp))
                    SheetActionRow(
                        icon = Icons.Outlined.Add,
                        label = "Add page",
                        subtitle = if (canAdd) "Page ${pageCount + 1} of ${com.example.deckbridge.domain.deck.DeckPagesPersisted.MAX_PAGES} max"
                                   else "Maximum ${com.example.deckbridge.domain.deck.DeckPagesPersisted.MAX_PAGES} pages reached",
                        enabled = canAdd,
                        onClick = onAddPage,
                    )
                    SheetActionRow(
                        icon = Icons.Outlined.ContentCopy,
                        label = "Duplicate this page",
                        subtitle = if (canAdd) "Copy inserted after current page"
                                   else "Maximum ${com.example.deckbridge.domain.deck.DeckPagesPersisted.MAX_PAGES} pages reached",
                        enabled = canAdd,
                        onClick = onDuplicatePage,
                    )
                    SheetActionRow(
                        icon = Icons.Outlined.Delete,
                        label = "Delete this page",
                        subtitle = if (canDelete) "Current page will be removed"
                                   else "Can't delete the only page",
                        enabled = canDelete,
                        isDestructive = true,
                        onClick = onDeletePage,
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = if (enabled) 1f else 0.35f
    val iconColor = when {
        !enabled -> Color.White.copy(alpha = contentAlpha)
        isDestructive -> Color(0xFFFF6B5A)
        else -> Color(0xFF3D62FF)
    }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = if (enabled) 0.12f else 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = if (enabled) 0.90f else 0.35f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = if (enabled) 0.48f else 0.25f),
            )
        }
    }
}

@Composable
private fun NoConnectionBanner(
    state: AppState,
    onGoToConnect: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noHost = state.lanServerHost.isBlank()
    val trustFailed = !state.lanTrustOk
    val (dotColor, titleText, subText) = when {
        noHost -> Triple(
            Color(0xFF5A5A68),
            stringResource(R.string.banner_no_pc_title_no_host),
            stringResource(R.string.banner_no_pc_body_no_host),
        )
        trustFailed -> Triple(
            Color(0xFFFF6B5A),
            stringResource(R.string.banner_no_pc_title_trust),
            stringResource(R.string.banner_no_pc_body_trust),
        )
        state.lanHealthRetrying -> Triple(
            Color(0xFFFFB020),
            stringResource(R.string.banner_no_pc_title_retrying),
            state.lanServerHost,
        )
        else -> Triple(
            Color(0xFFFF6B5A),
            stringResource(R.string.banner_no_pc_title_offline),
            state.lanServerHost,
        )
    }
    val bannerShape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(bannerShape)
            .background(Color(0xFF13131F).copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), bannerShape)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.90f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.50f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onGoToConnect,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(
                stringResource(R.string.banner_no_pc_connect_cta),
                color = Color(0xFF3D62FF),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.banner_no_pc_dismiss),
                tint = Color.White.copy(alpha = 0.38f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun mirrorPadSlotsFromButtons(buttons: List<com.example.deckbridge.domain.model.MacroButton>) =
    buttons
        .sortedBy { it.sortIndex }
        .map { btn ->
            MirrorPadSlot(
                title = if (btn.visible) btn.label else "—",
                shortcutHint = if (btn.visible) btn.resolvedShortcut else "",
                iconToken = if (btn.visible) btn.iconToken else null,
                deckButtonId = if (btn.visible && btn.enabled) btn.id else "",
                editTargetButtonId = if (btn.visible) btn.id else null,
            )
        }

@Composable
private fun PlatformSegmentedControl(
    selected: HostPlatform,
    onSelect: (HostPlatform) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    windowsSlot: PlatformSlotState? = null,
    macSlot: PlatformSlotState? = null,
) {
    val effective = when (selected) {
        HostPlatform.UNKNOWN -> HostPlatform.WINDOWS
        else -> selected
    }
    val trackShape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
    val controlHeight = if (compact) 36.dp else 44.dp
    val inset = if (compact) 3.dp else 4.dp
    val gap = if (compact) 3.dp else 4.dp

    Row(
        modifier = modifier
            .height(controlHeight)
            .border(1.dp, outline, trackShape)
            .clip(trackShape)
            .background(track)
            .padding(inset),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        PlatformSegment(
            iconPainter = painterResource(R.drawable.ic_platform_windows),
            label = stringResource(R.string.platform_chip_windows),
            selected = effective == HostPlatform.WINDOWS,
            onClick = { onSelect(HostPlatform.WINDOWS) },
            compact = compact,
            healthDotColor = windowsSlot?.let { slotHealthDotColor(it) },
            modifier = Modifier.weight(1f),
        )
        PlatformSegment(
            iconVector = Icons.Outlined.LaptopMac,
            label = stringResource(R.string.platform_chip_mac),
            selected = effective == HostPlatform.MAC,
            onClick = { onSelect(HostPlatform.MAC) },
            compact = compact,
            healthDotColor = macSlot?.let { slotHealthDotColor(it) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlatformSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    iconPainter: Painter? = null,
    iconVector: ImageVector? = null,
    healthDotColor: Color? = null,
) {
    val segShape = RoundedCornerShape(if (compact) 8.dp else 10.dp)
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        Color.Transparent
    }
    val stroke = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    check(iconPainter != null || iconVector != null)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(segShape)
            .then(
                if (selected) {
                    Modifier.border(1.dp, stroke, segShape)
                } else {
                    Modifier
                },
            )
            .background(bg)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = label
            }
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val iconSize = if (compact) 17.dp else 20.dp
        when {
            iconVector != null -> Icon(
                imageVector = iconVector,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = contentColor,
            )
            else -> Icon(
                painter = checkNotNull(iconPainter),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = contentColor,
            )
        }
        Spacer(modifier = Modifier.width(if (compact) 5.dp else 6.dp))
        Text(
            text = label,
            style = if (compact) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelLarge
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (healthDotColor != null) {
            Spacer(Modifier.width(if (compact) 4.dp else 5.dp))
            Box(
                Modifier
                    .size(if (compact) 6.dp else 7.dp)
                    .clip(CircleShape)
                    .background(healthDotColor),
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    DeckBridgeTheme(dynamicColor = false) {
        val res = LocalContext.current.resources
        HomeScreen(
            state = MockAppStateFactory.initial(1_700_000_000_000L, res),
            onDeckButtonTapped = {},
            onHostPlatformSelected = {},
        )
    }
}


@Composable
private fun DashboardAnimatedOverlay(theme: AnimatedBackgroundTheme, modifier: Modifier = Modifier) {
    when (theme) {
        AnimatedBackgroundTheme.GRID_PULSE -> DashboardEnergyPulsesOverlay(modifier)
        AnimatedBackgroundTheme.PARTICLES  -> DashboardParticlesOverlay(modifier)
        AnimatedBackgroundTheme.AURORA     -> DashboardAuroraOverlay(modifier)
    }
}
