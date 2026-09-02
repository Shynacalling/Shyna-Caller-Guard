package com.example.callruleblocker.call

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local bridge between the bound InCallService and the Compose call UI. */
object CallControlCenter {
    private var service: InCallService? = null
    private val calls = linkedSetOf<Call>()

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    val audioState = _audioState.asStateFlow()

    private val _allCalls = MutableStateFlow<List<Call>>(emptyList())
    val allCalls = _allCalls.asStateFlow()

    fun attach(value: InCallService) { service = value }
    fun detach(value: InCallService) { if (service === value) service = null }

    fun add(call: Call) {
        calls += call
        _allCalls.value = calls.toList()
        updateGlobalState()
    }

    fun remove(call: Call) {
        calls -= call
        _allCalls.value = calls.toList()
        updateGlobalState()
    }

    private fun updateGlobalState() {
        val activeCalls = calls.filter { it.state != Call.STATE_DISCONNECTED }
        val hasActive = activeCalls.isNotEmpty()
        
        if (!hasActive) {
            CallStateController.reportCallEvent(MainCallType.PHONE_DIALER, GlobalCallState.ENDED)
        } else {
            val ringing = activeCalls.any { it.state == Call.STATE_RINGING }
            val state = if (ringing) GlobalCallState.INCOMING else GlobalCallState.ACTIVE
            CallStateController.reportCallEvent(MainCallType.PHONE_DIALER, state)
        }
    }

    fun updateAudioState(state: CallAudioState) {
        val oldState = _audioState.value
        _audioState.value = state

        // AUTO-BLUETOOTH LOGIC:
        // If Bluetooth becomes available (connected) while a call is active, 
        // automatically switch to it for a seamless hands-free experience.
        val bluetoothAvailable = state.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0
        val wasBluetoothAvailable = oldState?.let { it.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0 } ?: false
        
        if (bluetoothAvailable && !wasBluetoothAvailable && state.route != CallAudioState.ROUTE_BLUETOOTH) {
            val hasActiveSession = _allCalls.value.any { it.state == Call.STATE_ACTIVE || it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
            if (hasActiveSession) {
                setRoute(CallAudioState.ROUTE_BLUETOOTH)
            }
        }
    }

    fun setMuted(muted: Boolean) { service?.setMuted(muted) }

    @Suppress("DEPRECATION")
    fun setRoute(route: Int): Boolean {
        val current = _audioState.value ?: return false
        if (current.supportedRouteMask and route == 0) return false
        val attached = service ?: return false
        attached.setAudioRoute(route)
        return true
    }

    fun end(call: Call) {
        endCallTree(call)
    }

    /**
     * Ends the complete Telecom session represented by [call]. Video calls and
     * conference calls may expose a parent plus child calls; disconnecting only
     * one child can leave the carrier session and camera alive. This method
     * terminates the parent/children and any remaining live calls owned by this
     * InCallService, then clears the process call holder.
     */
    fun endAllActiveCalls() {
        val snapshot = calls.toList()
        snapshot.forEach(::endCallTree)
        CallHolder.set(null)
    }

    private fun endCallTree(call: Call) {
        val targets = linkedSetOf<Call>()
        call.parent?.let(targets::add)
        targets += call
        targets += call.children

        targets.forEach { target ->
            runCatching {
                target.videoCall?.apply {
                    setCamera(null)
                    setPreviewSurface(null)
                    setDisplaySurface(null)
                }
            }
            runCatching {
                if (target.state == Call.STATE_RINGING) target.reject(false, null)
                else if (target.state != Call.STATE_DISCONNECTED && target.state != Call.STATE_DISCONNECTING) target.disconnect()
            }
        }
    }

    fun merge(call: Call): Boolean {
        call.conferenceableCalls.firstOrNull()?.let { candidate ->
            call.conference(candidate)
            return true
        }
        _allCalls.value.forEach { source ->
            source.conferenceableCalls.firstOrNull()?.let { candidate ->
                source.conference(candidate)
                return true
            }
        }
        return false
    }

    fun swap(active: Call, held: Call) {
        held.unhold()
        active.hold()
        CallHolder.set(held)
    }

    fun answerAndHold(incoming: Call, active: Call) {
        active.hold()
        incoming.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
        CallHolder.set(incoming)
    }
}
