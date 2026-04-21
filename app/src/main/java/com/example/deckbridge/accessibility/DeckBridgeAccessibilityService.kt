package com.example.deckbridge.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.deckbridge.DeckBridgeApplication
import com.example.deckbridge.logging.DeckBridgeLog

/**
 * System-level key event capture that forwards hardware keyboard events to
 * [com.example.deckbridge.data.repository.DeckBridgeRepository.notifyKeyEvent] even when
 * DeckBridge is minimized or another app is in the foreground.
 *
 * This service is opt-in: the user must enable it in Android Settings → Accessibility.
 * DeckBridge does NOT enable it automatically. The Settings screen shows its status
 * and links to the system page so the user can grant it.
 *
 * Key events are NOT consumed (returns false), so the original app still receives them.
 * DeckBridge uses the event only to trigger deck actions — it does not intercept typing.
 */
class DeckBridgeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        // Ensure the key-filter flag is set at runtime (XML config sets it too,
        // but enforcing here makes it reliable across OS versions).
        serviceInfo = serviceInfo?.also { info ->
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        DeckBridgeLog.state("accessibility: service connected — key events active")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val app = application as? DeckBridgeApplication ?: return false
        // Forward to the repository — same path as foreground key events in MainActivity.
        app.repository.notifyKeyEvent(event)
        // Return false so the target app still receives the key.
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // No window/view events needed — this service exists only for key forwarding.
    }

    override fun onInterrupt() {
        DeckBridgeLog.state("accessibility: interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DeckBridgeLog.state("accessibility: service unbound")
        return super.onUnbind(intent)
    }
}
