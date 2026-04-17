package com.example.deckbridge.update

import android.net.Uri

sealed class UpdateState {
    object Idle : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class NeedsPermission(val info: UpdateInfo) : UpdateState()
    /** [progress] in 0..1, or -1f while size is unknown. */
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState()
    data class ReadyToInstall(val info: UpdateInfo, val apkUri: Uri) : UpdateState()
    object Dismissed : UpdateState()
}
