package com.google.adk.session.sqlite

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :sessionId AND user_id = :userId")
    suspend fun getSession(sessionId: String, userId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE user_id = :userId")
    suspend fun listSessionsByUser(userId: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE user_id = :userId AND app_name = :appName")
    suspend fun listSessionsByUserAndApp(userId: String, appName: String): List<SessionEntity>

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Query("SELECT * FROM events WHERE session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getEvents(sessionId: String): List<EventEntity>
}