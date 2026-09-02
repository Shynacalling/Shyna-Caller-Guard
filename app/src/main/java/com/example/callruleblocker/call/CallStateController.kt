package com.example.callruleblocker.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MainCallType {
    PHONE_DIALER,
    SHYNA_LINK,
    OFFLINE_CALL
}

enum class GlobalCallState {
    IDLE,
    INCOMING,
    CONNECTING,
    ACTIVE,
    ON_HOLD,
    INTERRUPTED,
    ENDED,
    FAILED
}

data class GlobalCallSession(
    val callId: String,
    val callType: MainCallType,
    val state: GlobalCallState,
    val userId: String?,
    val startedAt: Long?
)

data class ShynaHandshake(
    val protocolVersion: Int,
    val userId: String,
    val displayName: String,
    val deviceId: String,
    val supportsBluetooth: Boolean,
    val supportsWifiDirect: Boolean,
    val supportsRadio: Boolean,
    val supportsVoice: Boolean,
    val supportsMessaging: Boolean
)

object CallStateController {
    private val _primaryFeature = MutableStateFlow(MainCallType.PHONE_DIALER)
    val primaryFeature = _primaryFeature.asStateFlow()

    private val _globalState = MutableStateFlow(GlobalCallState.IDLE)
    val globalState = _globalState.asStateFlow()

    private val _activeSession = MutableStateFlow<GlobalCallSession?>(null)
    val activeSession = _activeSession.asStateFlow()

    private val activeSessions = mutableMapOf<MainCallType, GlobalCallSession>()

    fun setPrimaryFeature(feature: MainCallType) {
        _primaryFeature.value = feature
    }

    fun reportCallEvent(type: MainCallType, state: GlobalCallState, callId: String = "default", userId: String? = null) {
        android.util.Log.d("ShynaCall", "REPORT_CALL_EVENT: type=$type state=$state id=$callId")
        val session = GlobalCallSession(callId, type, state, userId, System.currentTimeMillis())
        if (state == GlobalCallState.ENDED || state == GlobalCallState.FAILED) {
            activeSessions.remove(type)
        } else {
            activeSessions[type] = session
        }
        resolvePriority()
    }

    private fun resolvePriority() {
        // Priority: PHONE_DIALER (3) > SHYNA_LINK (2) > OFFLINE_CALL (1)
        val sortedActive = activeSessions.values.sortedByDescending { 
            when (it.callType) {
                MainCallType.PHONE_DIALER -> 3
                MainCallType.SHYNA_LINK -> 2
                MainCallType.OFFLINE_CALL -> 1
            }
        }

        val topSession = sortedActive.firstOrNull()
        if (topSession == null) {
            if (_globalState.value != GlobalCallState.IDLE) {
                android.util.Log.d("ShynaCall", "GLOBAL_STATE_CHANGE: ${_globalState.value} -> IDLE")
                _globalState.value = GlobalCallState.IDLE
            }
            _activeSession.value = null
            return
        }

        // Apply Interruption to lower priority calls
        sortedActive.drop(1).forEach { lower ->
            if (lower.state == GlobalCallState.ACTIVE) {
                activeSessions[lower.callType] = lower.copy(state = GlobalCallState.INTERRUPTED)
            }
        }

        if (_globalState.value != topSession.state || _activeSession.value?.callId != topSession.callId) {
            android.util.Log.d("ShynaCall", "GLOBAL_STATE_CHANGE: ${_globalState.value} -> ${topSession.state} (id=${topSession.callId})")
        }
        
        _activeSession.value = topSession
        _globalState.value = topSession.state
    }

    fun getSecondaryFeatures(): List<MainCallType> {
        val primary = _primaryFeature.value
        return MainCallType.entries.filter { it != primary }.sortedBy { 
            when (it) {
                MainCallType.PHONE_DIALER -> 0
                MainCallType.SHYNA_LINK -> 1
                MainCallType.OFFLINE_CALL -> 2
            }
        }
    }
}
