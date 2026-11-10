package com.example.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlexSettings
import com.example.domain.model.TranscriptTurn
import com.example.feature.ChatUiState
import com.example.feature.components.TranscriptTurnBubble
import com.example.ui.theme.AlexBlack
import com.example.ui.theme.AlexCardBorder
import com.example.ui.theme.AlexCardNavy
import com.example.ui.theme.AlexCyan
import com.example.ui.theme.AlexDeepNavy
import com.example.ui.theme.AlexStatusDisconnected
import com.example.ui.theme.AlexSurfaceNavy
import com.example.ui.theme.AlexTextMuted
import com.example.ui.theme.AlexTextPrimary
import com.example.ui.theme.AlexTextSecondary

@Composable
fun ChatScreen(
    chatTurns: List<TranscriptTurn>,
    chatUiState: ChatUiState,
    isTtsSpeaking: Boolean,
    settings: AlexSettings,
    onSendMessage: (String) -> Unit,
    onStartNewChat: () -> Unit,
    onStopSpeaking: () -> Unit,
    onToggleSpeakReplies: (Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatTurns.size, chatTurns.lastOrNull()?.text) {
        if (chatTurns.isNotEmpty()) {
            listState.animateScrollToItem(chatTurns.lastIndex)
        }
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
            .imePadding()
            .testTag("chat_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 10.dp)
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
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AlexCyan
                        )
                    }
                    Column {
                        Text(
                            text = "ALEX NEURAL CHAT",
                            style = MaterialTheme.typography.headlineMedium,
                            color = AlexCyan
                        )
                        Text(
                            text = "MODEL: ${settings.chatModelName} • বাংলা / EN / हिं",
                            style = MaterialTheme.typography.labelMedium,
                            color = AlexTextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isTtsSpeaking) {
                        IconButton(
                            onClick = onStopSpeaking,
                            modifier = Modifier.testTag("stop_tts_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.StopCircle,
                                contentDescription = "Stop ALEX voice readout",
                                tint = AlexStatusDisconnected
                            )
                        }
                    }
                    IconButton(
                        onClick = { onToggleSpeakReplies(!settings.speakChatReplies) },
                        modifier = Modifier.testTag("toggle_speak_replies_button")
                    ) {
                        Icon(
                            imageVector = if (settings.speakChatReplies) {
                                Icons.AutoMirrored.Filled.VolumeUp
                            } else {
                                Icons.AutoMirrored.Filled.VolumeOff
                            },
                            contentDescription = "Toggle Female Voice Readout",
                            tint = if (settings.speakChatReplies) AlexCyan else AlexTextMuted
                        )
                    }
                    IconButton(
                        onClick = onStartNewChat,
                        modifier = Modifier.testTag("new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddComment,
                            contentDescription = "Start New Chat Session",
                            tint = AlexTextPrimary
                        )
                    }
                }
            }

            if (chatUiState is ChatUiState.Thinking || chatUiState is ChatUiState.Streaming) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AlexCyan,
                    trackColor = AlexCardNavy
                )
            }

            if (chatUiState is ChatUiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_error_banner"),
                    shape = RoundedCornerShape(12.dp),
                    color = AlexStatusDisconnected.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, AlexStatusDisconnected)
                ) {
                    Text(
                        text = chatUiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlexTextPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Multilingual Conversation Starters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val examples = listOf(
                    "হ্যালো অ্যালেক্স!",
                    "What are you doing?",
                    "আজ আমার মন ভালো নেই।",
                    "नमस्ते एलेक्स, मुझे एक मजेदार बात बताओ।"
                )
                examples.forEachIndexed { index, sample ->
                    Surface(
                        modifier = Modifier
                            .clickable { onSendMessage(sample) }
                            .testTag("chat_sample_chip_$index"),
                        shape = RoundedCornerShape(50),
                        color = AlexCardNavy,
                        border = BorderStroke(1.dp, AlexCardBorder)
                    ) {
                        Text(
                            text = sample,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlexCyan,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chat Messages List
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = AlexSurfaceNavy.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, AlexCardBorder)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chatTurns, key = { it.id }) { turn ->
                        TranscriptTurnBubble(
                            turn = turn,
                            userTitle = settings.userTitle
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input"),
                    placeholder = {
                        Text(
                            text = "Ask ALEX in বাংলা, English, or हिन्दी...",
                            color = AlexTextMuted
                        )
                    },
                    singleLine = false,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AlexCyan,
                        unfocusedBorderColor = AlexCardBorder,
                        focusedTextColor = AlexTextPrimary,
                        unfocusedTextColor = AlexTextPrimary,
                        focusedContainerColor = AlexCardNavy,
                        unfocusedContainerColor = AlexCardNavy
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText)
                                inputText = ""
                            }
                        }
                    )
                )

                Surface(
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("chat_send_button"),
                    shape = RoundedCornerShape(16.dp),
                    color = if (inputText.isNotBlank()) AlexCyan else AlexCardNavy,
                    border = BorderStroke(1.dp, AlexCardBorder)
                ) {
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = if (inputText.isNotBlank()) AlexBlack else AlexTextMuted
                        )
                    }
                }
            }
        }
    }
}
