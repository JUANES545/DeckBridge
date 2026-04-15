package com.example.deckbridge.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.deckbridge.data.deck.DeckGridEditorCatalog
import com.example.deckbridge.data.repository.DeckBridgeRepository
import com.example.deckbridge.domain.deck.DeckGridActionKind
import com.example.deckbridge.domain.deck.DeckKnobActionPersisted
import com.example.deckbridge.domain.deck.DeckKnobPersisted
import com.example.deckbridge.domain.model.DeckButtonIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class KnobEditViewModel(
    private val repository: DeckBridgeRepository,
    private val knobId: String,
) : ViewModel() {

    private val _draft = MutableStateFlow<DeckKnobPersisted?>(null)
    val draft: StateFlow<DeckKnobPersisted?> = _draft.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    init {
        reloadFromRepository()
    }

    fun reloadFromRepository() {
        viewModelScope.launch {
            val knob = repository.getDeckKnob(knobId)
            if (knob == null) {
                _loadFailed.value = true
                _draft.value = null
            } else {
                _loadFailed.value = false
                _draft.value = sanitizeLoaded(knob)
            }
        }
    }

    private fun sanitizeLoaded(knob: DeckKnobPersisted): DeckKnobPersisted =
        knob.copy(
            rotateCcw = sanitizeAction(knob.rotateCcw),
            rotateCw = sanitizeAction(knob.rotateCw),
            press = sanitizeAction(knob.press),
        )

    private fun sanitizeAction(action: DeckKnobActionPersisted): DeckKnobActionPersisted =
        when (action.kind) {
            DeckGridActionKind.APP_LAUNCH,
            DeckGridActionKind.SCRIPT,
            -> DeckKnobActionPersisted(
                kind = DeckGridActionKind.NOOP,
                intentId = DeckButtonIntent.Noop.intentId,
                payload = emptyMap(),
            )
            else -> action
        }

    fun updateDraft(transform: (DeckKnobPersisted) -> DeckKnobPersisted) {
        _draft.update { current -> current?.let(transform) }
    }

    fun setBindingKind(binding: KnobEditBinding, kind: DeckGridActionKind) {
        _draft.update { current ->
            current?.let { k ->
                val slice = when (binding) {
                    KnobEditBinding.ROTATE_CCW -> k.rotateCcw
                    KnobEditBinding.ROTATE_CW -> k.rotateCw
                    KnobEditBinding.PRESS -> k.press
                }
                val merged = DeckGridEditorCatalog.mergeKindDefaultsForKnobAction(slice, kind)
                when (binding) {
                    KnobEditBinding.ROTATE_CCW -> k.copy(rotateCcw = merged)
                    KnobEditBinding.ROTATE_CW -> k.copy(rotateCw = merged)
                    KnobEditBinding.PRESS -> k.copy(press = merged)
                }
            }
        }
    }

    fun setBindingIntentId(binding: KnobEditBinding, intentId: String) {
        _draft.update { current ->
            current?.let { k ->
                when (binding) {
                    KnobEditBinding.ROTATE_CCW -> k.copy(rotateCcw = k.rotateCcw.copy(intentId = intentId))
                    KnobEditBinding.ROTATE_CW -> k.copy(rotateCw = k.rotateCw.copy(intentId = intentId))
                    KnobEditBinding.PRESS -> k.copy(press = k.press.copy(intentId = intentId))
                }
            }
        }
    }

    fun setBindingTextLiteral(binding: KnobEditBinding, value: String) {
        _draft.update { current ->
            current?.let { k ->
                val p = mapOf("literal" to value)
                when (binding) {
                    KnobEditBinding.ROTATE_CCW -> k.copy(rotateCcw = k.rotateCcw.copy(payload = p))
                    KnobEditBinding.ROTATE_CW -> k.copy(rotateCw = k.rotateCw.copy(payload = p))
                    KnobEditBinding.PRESS -> k.copy(press = k.press.copy(payload = p))
                }
            }
        }
    }

    private fun normalizedDraft(): DeckKnobPersisted? {
        val d = _draft.value ?: return null
        fun norm(a: DeckKnobActionPersisted): DeckKnobActionPersisted = when (a.kind) {
            DeckGridActionKind.TEXT ->
                a.copy(payload = mapOf("literal" to a.payload["literal"].orEmpty().trim()))
            else -> a.copy(payload = emptyMap())
        }
        return d.copy(
            label = d.label.trim(),
            subtitle = d.subtitle.trim(),
            rotateCcw = norm(d.rotateCcw),
            rotateCw = norm(d.rotateCw),
            press = norm(d.press),
        )
    }

    fun save(onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val n = normalizedDraft()
            if (n == null) {
                onDone(Result.failure(IllegalStateException("draft")))
                return@launch
            }
            onDone(repository.updateDeckKnob(n))
        }
    }

    fun resetToDefault(onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val r = repository.resetDeckKnobToDefault(knobId)
            if (r.isSuccess) {
                _draft.value = repository.getDeckKnob(knobId)?.let { sanitizeLoaded(it) }
            }
            onDone(r)
        }
    }

    companion object {
        fun factory(repository: DeckBridgeRepository, knobId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(KnobEditViewModel::class.java))
                    return KnobEditViewModel(repository, knobId) as T
                }
            }
    }
}
