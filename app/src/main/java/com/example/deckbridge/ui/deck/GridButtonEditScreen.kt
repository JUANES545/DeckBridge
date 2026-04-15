@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.deckbridge.ui.deck

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.DoNotTouch
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.deckbridge.R
import com.example.deckbridge.data.deck.DeckEditorIntentOption
import com.example.deckbridge.data.deck.DeckGridEditorCatalog
import com.example.deckbridge.data.deck.DeckGridIntentCodec
import com.example.deckbridge.domain.PlatformActionResolver
import com.example.deckbridge.domain.deck.DeckGridActionKind
import com.example.deckbridge.domain.deck.DeckGridButtonPersisted
import com.example.deckbridge.domain.model.HostPlatform
import com.example.deckbridge.ui.theme.DeckBridgeTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun GridButtonEditScreen(
    viewModel: GridButtonEditViewModel,
    hostPlatform: HostPlatform,
    onBack: () -> Unit,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(loadFailed) {
        if (loadFailed) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.grid_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.grid_edit_cancel))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            draft?.let { d ->
                val landscape =
                    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                GridEditBottomBar(
                    compact = landscape,
                    onReset = {
                        viewModel.resetToDefault { r ->
                            scope.launch {
                                if (r.isSuccess) {
                                    snackbarHostState.showSnackbar(context.getString(R.string.grid_edit_reset_done))
                                } else {
                                    snackbarHostState.showSnackbar(
                                        r.exceptionOrNull()?.message ?: context.getString(R.string.grid_edit_err_generic),
                                    )
                                }
                            }
                        }
                    },
                    onCancel = onBack,
                    onSave = {
                        viewModel.save { r ->
                            scope.launch {
                                if (r.isSuccess) {
                                    onBack()
                                } else {
                                    snackbarHostState.showSnackbar(
                                        r.exceptionOrNull()?.message ?: context.getString(R.string.grid_edit_err_generic),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        val d = draft
        if (d == null && !loadFailed) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.grid_edit_loading), modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        if (d == null) return@Scaffold

        val wide = LocalConfiguration.current.screenWidthDp >= 560
        val scroll = rememberScrollState()

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.grid_edit_section_preview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.grid_edit_preview_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GridEditDeckPreviewCard(
                            d = d,
                            hostPlatform = hostPlatform,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        IdentityFields(d = d, viewModel = viewModel)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        GridEditActionAndIconSection(d = d, viewModel = viewModel)
                    }
                }
            } else {
                GridEditDeckPreviewCard(d = d, hostPlatform = hostPlatform, modifier = Modifier.fillMaxWidth())
                IdentityFields(d = d, viewModel = viewModel)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                GridEditActionAndIconSection(d = d, viewModel = viewModel)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GridEditActionAndIconSection(
    d: DeckGridButtonPersisted,
    viewModel: GridButtonEditViewModel,
) {
    Text(
        text = stringResource(R.string.grid_edit_section_action),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    KindChoiceChips(d = d, onSelectKind = { viewModel.setKind(it) })

    when (d.kind) {
        DeckGridActionKind.TEXT -> {
            OutlinedTextField(
                value = d.payload["literal"].orEmpty(),
                onValueChange = { viewModel.setTextLiteral(it) },
                label = { Text(stringResource(R.string.grid_edit_text_body)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 3,
            )
        }
        DeckGridActionKind.KEY -> {
            IntentChoiceCards(
                sectionTitle = stringResource(R.string.grid_edit_key_title),
                options = DeckGridEditorCatalog.keyOptions,
                selectedIntentId = d.intentId,
                onSelect = { viewModel.setIntentId(it) },
            )
        }
        DeckGridActionKind.COMBO -> {
            IntentChoiceCards(
                sectionTitle = stringResource(R.string.grid_edit_combo_title),
                options = DeckGridEditorCatalog.comboOptions,
                selectedIntentId = d.intentId,
                onSelect = { viewModel.setIntentId(it) },
            )
        }
        DeckGridActionKind.MEDIA -> {
            IntentChoiceCards(
                sectionTitle = stringResource(R.string.grid_edit_media_title),
                options = DeckGridEditorCatalog.mediaOptions,
                selectedIntentId = d.intentId,
                onSelect = { viewModel.setIntentId(it) },
            )
        }
        DeckGridActionKind.NOOP -> {
            Text(
                text = stringResource(R.string.grid_edit_noop_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DeckGridActionKind.APP_LAUNCH,
        DeckGridActionKind.SCRIPT,
        -> Unit
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

    Text(
        text = stringResource(R.string.grid_edit_section_icon),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = stringResource(R.string.grid_edit_icon_suggested_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    IconTokenPicker(
        selectedToken = d.iconToken,
        onPick = { token -> viewModel.updateDraft { it.copy(iconToken = token) } },
    )
    OutlinedTextField(
        value = d.iconToken.orEmpty(),
        onValueChange = { v -> viewModel.updateDraft { it.copy(iconToken = v.takeIf { it.isNotBlank() }) } },
        label = { Text(stringResource(R.string.grid_edit_icon_token_hint)) },
        placeholder = { Text(stringResource(R.string.grid_edit_icon_token_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun GridEditBottomBar(
    compact: Boolean,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val resetLabel = stringResource(R.string.grid_edit_reset_default)
    val vPad = if (compact) 4.dp else 10.dp
    val gap = if (compact) 6.dp else 10.dp
    val hPad = if (compact) 12.dp else 16.dp
    val btnPad = if (compact) {
        PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    } else {
        ButtonDefaults.ContentPadding
    }
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = if (compact) 2.dp else 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (compact) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad, vertical = vPad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restore,
                        contentDescription = resetLabel,
                        modifier = Modifier.size(22.dp),
                    )
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    contentPadding = btnPad,
                ) {
                    Text(
                        stringResource(R.string.grid_edit_cancel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    contentPadding = btnPad,
                ) {
                    Text(
                        stringResource(R.string.grid_edit_save),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad, vertical = vPad),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(resetLabel)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.grid_edit_cancel))
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.grid_edit_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityFields(d: DeckGridButtonPersisted, viewModel: GridButtonEditViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.grid_edit_section_identity),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = d.label,
            onValueChange = { v -> viewModel.updateDraft { it.copy(label = v) } },
            label = { Text(stringResource(R.string.grid_edit_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = d.subtitle,
            onValueChange = { v -> viewModel.updateDraft { it.copy(subtitle = v) } },
            label = { Text(stringResource(R.string.grid_edit_subtitle_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

private val previewTilePalettes: List<List<Color>> = listOf(
    listOf(Color(0xFF181820), Color(0xFF0A0A10)),
    listOf(Color(0xFF16161E), Color(0xFF0B0B12)),
    listOf(Color(0xFF1A1A24), Color(0xFF08080E)),
    listOf(Color(0xFF14141A), Color(0xFF0D0D14)),
)

@Composable
private fun GridEditDeckPreviewCard(
    d: DeckGridButtonPersisted,
    hostPlatform: HostPlatform,
    modifier: Modifier = Modifier,
) {
    val intent = DeckGridIntentCodec.intentFromPersisted(d)
    val resolved = PlatformActionResolver.resolve(intent, hostPlatform)
    val palette = previewTilePalettes[d.id.hashCode().absoluteValue % previewTilePalettes.size]
    val outerBrush = Brush.linearGradient(palette)
    val innerBrush = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.16f),
            Color.White.copy(alpha = 0.05f),
        ),
    )
    val shape = RoundedCornerShape(18.dp)

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(outerBrush),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(innerBrush),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = deckGridSlotGlyph(d.iconToken),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White.copy(alpha = 0.92f),
                        )
                    }
                    Text(
                        text = d.label.ifBlank { "—" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = 0.96f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = d.subtitle.ifBlank { " " },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                AssistPill(text = kindLabel(d.kind))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = resolved.intentDisplayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = resolved.shortcutDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun AssistPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun KindChoiceChips(d: DeckGridButtonPersisted, onSelectKind: (DeckGridActionKind) -> Unit) {
    val kinds = DeckGridEditorCatalog.editableKinds
    val firstRow = kinds.take(3)
    val secondRow = kinds.drop(3)
    val scrollA = rememberScrollState()
    val scrollB = rememberScrollState()
    Text(
        text = stringResource(R.string.grid_edit_action_type),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollA),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            firstRow.forEach { kind ->
                KindFilterChip(
                    kind = kind,
                    selected = d.kind == kind,
                    onSelect = onSelectKind,
                )
            }
        }
        if (secondRow.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollB),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                secondRow.forEach { kind ->
                    KindFilterChip(
                        kind = kind,
                        selected = d.kind == kind,
                        onSelect = onSelectKind,
                    )
                }
            }
        }
    }
}

@Composable
private fun KindFilterChip(
    kind: DeckGridActionKind,
    selected: Boolean,
    onSelect: (DeckGridActionKind) -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelect(kind) },
        label = {
            Text(
                text = kindLabel(kind),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = kindIcon(kind),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            borderWidth = if (selected) 1.5.dp else 1.dp,
        ),
    )
}

@Composable
private fun kindIcon(kind: DeckGridActionKind) = when (kind) {
    DeckGridActionKind.TEXT -> Icons.Filled.TextFields
    DeckGridActionKind.KEY -> Icons.Filled.Keyboard
    DeckGridActionKind.COMBO -> Icons.Filled.TouchApp
    DeckGridActionKind.MEDIA -> Icons.Filled.Audiotrack
    DeckGridActionKind.NOOP -> Icons.Filled.DoNotTouch
    DeckGridActionKind.APP_LAUNCH,
    DeckGridActionKind.SCRIPT,
    -> Icons.Filled.TextFields
}

@Composable
private fun kindLabel(kind: DeckGridActionKind): String = when (kind) {
    DeckGridActionKind.TEXT -> stringResource(R.string.grid_edit_kind_text)
    DeckGridActionKind.KEY -> stringResource(R.string.grid_edit_kind_key)
    DeckGridActionKind.COMBO -> stringResource(R.string.grid_edit_kind_combo)
    DeckGridActionKind.MEDIA -> stringResource(R.string.grid_edit_kind_media)
    DeckGridActionKind.NOOP -> stringResource(R.string.grid_edit_kind_noop)
    DeckGridActionKind.APP_LAUNCH -> stringResource(R.string.grid_edit_kind_app_launch)
    DeckGridActionKind.SCRIPT -> stringResource(R.string.grid_edit_kind_script)
}

@Composable
private fun IntentChoiceCards(
    sectionTitle: String,
    options: List<DeckEditorIntentOption>,
    selectedIntentId: String,
    onSelect: (String) -> Unit,
) {
    Text(
        text = sectionTitle,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { opt ->
            val selected = selectedIntentId == opt.intentId
            Card(
                onClick = { onSelect(opt.intentId) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
                border = if (selected) {
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(opt.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun IconTokenPicker(
    selectedToken: String?,
    onPick: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DeckGridEditorCatalog.suggestedIconTokens.forEach { opt ->
            val selected = selectedToken == opt.token
            FilterChip(
                selected = selected,
                onClick = { onPick(opt.token) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = deckGridSlotGlyph(opt.token),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(opt.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun GridEditDeckPreviewCardPreviewPortrait() {
    DeckBridgeTheme(dynamicColor = false) {
        val sample = DeckGridButtonPersisted(
            id = "btn_preview",
            sortIndex = 0,
            label = "Jamón 123",
            subtitle = "Windows · type text",
            kind = DeckGridActionKind.TEXT,
            intentId = "deck.intent.inject_text",
            payload = mapOf("literal" to "Hola PC"),
            iconToken = "text_jamon",
        )
        GridEditDeckPreviewCard(
            d = sample,
            hostPlatform = HostPlatform.WINDOWS,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 720, heightDp = 360)
@Composable
private fun GridEditDeckPreviewCardPreviewLandscape() {
    DeckBridgeTheme(dynamicColor = false) {
        val sample = DeckGridButtonPersisted(
            id = "btn_paste",
            sortIndex = 2,
            label = "Paste",
            subtitle = "Ctrl+V",
            kind = DeckGridActionKind.COMBO,
            intentId = com.example.deckbridge.domain.model.DeckButtonIntent.KeyboardChord.Paste.intentId,
            iconToken = "content_paste",
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            GridEditDeckPreviewCard(
                d = sample,
                hostPlatform = HostPlatform.WINDOWS,
                modifier = Modifier.width(160.dp),
            )
            KindChoiceChips(d = sample, onSelectKind = {})
        }
    }
}
