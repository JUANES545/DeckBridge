package com.example.deckbridge.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.deckbridge.data.deck.DeckGridEditorCatalog
import com.example.deckbridge.data.repository.DeckBridgeRepository
import com.example.deckbridge.domain.deck.DeckGridActionKind
import com.example.deckbridge.domain.deck.DeckGridButtonPersisted
import com.example.deckbridge.domain.model.DeckButtonIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GridButtonEditViewModel(
    private val repository: DeckBridgeRepository,
    private val buttonId: String,
) : ViewModel() {

    private val _draft = MutableStateFlow<DeckGridButtonPersisted?>(null)
    val draft: StateFlow<DeckGridButtonPersisted?> = _draft.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    init {
        reloadFromRepository()
    }

    fun reloadFromRepository() {
        viewModelScope.launch {
            val cell = repository.getDeckGridButton(buttonId)
            if (cell == null) {
                _loadFailed.value = true
                _draft.value = null
            } else {
                _loadFailed.value = false
                _draft.value = sanitizeLoaded(cell)
            }
        }
    }

    private fun sanitizeLoaded(cell: DeckGridButtonPersisted): DeckGridButtonPersisted =
        when (cell.kind) {
            DeckGridActionKind.APP_LAUNCH,
            DeckGridActionKind.SCRIPT,
            -> cell.copy(
                kind = DeckGridActionKind.NOOP,
                intentId = DeckButtonIntent.Noop.intentId,
                payload = emptyMap(),
            )
            else -> cell
        }

    fun updateDraft(transform: (DeckGridButtonPersisted) -> DeckGridButtonPersisted) {
        _draft.update { current -> current?.let(transform) }
    }

    fun setKind(kind: DeckGridActionKind) {
        _draft.update { current ->
            current?.let { DeckGridEditorCatalog.mergeKindDefaults(it, kind) }
        }
    }

    fun setIntentId(intentId: String) {
        _draft.update { current -> current?.copy(intentId = intentId) }
    }

    fun setTextLiteral(value: String) {
        _draft.update { current ->
            current?.copy(payload = mapOf("literal" to value))
        }
    }

    private fun normalizedDraft(): DeckGridButtonPersisted? {
        val d = _draft.value ?: return null
        return d.copy(
            label = d.label.trim(),
            subtitle = d.subtitle.trim(),
            payload = if (d.kind == DeckGridActionKind.TEXT) {
                mapOf("literal" to d.payload["literal"].orEmpty().trim())
            } else {
                emptyMap()
            },
            iconToken = d.iconToken?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun save(onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val n = normalizedDraft()
            if (n == null) {
                onDone(Result.failure(IllegalStateException("draft")))
                return@launch
            }
            onDone(repository.updateDeckGridButton(n))
        }
    }

    fun resetToDefault(onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val r = repository.resetDeckGridButtonToDefault(buttonId)
            if (r.isSuccess) {
                _draft.value = repository.getDeckGridButton(buttonId)?.let { sanitizeLoaded(it) }
            }
            onDone(r)
        }
    }

    companion object {
        fun factory(repository: DeckBridgeRepository, buttonId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(GridButtonEditViewModel::class.java))
                    return GridButtonEditViewModel(repository, buttonId) as T
                }
            }
    }
}
