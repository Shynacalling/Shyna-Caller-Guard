package com.example.callruleblocker.call

import android.telecom.Call
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * android.telecom.Call cannot be passed through an Intent (it's not
 * Parcelable) — it's only meaningful within the process that's bound as
 * the InCallService. Since our whole app runs in a single process, this
 * simple in-memory holder is how CallActivity finds out about the call
 * that InCallServiceImpl just received.
 */
object CallHolder {
    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall = _currentCall.asStateFlow()

    fun set(call: Call?) {
        _currentCall.value = call
    }
}
