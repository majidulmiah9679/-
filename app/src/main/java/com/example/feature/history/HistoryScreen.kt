package com.example.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlexSettings
import com.example.domain.model.ConversationModality
import com.example.domain.model.ConversationSummary
import com.example.domain.model.TranscriptTurn
import com.example.feature.components.LanguageChipBadge
import com.example.feature.components.TranscriptTurnBubble
import com.example.ui.theme.AlexBlack
import com.example.ui.theme.AlexCardBorder
import com.example.ui.theme.AlexCardNavy
import com.example.ui.theme.AlexCyan
import com.example.ui.theme.AlexDeepNavy
import com.example.ui.theme.AlexElectricBlue
import com.example.ui.theme.AlexStatusDisconnected
import com.example.ui.theme.AlexSurfaceNavy
import com.example.ui.theme.AlexTextMuted
import com.example.ui.theme.AlexTextPrimary
import com.example.ui.theme.AlexTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    conversations: List<ConversationSummary>,
    selectedConversationTurns: List<TranscriptTurn>,
    settings: AlexSettings,
    onSelectConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onClearAllHistory: () -> Unit,
    onToggleSaveHistory: (Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    var filterModality by remember { mutableStateOf<ConversationModality?>(null) }
    var inspectedConversation by remember { mutableStateOf<ConversationSummary?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filteredConversations = remember(conversations, filterModality) {
        if (filterModality == null) {
            conversations
        } else {
            conversations.filter { it.modality == filterModality }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = AlexCardNavy,
            titleContentColor = AlexStatusDisconnected,
            textContentColor = AlexTextPrimary,
            title = { Text("Clear All History?") },
            text = {
                Text("This permanently deletes all saved text transcripts from your local Room database. Raw audio is never stored.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearAllHistory()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlexStatusDisconnected,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_clear_history_button")
                ) {
                    Text("DELETE ALL")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("CANCEL", color = AlexTextSecondary)
                }
            }
        )
    }

    if (inspectedConversation != null) {
        AlertDialog(
            onDismissRequest = { inspectedConversation = null },
            containerColor = AlexDeepNavy,
            titleContentColor = AlexCyan,
            textContentColor = AlexTextPrimary,
            title = {
                Text(
                    text = inspectedConversation?.title ?: "Transcript",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Box(modifier = Modifier.height(340.dp)) {
                    if (selectedConversationTurns.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No turns recorded in this session.", color = AlexTextSecondary)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedConversationTurns, key = { it.id }) { turn ->
                                TranscriptTurnBubble(
                                    turn = turn,
                                    userTitle = settings.userTitle
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedConversation = null }) {
                    Text("CLOSE", color = AlexCyan)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AlexBlack, AlexDeepNavy, AlexBlack)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("history_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("history_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AlexCyan
                        )
                    }
                    Column {
                        Text(
                            text = "CONVERSATION ARCHIVE",
                            style = MaterialTheme.typography.headlineMedium,
                            color = AlexCyan
                        )
                        Text(
                            text = if (settings.saveHistoryEnabled) {
                                "LOCAL ROOM DB • TEXT ONLY (NO RAW AUDIO SAVED)"
                            } else {
                                "INCOGNITO MODE • HISTORY SAVING PAUSED"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = AlexTextSecondary
                        )
                    }
                }

                if (conversations.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All History",
                            tint = AlexStatusDisconnected
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips + Privacy Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = filterModality == null,
                    onClick = { filterModality = null },
                    label = { Text("ALL (${conversations.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AlexCyan,
                        selectedLabelColor = AlexBlack
                    )
                )
                FilterChip(
                    selected = filterModality == ConversationModality.LIVE_VOICE,
                    onClick = { filterModality = ConversationModality.LIVE_VOICE },
                    label = { Text("LIVE CALLS") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AlexCyan,
                        selectedLabelColor = AlexBlack
                    )
                )
                FilterChip(
                    selected = filterModality == ConversationModality.TEXT_CHAT,
                    onClick = { filterModality = ConversationModality.TEXT_CHAT },
                    label = { Text("TEXT CHATS") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AlexCyan,
                        selectedLabelColor = AlexBlack
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredConversations.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    color = AlexSurfaceNavy.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, AlexCardBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "No conversations",
                                tint = AlexCyan.copy(alpha = 0.5f),
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = "No Saved Conversations Yet",
                                style = MaterialTheme.typography.titleLarge,
                                color = AlexTextPrimary
                            )
                            Text(
                                text = "Your Live Voice and Text Chat transcripts with ALEX will appear here when History Saving is enabled.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AlexTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredConversations, key = { it.id }) { summary ->
                        ConversationHistoryCard(
                            summary = summary,
                            onClick = {
                                onSelectConversation(summary.id)
                                inspectedConversation = summary
                            },
                            onDelete = { onDeleteConversation(summary.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationHistoryCard(
    summary: ConversationSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(summary.updatedAt) {
        dateFormat.format(Date(summary.updatedAt))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("history_item_${summary.id}"),
        shape = RoundedCornerShape(16.dp),
        color = AlexCardNavy.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, AlexCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = AlexSurfaceNavy,
                border = BorderStroke(1.dp, AlexCyan.copy(alpha = 0.4f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (summary.modality == ConversationModality.LIVE_VOICE) {
                            Icons.Default.GraphicEq
                        } else {
                            Icons.AutoMirrored.Filled.Chat
                        },
                        contentDescription = summary.modality.name,
                        tint = if (summary.modality == ConversationModality.LIVE_VOICE) AlexCyan else AlexElectricBlue
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = AlexTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    LanguageChipBadge(language = summary.primaryLanguage)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary.previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlexTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$formattedDate • ${summary.messageCount} turns",
                    style = MaterialTheme.typography.labelMedium,
                    color = AlexTextMuted
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_conversation_${summary.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete conversation",
                    tint = AlexStatusDisconnected
                )
            }
        }
    }
}
