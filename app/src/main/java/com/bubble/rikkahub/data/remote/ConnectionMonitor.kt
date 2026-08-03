package com.bubble.rikkahub.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tracks whether the RikkaHub server is reachable. ViewModels report success/failure via
 * [reportSuccess]/[reportFailure]; while offline a lightweight probe polls periodically,
 * and when the connection comes back [onReconnect] runs (used to flush queued messages).
 */
class ConnectionMonitor(
    private val api: RikkaHubApi,
    private val scope: CoroutineScope,
    private val onReconnect: suspend () -> Unit = {}
) {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var probeJob: Job? = null

    fun reportSuccess() {
        if (!_isOnline.value) {
            _isOnline.value = true
            probeJob?.cancel()
            probeJob = null
            scope.launch { runCatching { onReconnect() } }
        }
    }

    fun reportFailure() {
        if (_isOnline.value) {
            _isOnline.value = false
            startProbing()
        }
    }

    private fun startProbing() {
        probeJob?.cancel()
        probeJob = scope.launch {
            while (!_isOnline.value) {
                delay(PROBE_INTERVAL_MS)
                val reachable = runCatching { api.getConversations() }.isSuccess
                if (reachable) {
                    _isOnline.value = true
                    runCatching { onReconnect() }
                    probeJob = null
                    return@launch
                }
            }
        }
    }

    private companion object {
        const val PROBE_INTERVAL_MS = 10_000L
    }
}
