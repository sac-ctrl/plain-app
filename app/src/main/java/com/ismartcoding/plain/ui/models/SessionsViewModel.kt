package com.ismartcoding.plain.ui.models

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ismartcoding.plain.db.DSession
import com.ismartcoding.plain.web.HttpServerManager
import com.ismartcoding.plain.web.SessionList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Instant

data class VSession(val clientId: String, val clientIP: String, val osName: String, val osVersion: String, val browserName: String, val browserVersion: String, val createdAt: Instant, val updatedAt: Instant) {
    companion object {
        fun from(data: DSession): VSession {
            return VSession(
                data.clientId,
                data.clientIP,
                data.osName,
                data.osVersion,
                data.browserName,
                data.browserVersion,
                data.createdAt,
                data.updatedAt,
            )
        }
    }
}

class SessionsViewModel : ViewModel() {
    private val _itemsFlow = MutableStateFlow(mutableStateListOf<VSession>())
    val itemsFlow: StateFlow<List<VSession>> get() = _itemsFlow

    fun fetch() {
        viewModelScope.launch(Dispatchers.IO) {
            // Hide loopback (localhost) sessions from the on-device "Sessions" list.
            // The session is still kept in the database so HttpServerManager.loadTokenCache()
            // can still authenticate the localhost browser — the user just doesn't see it
            // in the connected-devices list.
            _itemsFlow.value = SessionList.getItemsAsync()
                .filter { !isLoopback(it.clientIP) }
                .map { VSession.from(it) }
                .toMutableStateList()
        }
    }

    private fun isLoopback(ip: String): Boolean {
        val s = ip.trim().lowercase()
        if (s.isEmpty()) return false
        // Strip an optional port (e.g. "127.0.0.1:54321" or "[::1]:54321")
        val noPort = when {
            s.startsWith("[") -> s.substringAfter('[').substringBefore(']')
            s.count { it == ':' } == 1 && !s.contains("::") -> s.substringBefore(':')
            else -> s
        }
        return noPort == "127.0.0.1" ||
            noPort.startsWith("127.") ||
            noPort == "::1" ||
            noPort == "0:0:0:0:0:0:0:1" ||
            noPort == "::ffff:127.0.0.1" ||
            noPort.startsWith("::ffff:127.")
    }

    fun delete(clientId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            SessionList.deleteAsync(clientId)
            _itemsFlow.value.removeIf { it.clientId == clientId }
            HttpServerManager.loadTokenCache()
        }
    }
}
