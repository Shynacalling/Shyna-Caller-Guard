package com.example.callruleblocker.ui

import android.content.Context
import android.view.SoundEffectConstants
import android.view.View

/** Lightweight, user-controllable UI click feedback. */
object UiFeedback {
    const val PREFS = "ui_feedback_prefs"
    const val KEY_BUTTON_SOUND = "button_sound_enabled"

    fun isSoundEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BUTTON_SOUND, true)

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BUTTON_SOUND, enabled).apply()
    }

    fun playClick(context: Context, view: View) {
        if (isSoundEnabled(context)) {
            runCatching { view.playSoundEffect(SoundEffectConstants.CLICK) }
        }
    }
}
