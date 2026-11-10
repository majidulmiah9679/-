package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val previewText: String,
    val modality: String, // "LIVE_VOICE" or "TEXT_CHAT"
    val primaryLanguage: String, // "BN", "EN", "HI"
    val messageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val sender: String, // "USER", "ALEX", "SYSTEM"
    val text: String,
    val languageCode: String, // "BN", "EN", "HI"
    val timestamp: Long = System.currentTimeMillis(),
    val wasInterrupted: Boolean = false
)
