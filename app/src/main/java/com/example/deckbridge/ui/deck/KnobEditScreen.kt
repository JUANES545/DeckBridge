@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.deckbridge.ui.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.deckbridge.R
import com.example.deckbridge.data.deck.DeckEditorIntentOption
import com.example.deckbridge.data.deck.DeckGridEditorCatalog
import com.example.deckbridge.data.deck.DeckGridIntentCodec
import com.example.deckbridge.domain.PlatformActionResolver
import com.example.deckbridge.domain.deck.DeckGridActionKind
import com.example.deckbridge.domain.deck.DeckKnobActionPersisted
import com.example.deckbridge.domain.deck.DeckKnobPersisted
import com.example.deckbridge.domain.model.HostPlatform
import kotlinx.coroutines.launch

@Composable
fun KnobEditScreen(
    viewModel: KnobEditViewModel,
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
                title = { Text(stringResource(R.string.knob_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.grid_edit_cancel),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        val d = draft
        if (d == null && !loadFailed) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.knob_edit_loading), modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        if (d == null) return@Scaffold

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KnobPreviewHeader(knob = d, hostPlatform = hostPlatform)

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

            KnobBindingSection(
                sectionTitle = stringResource(R.string.knob_edit_section_rotate_left),
                action = d.rotateCcw,
                hostPlatform = hostPlatform,
                binding = KnobEditBinding.ROTATE_CCW,
                viewModel = viewModel,
            )

            KnobBindingSection(
                sectionTitle = stringResource(R.string.knob_edit_section_rotate_right),
                action = d.rotateCw,
                hostPlatform = hostPlatform,
                binding = KnobEditBinding.ROTATE_CW,
                viewModel = viewModel,
            )

            KnobBindingSection(
                sectionTitle = stringResource(R.string.knob_edit_section_press),
                action = d.press,
                hostPlatform = hostPlatform,
                binding = KnobEditBinding.PRESS,
                viewModel = viewModel,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.resetToDefault { r ->
                            scope.launch {
                                if (r.isSuccess) {
                                    snackbarHostState.showSnackbar(context.getString(R.string.knob_edit_reset_done))
                                } else {
                                    snackbarHostState.showSnackbar(
                                        r.exceptionOrNull()?.message
                                            ?: context.getString(R.string.grid_edit_err_generic),
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.grid_edit_reset_default))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.grid_edit_cancel))
                }
                Button(
                    onClick = {
                        viewModel.save { r ->
                            scope.launch {
                                if (r.isSuccess) {
                                    onBack()
                                } else {
                                    snackbarHostState.showSnackbar(
                                        r.exceptionOrNull()?.message
                                            ?: context.getString(R.string.grid_edit_err_generic),
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.grid_edit_save))
                }
            }
        }
    }
}

@Composable
private fun KnobPreviewHeader(knob: DeckKnobPersisted, hostPlatform: HostPlatform) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.knob_edit_preview_section),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                knob.label.ifBlank { "—" },
                style = MaterialTheme.typography.titleMedium,
            )
            if (knob.subtitle.isNotBlank()) {
                Text(
                    knob.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PreviewBindingLine(
                label = stringResource(R.string.knob_edit_section_rotate_left),
                action = knob.rotateCcw,
                hostPlatform = hostPlatform,
            )
            PreviewBindingLine(
                label = stringResource(R.string.knob_edit_section_rotate_right),
                action = knob.rotateCw,
                hostPlatform = hostPlatform,
            )
            PreviewBindingLine(
                label = stringResource(R.string.knob_edit_section_press),
                action = knob.press,
                hostPlatform = hostPlatform,
            )
        }
    }
}

@Composable
private fun PreviewBindingLine(
    label: String,
    action: DeckKnobActionPersisted,
    hostPlatform: HostPlatform,
) {
    val intent = DeckGridIntentCodec.intentFromKnobAction(action)
    val resolved = PlatformActionResolver.resolve(intent, hostPlatform)
    Text(
        text = "$label · ${resolved.shortcutDisplay}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun KnobBindingSection(
    sectionTitle: String,
    action: DeckKnobActionPersisted,
    hostPlatform: HostPlatform,
    binding: KnobEditBinding,
    viewModel: KnobEditViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val intent = DeckGridIntentCodec.intentFromKnobAction(action)
            val resolved = PlatformActionResolver.resolve(intent, hostPlatform)
            Text(
                text = resolved.shortcutDisplay,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            KindChoiceSectionKnob(
                selectedKind = action.kind,
                onSelectKind = { viewModel.setBindingKind(binding, it) },
            )

            when (action.kind) {
                DeckGridActionKind.TEXT -> {
                    OutlinedTextField(
                        value = action.payload["literal"].orEmpty(),
                        onValueChange = { viewModel.setBindingTextLiteral(binding, it) },
                        label = { Text(stringResource(R.string.grid_edit_text_body)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2,
                    )
                }
                DeckGridActionKind.KEY -> {
                    IntentChoiceSectionKnob(
                        sectionTitle = stringResource(R.string.grid_edit_key_title),
                        options = DeckGridEditorCatalog.keyOptions,
                        selectedIntentId = action.intentId,
                        onSelect = { viewModel.setBindingIntentId(binding, it) },
                    )
                }
                DeckGridActionKind.COMBO -> {
                    IntentChoiceSectionKnob(
                        sectionTitle = stringResource(R.string.grid_edit_combo_title),
                        options = DeckGridEditorCatalog.comboOptions,
                        selectedIntentId = action.intentId,
                        onSelect = { viewModel.setBindingIntentId(binding, it) },
                    )
                }
                DeckGridActionKind.MEDIA -> {
                    IntentChoiceSectionKnob(
                        sectionTitle = stringResource(R.string.grid_edit_media_title),
                        options = DeckGridEditorCatalog.mediaOptions,
                        selectedIntentId = action.intentId,
                        onSelect = { viewModel.setBindingIntentId(binding, it) },
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
        }
    }
}

@Composable
private fun KindChoiceSectionKnob(
    selectedKind: DeckGridActionKind,
    onSelectKind: (DeckGridActionKind) -> Unit,
) {
    Text(
        text = stringResource(R.string.grid_edit_action_type),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    DeckGridEditorCatalog.editableKinds.forEach { kind ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selectedKind == kind,
                onClick = { onSelectKind(kind) },
            )
            Text(
                text = knobEditKindLabel(kind),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun knobEditKindLabel(kind: DeckGridActionKind): String = when (kind) {
    DeckGridActionKind.TEXT -> stringResource(R.string.grid_edit_kind_text)
    DeckGridActionKind.KEY -> stringResource(R.string.grid_edit_kind_key)
    DeckGridActionKind.COMBO -> stringResource(R.string.grid_edit_kind_combo)
    DeckGridActionKind.MEDIA -> stringResource(R.string.grid_edit_kind_media)
    DeckGridActionKind.NOOP -> stringResource(R.string.grid_edit_kind_noop)
    DeckGridActionKind.APP_LAUNCH -> stringResource(R.string.grid_edit_kind_app_launch)
    DeckGridActionKind.SCRIPT -> stringResource(R.string.grid_edit_kind_script)
}

@Composable
private fun IntentChoiceSectionKnob(
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
    options.forEach { opt ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selectedIntentId == opt.intentId,
                onClick = { onSelect(opt.intentId) },
            )
            Text(
                text = stringResource(opt.labelRes),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
