package com.example.data.repository

import com.example.data.local.ConversationDao
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.domain.model.AlexLanguage
import com.example.domain.model.ConversationModality
import com.example.domain.model.ConversationSummary
import com.example.domain.model.MessageSender
import com.example.domain.model.TranscriptTurn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConversationRepository(
    private val conversationDao: ConversationDao
) {
    val allConversations: Flow<List<ConversationSummary>> =
        conversationDao.getAllConversations().map { list ->
            list.map { entity -> entity.toDomain() }
        }

    fun getTranscriptTurns(conversationId: Long): Flow<List<TranscriptTurn>> {
        return conversationDao.getMessagesForConversation(conversationId).map { list ->
            list.map { msg -> msg.toDomain() }
        }
    }

    suspend fun getTranscriptSnapshot(conversationId: Long): List<TranscriptTurn> {
        return conversationDao.getMessagesSnapshot(conversationId).map { it.toDomain() }
    }

    suspend fun createConversation(
        title: String,
        modality: ConversationModality,
        language: AlexLanguage
    ): Long {
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            title = title.take(64).ifBlank { "ALEX Session" },
            previewText = "Session initialized",
            modality = modality.name,
            primaryLanguage = language.code,
            messageCount = 0,
            createdAt = now,
            updatedAt = now
        )
        return conversationDao.insertConversation(entity)
    }

    suspend fun appendTurn(
        conversationId: Long,
        sender: MessageSender,
        text: String,
        language: AlexLanguage,
        wasInterrupted: Boolean = false
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        conversationDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                sender = sender.name,
                text = trimmed,
                languageCode = language.code,
                timestamp = now,
                wasInterrupted = wasInterrupted
            )
        )
        conversationDao.updateConversationSummary(
            conversationId = conversationId,
            previewText = trimmed.take(120),
            languageCode = language.code,
            updatedAt = now
        )
    }

    suspend fun deleteConversation(conversationId: Long) {
        conversationDao.deleteConversationById(conversationId)
    }

    suspend fun clearAllHistory() {
        conversationDao.clearAllConversations()
    }

    private fun ConversationEntity.toDomain(): ConversationSummary = ConversationSummary(
        id = id,
        title = title,
        previewText = previewText,
        modality = runCatching { ConversationModality.valueOf(modality) }
            .getOrDefault(ConversationModality.LIVE_VOICE),
        primaryLanguage = AlexLanguage.fromCode(primaryLanguage),
        messageCount = messageCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MessageEntity.toDomain(): TranscriptTurn = TranscriptTurn(
        id = id.toString(),
        sender = runCatching { MessageSender.valueOf(sender) }.getOrDefault(MessageSender.ALEX),
        text = text,
        language = AlexLanguage.fromCode(languageCode),
        timestamp = timestamp,
        isStreaming = false,
        wasInterrupted = wasInterrupted
    )
}
