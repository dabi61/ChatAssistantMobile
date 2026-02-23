package com.chatassistantmobile.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.chatassistantmobile.data.local.CurrentScreenCaptureStore
import com.chatassistantmobile.data.local.PrivacyConsentStore

class ChatAccessibilityService : AccessibilityService() {
    private data class CapturedNodeText(
        val text: String,
        val isClickable: Boolean,
        val className: String,
        val centerX: Int
    )

    companion object {
        @Volatile
        private var instance: ChatAccessibilityService? = null

        fun requestImmediateCapture(): Boolean {
            return instance?.captureNowFromActiveWindow().orFalse()
        }

        private fun Boolean?.orFalse(): Boolean = this ?: false
    }

    private val captureStore: CurrentScreenCaptureStore by lazy {
        CurrentScreenCaptureStore(applicationContext)
    }
    private val privacyConsentStore: PrivacyConsentStore by lazy {
        PrivacyConsentStore(applicationContext)
    }
    private var lastCaptureEpochMillis: Long = 0L
    private val ignoredUiLabels = setOf(
        "back", "home", "recent", "search", "menu", "more", "more options",
        "send", "reply", "react", "call", "video call", "camera", "gallery",
        "emoji", "sticker", "gif", "voice", "microphone", "record",
        "type a message", "message", "new message", "new chat",
        "đóng", "quay lại", "gửi", "menu", "thêm", "gọi", "video",
        "camera", "thư viện", "nhãn dán", "tin nhắn", "nhập tin nhắn"
    )
    private val ignoredMetaLabels = setOf(
        "today", "yesterday", "seen", "delivered", "online", "active now",
        "typing", "typing...", "write a message", "message unsent",
        "hôm nay", "hôm qua", "đã xem", "đã gửi", "trực tuyến", "đang hoạt động",
        "đang nhập", "đang nhập...", "tin nhắn đã thu hồi"
    )
    private val timeLikeRegex = Regex("^\\d{1,2}:\\d{2}(\\s?(am|pm))?$", RegexOption.IGNORE_CASE)
    private val dateLikeRegex = Regex("^\\d{1,2}[./-]\\d{1,2}([./-]\\d{2,4})?$")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!canCapture()) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastCaptureEpochMillis < 1200) return

        if (saveActiveWindowContent(event.packageName?.toString().orEmpty())) {
            lastCaptureEpochMillis = now
        }
    }

    override fun onInterrupt() {
        // No-op for MVP.
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    private fun canCapture(): Boolean {
        return privacyConsentStore.isPrivacyAccepted() &&
            privacyConsentStore.isAccessibilityCaptureEnabled()
    }

    private fun captureNowFromActiveWindow(): Boolean {
        if (!canCapture()) return false
        return saveActiveWindowContent()
    }

    @Suppress("DEPRECATION")
    private fun saveActiveWindowContent(packageNameHint: String = ""): Boolean {
        val root = rootInActiveWindow ?: return false
        val packageName = root.packageName?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: packageNameHint
        val screenWidth = root.boundsWidth()
            .takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val horizontalCenter = screenWidth / 2f
        val deadZone = (screenWidth * 0.08f).coerceAtLeast(28f)
        val rawNodes = mutableListOf<CapturedNodeText>()
        try {
            collectVisibleText(root, rawNodes)
        } finally {
            root.recycle()
        }

        if (rawNodes.isEmpty()) return false

        val rawLines = rawNodes
            .mapNotNull { entry ->
                val text = entry.text.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val side = resolveSpeakerByPosition(entry.centerX, horizontalCenter, deadZone)
                "[$side] $text"
            }
            .filter { it.isNotBlank() }
            .takeLast(220)
        val filteredLines = rawNodes
            .mapNotNull { entry ->
                val message = entry.text
                    .trim()
                    .takeIf { it.length in 2..260 && isLikelyConversationText(it, entry) }
                    ?: return@mapNotNull null
                val speaker = resolveSpeakerByPosition(entry.centerX, horizontalCenter, deadZone)
                "$speaker: $message"
            }
            .distinct()
            .takeLast(80)

        captureStore.save(
            packageName = packageName,
            rawLines = rawLines,
            filteredLines = filteredLines
        )
        return true
    }

    @Suppress("DEPRECATION")
    private fun collectVisibleText(node: AccessibilityNodeInfo?, output: MutableList<CapturedNodeText>) {
        if (node == null) return

        val nodeBounds = Rect().also { node.getBoundsInScreen(it) }
        node.text?.toString()
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 300 }
            ?.let { text ->
                output += CapturedNodeText(
                    text = text,
                    isClickable = node.isClickable,
                    className = node.className?.toString().orEmpty(),
                    centerX = nodeBounds.centerX()
                )
            }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index)
            try {
                collectVisibleText(child, output)
            } finally {
                child?.recycle()
            }
        }
    }

    private fun isLikelyConversationText(text: String, entry: CapturedNodeText): Boolean {
        val normalized = text
            .lowercase()
            .replace("\\s+".toRegex(), " ")
            .trim()
        if (normalized.isBlank()) return false
        if (normalized in ignoredUiLabels || normalized in ignoredMetaLabels) return false

        // Most icon/button labels are short and clickable; skip these to reduce UI chrome noise.
        if (entry.isClickable && normalized.length <= 24) return false

        // EditText often contains composer content or hints rather than received messages.
        if (entry.className.contains("EditText", ignoreCase = true)) return false

        // Ignore URL-like/system labels that often come from browser/chat app chrome.
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) return false

        // Filter time/day separators that add noise to model input.
        if (timeLikeRegex.matches(normalized) || dateLikeRegex.matches(normalized)) return false

        // Ignore tiny symbolic counters/badges.
        if (normalized.length <= 4 && normalized.all { it.isDigit() || !it.isLetterOrDigit() }) {
            return false
        }

        return true
    }

    private fun resolveSpeakerByPosition(
        centerX: Int,
        horizontalCenter: Float,
        deadZone: Float
    ): String {
        return when {
            centerX < (horizontalCenter - deadZone) -> "other"
            centerX > (horizontalCenter + deadZone) -> "me"
            else -> "other"
        }
    }

    private fun AccessibilityNodeInfo.boundsWidth(): Int {
        val rect = Rect()
        getBoundsInScreen(rect)
        return rect.width()
    }
}
