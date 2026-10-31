package com.google.adk.session

import com.google.adk.event.AgentEvent

interface SessionStore {
    suspend fun createSession(session: Session): Session
    suspend fun getSession(sessionId: String, userId: String): Session?
    suspend fun listSessions(userId: String, appName: String? = null): List<Session>
    suspend fun appendEvent(sessionId: String, event: AgentEvent)
    suspend fun updateState(sessionId: String, state: Map<String, Any?>)
    suspend fun deleteSession(sessionId: String)
}