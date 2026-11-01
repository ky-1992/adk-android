package com.google.adk.demo.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.adk.demo.di.AppModule
import com.google.adk.session.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionsState(
    val sessions: List<Session> = emptyList(),
    val loading: Boolean = false,
)

class SessionsViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionStore = AppModule.sessionStore(application)

    private val _state = MutableStateFlow(SessionsState())
    val state: StateFlow<SessionsState> = _state.asStateFlow()

    fun loadSessions() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val sessions = sessionStore.listSessions("demo-user")
            _state.update { it.copy(sessions = sessions, loading = false) }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            sessionStore.deleteSession(sessionId)
            loadSessions()
        }
    }

    init { loadSessions() }
}