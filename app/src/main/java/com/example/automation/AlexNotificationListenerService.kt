package com.example.automation

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.domain.model.SocialNotificationEvent
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationAutomationBus {

    private val _events = MutableStateFlow<List<SocialNotificationEvent>>(emptyList())
    val events: StateFlow<List<SocialNotificationEvent>> = _events.asStateFlow()

    var onSpeakAnnouncementRequested: ((String) -> Unit)? = null
    var whatsappVoiceEnabled: Boolean = true
    var announceIncomingEnabled: Boolean = true
    var readMessageContentAloud: Boolean = false
    var userTitle: String = "Boss"

    fun isNotificationAccessGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val cn = ComponentName(context, AlexNotificationListenerService::class.java).flattenToString()
        return flat.contains(cn) || flat.contains(context.packageName)
    }

    fun openNotificationAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun notifyWhatsAppMessageSent(recipientName: String, messageSnippet: String = "") {
        val cleanRecipient = recipientName.trim()
        val announcement = if (cleanRecipient.isNotEmpty() && !cleanRecipient.equals("Unknown", ignoreCase = true)) {
            "$userTitle, your WhatsApp message to $cleanRecipient has been sent successfully."
        } else {
            "$userTitle, your WhatsApp message has been sent successfully."
        }

        val event = SocialNotificationEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            packageName = "com.whatsapp",
            appDisplayName = "WhatsApp",
            senderOrRecipient = cleanRecipient.ifEmpty { "Recipient" },
            messageContent = messageSnippet.ifBlank { "WhatsApp message sent" },
            isSentConfirmation = true,
            announcedByAlex = whatsappVoiceEnabled
        )
        _events.value = listOf(event) + _events.value.take(39)

        if (whatsappVoiceEnabled) {
            onSpeakAnnouncementRequested?.invoke(announcement)
        }
    }

    fun recordIncomingOrSentNotification(
        packageName: String,
        title: String,
        text: String,
        isSentConfirmation: Boolean
    ) {
        val appName = resolveFriendlyAppName(packageName)
        val cleanSender = title.trim().ifEmpty { appName }
        val cleanText = text.trim()

        val shouldAnnounce = when {
            isSentConfirmation && packageName.contains("whatsapp", ignoreCase = true) -> whatsappVoiceEnabled
            else -> announceIncomingEnabled
        }

        val spokenText = when {
            isSentConfirmation && packageName.contains("whatsapp", ignoreCase = true) -> {
                if (cleanSender.isNotEmpty() && !cleanSender.equals("WhatsApp", ignoreCase = true)) {
                    "$userTitle, your WhatsApp message to $cleanSender has been sent successfully."
                } else {
                    "$userTitle, your WhatsApp message has been sent successfully."
                }
            }
            readMessageContentAloud && cleanText.isNotEmpty() -> {
                "$userTitle, new $appName message from $cleanSender: $cleanText"
            }
            else -> {
                "$userTitle, you have a new $appName message from $cleanSender."
            }
        }

        val event = SocialNotificationEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            packageName = packageName,
            appDisplayName = appName,
            senderOrRecipient = cleanSender,
            messageContent = cleanText,
            isSentConfirmation = isSentConfirmation,
            announcedByAlex = shouldAnnounce
        )
        _events.value = listOf(event) + _events.value.take(39)

        if (shouldAnnounce) {
            onSpeakAnnouncementRequested?.invoke(spokenText)
        }
    }

    fun resolveFriendlyAppName(packageName: String): String {
        val lower = packageName.lowercase()
        return when {
            lower.contains("whatsapp") -> "WhatsApp"
            lower.contains("facebook.orca") || lower.contains("messenger") -> "Facebook Messenger"
            lower.contains("facebook.katana") -> "Facebook"
            lower.contains("instagram") -> "Instagram"
            lower.contains("telegram") -> "Telegram"
            else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}

class AlexNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName.orEmpty()
        if (pkg.isBlank() || pkg == applicationContext.packageName) return

        val isSocialPackage = pkg.contains("whatsapp", ignoreCase = true) ||
            pkg.contains("facebook", ignoreCase = true) ||
            pkg.contains("instagram", ignoreCase = true) ||
            pkg.contains("telegram", ignoreCase = true) ||
            pkg.contains("mms", ignoreCase = true) ||
            pkg.contains("messaging", ignoreCase = true)

        if (!isSocialPackage) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""

        if (title.isBlank() && text.isBlank()) return

        // Ignore ongoing background summary headers
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val isSentConfirmation = text.contains("sent", ignoreCase = true) ||
            text.contains("পাঠানো হয়েছে") ||
            title.startsWith("You", ignoreCase = true)

        NotificationAutomationBus.recordIncomingOrSentNotification(
            packageName = pkg,
            title = title,
            text = text,
            isSentConfirmation = isSentConfirmation
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
