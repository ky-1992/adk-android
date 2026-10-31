package com.google.adk.session.sqlite

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("session_id")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "event_json") val eventJson: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
)