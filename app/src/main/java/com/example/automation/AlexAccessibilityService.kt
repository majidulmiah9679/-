package com.example.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScreenActionType {
    CLICK_TEXT,
    SCROLL_DOWN,
    SCROLL_UP,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    PRESS_BACK,
    GO_HOME,
    SWITCH_APPS,
    TYPE_TEXT,
    COPY_TEXT,
    PASTE_TEXT
}

data class ScreenAutomationStep(
    val actionType: ScreenActionType,
    val targetTextOrHint: String = "",
    val inputValue: String = ""
)

class AlexAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        if (pkg.isNotBlank()) {
            _foregroundPackage.value = pkg
        }
        processPendingAutomationQueue()
    }

    override fun onInterrupt() {
        // Required override for AccessibilityService
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance === this) {
            activeInstance = null
        }
        _isConnected.value = false
    }

    fun executeStep(step: ScreenAutomationStep): Boolean {
        return when (step.actionType) {
            ScreenActionType.PRESS_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            ScreenActionType.GO_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            ScreenActionType.SWITCH_APPS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            ScreenActionType.SCROLL_DOWN -> performScroll(forward = true)
            ScreenActionType.SCROLL_UP -> performScroll(forward = false)
            ScreenActionType.SWIPE_LEFT -> performSwipeGesture(startX = 900f, startY = 1000f, endX = 180f, endY = 1000f)
            ScreenActionType.SWIPE_RIGHT -> performSwipeGesture(startX = 180f, startY = 1000f, endX = 900f, endY = 1000f)
            ScreenActionType.CLICK_TEXT -> clickNodeByText(step.targetTextOrHint)
            ScreenActionType.TYPE_TEXT -> typeTextIntoFocusedOrMatchingField(step.targetTextOrHint, step.inputValue)
            ScreenActionType.COPY_TEXT -> copyTextToClipboard(step.inputValue)
            ScreenActionType.PASTE_TEXT -> pasteIntoFocusedField()
        }
    }

    private fun clickNodeByText(label: String): Boolean {
        if (label.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(label)
        if (!matches.isNullOrEmpty()) {
            for (node in matches) {
                if (clickNodeOrParent(node)) return true
            }
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return false
    }

    private fun typeTextIntoFocusedOrMatchingField(hintOrTarget: String, textToType: String): Boolean {
        if (textToType.isEmpty()) return false
        val root = rootInActiveWindow ?: return false
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                textToType
            )
        }

        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        if (hintOrTarget.isNotBlank()) {
            val nodes = root.findAccessibilityNodeInfosByText(hintOrTarget)
            val editable = nodes?.firstOrNull { it.isEditable }
            if (editable != null) {
                editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
        }

        val firstEditable = findFirstEditableNode(root)
        if (firstEditable != null) {
            firstEditable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            return firstEditable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return false
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findFirstEditableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun performScroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow
        val scrollable = findFirstScrollableNode(root)
        if (scrollable != null) {
            val action = if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            if (scrollable.performAction(action)) return true
        }
        // Fallback to gesture swipe
        return if (forward) {
            performSwipeGesture(540f, 1500f, 540f, 450f)
        } else {
            performSwipeGesture(540f, 450f, 540f, 1500f)
        }
    }

    private fun findFirstScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findFirstScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun performSwipeGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): Boolean {
        return try {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 280L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (_: Exception) {
            false
        }
    }

    private fun copyTextToClipboard(text: String): Boolean {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        cm.setPrimaryClip(ClipData.newPlainText("ALEX_AI_CLIP", text))
        return true
    }

    private fun pasteIntoFocusedField(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findFirstEditableNode(root)
        return focused?.performAction(AccessibilityNodeInfo.ACTION_PASTE) ?: false
    }

    private fun processPendingAutomationQueue() {
        val nextStep = synchronized(pendingStepsQueue) {
            if (pendingStepsQueue.isNotEmpty()) pendingStepsQueue.removeAt(0) else null
        } ?: return
        executeStep(nextStep)
    }

    companion object {
        @Volatile
        var activeInstance: AlexAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _foregroundPackage = MutableStateFlow("")
        val foregroundPackage: StateFlow<String> = _foregroundPackage.asStateFlow()

        private val pendingStepsQueue = mutableListOf<ScreenAutomationStep>()

        fun isAccessibilityPermissionEnabled(context: Context): Boolean {
            if (_isConnected.value && activeInstance != null) return true
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val expectedComponent = ComponentName(context, AlexAccessibilityService::class.java).flattenToString()
            return enabledServices.contains(expectedComponent) || enabledServices.contains(context.packageName)
        }

        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
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

        fun enqueueSteps(steps: List<ScreenAutomationStep>): Boolean {
            val service = activeInstance
            if (service != null) {
                var anyExecuted = false
                for (step in steps) {
                    if (service.executeStep(step)) {
                        anyExecuted = true
                    }
                }
                return anyExecuted
            } else {
                synchronized(pendingStepsQueue) {
                    pendingStepsQueue.addAll(steps)
                }
                return false
            }
        }
    }
}
