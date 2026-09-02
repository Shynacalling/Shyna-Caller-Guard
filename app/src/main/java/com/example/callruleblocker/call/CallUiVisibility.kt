package com.example.callruleblocker.call

/**
 * Process-local foreground state for the dedicated telecom call UI.
 *
 * The InCallService reads this only to avoid showing a duplicate incoming
 * heads-up banner while CallActivity itself is already visible.
 */
object CallUiVisibility {
    @Volatile
    var isCallScreenForeground: Boolean = false
        private set

    fun onResumed() {
        isCallScreenForeground = true
    }

    fun onPaused() {
        isCallScreenForeground = false
    }
}
