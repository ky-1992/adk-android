package com.google.adk.session.sqlite

import android.content.Context
import com.google.adk.event.AgentEvent
import com.google.adk.event.TokenUsage
import com.google.adk.session.Session
import com.google.adk.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.time.Instant

class SqliteSessionStore(context: Context) : SessionStore {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.sessionDao()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    override suspend fun createSession(session: Session): Session = withContext(Dispatchers.IO) {
        dao.upsertSession(SessionEntity(
            id = session.id,
            userId = session.userId,
            appName = session.appName,
            agentName = session.agentName,
            stateJson = json.encodeToString(session.state.mapValues { it.value?.toString() }),
            createdAt = session.createdAt.toEpochMilli(),
            updatedAt = session.updatedAt.toEpochMilli(),
        ))
        session
    }

    override suspend fun getSession(sessionId: String, userId: String): Session? = withContext(Dispatchers.IO) {
        val entity = dao.getSession(sessionId, userId) ?: return@withContext null
        val events = dao.getEvents(sessionId).map { it.toAgentEvent() }
        entity.toSession(events)
    }

    override suspend fun listSessions(userId: String, appName: String?): List<Session> = withContext(Dispatchers.IO) {
        val entities = if (appName != null) dao.listSessionsByUserAndApp(userId, appName)
        else dao.listSessionsByUser(userId)
        entities.map { entity ->
            val events = dao.getEvents(entity.id).map { it.toAgentEvent() }
            entity.toSession(events)
        }
    }

    override suspend fun appendEvent(sessionId: String, event: AgentEvent) = withContext(Dispatchers.IO) {
        dao.insertEvent(EventEntity(
            sessionId = sessionId,
            eventType = event::class.simpleName ?: "Unknown",
            eventJson = eventToJson(event),
            timestamp = System.currentTimeMillis(),
        ))
    }

    override suspend fun updateState(sessionId: String, state: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val entity = dao.getSession(sessionId, "") ?: return@withContext
        val updated = entity.copy(
            stateJson = json.encodeToString(state.mapValues { it.value?.toString() }),
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsertSession(updated)
    }

    override suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        dao.deleteSession(sessionId)
    }

    private fun eventToJson(event: AgentEvent): String {
        return when (event) {
            is AgentEvent.RunStarted -> """{"type":"RunStarted","inputTokenCount":${event.inputTokenCount}}"""
            is AgentEvent.TextDelta -> """{"type":"TextDelta","text":"${escapeJson(event.text)}","isFinal":${event.isFinal}}"""
            is AgentEvent.ToolCallRequest -> """{"type":"ToolCallRequest","callId":"${event.callId}","toolName":"${event.toolName}"}"""
            is AgentEvent.ToolCallResult -> """{"type":"ToolCallResult","callId":"${event.callId}","toolName":"${event.toolName}","result":${event.result}}"""
            is AgentEvent.FinalResponse -> """{"type":"FinalResponse","text":"${escapeJson(event.text)}"}"""
            is AgentEvent.RunError -> """{"type":"RunError","message":"${escapeJson(event.error.message ?: "")}"}"""
            is AgentEvent.RunCompleted -> """{"type":"RunCompleted"}"""
        }
    }

    private fun EventEntity.toAgentEvent(): AgentEvent {
        return when (eventType) {
            "RunStarted" -> AgentEvent.RunStarted(0)
            "TextDelta" -> AgentEvent.TextDelta("", isFinal = false)
            "ToolCallRequest" -> AgentEvent.ToolCallRequest("", "", emptyMap())
            "ToolCallResult" -> AgentEvent.ToolCallResult("", "", null)
            "FinalResponse" -> AgentEvent.FinalResponse("", TokenUsage(0, 0))
            "RunCompleted" -> AgentEvent.RunCompleted
            else -> AgentEvent.RunError(RuntimeException("Unknown event type: $eventType"), com.google.adk.event.ErrorPhase.MODEL_CALL)
        }
    }

    private fun SessionEntity.toSession(events: List<AgentEvent>): Session = Session(
        id = id,
        userId = userId,
        appName = appName,
        agentName = agentName,
        state = emptyMap(),
        events = events,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

    private fun escapeJson(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}