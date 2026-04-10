package com.example.deckbridge.domain.model

/**
 * What a deck button triggers on the host. Sealed for exhaustive handling as features grow
 * (USB HID, app-specific macros, shell scripts, etc.).
 */
sealed class ButtonAction {
    abstract val id: String
    abstract val summary: String

    data class Clipboard(
        override val id: String,
        val operation: ClipboardOperation,
    ) : ButtonAction() {
        override val summary: String = when (operation) {
            ClipboardOperation.COPY -> "Copiar"
            ClipboardOperation.PASTE -> "Pegar"
            ClipboardOperation.CUT -> "Cortar"
        }
    }

    data class Media(
        override val id: String,
        val control: MediaControl,
    ) : ButtonAction() {
        override val summary: String = when (control) {
            MediaControl.PLAY_PAUSE -> "Reproducir / Pausa"
            MediaControl.VOLUME_UP -> "Subir volumen"
            MediaControl.VOLUME_DOWN -> "Bajar volumen"
            MediaControl.MUTE -> "Silenciar"
        }
    }

    data class KeyChord(
        override val id: String,
        val keys: List<String>,
        val preferredPlatforms: Set<HostPlatform> = emptySet(),
    ) : ButtonAction() {
        override val summary: String = keys.joinToString(" + ")
    }

    /** Reserved for USB agent / host daemon integration in a later milestone. */
    data class HostPipeline(
        override val id: String,
        val pipelineName: String,
        val notes: String,
    ) : ButtonAction() {
        override val summary: String = pipelineName
    }
}

enum class ClipboardOperation {
    COPY,
    PASTE,
    CUT,
}

enum class MediaControl {
    PLAY_PAUSE,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
}
