package com.chatassistantmobile.service.overlay

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button as ComposeButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text as ComposeText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.chatassistantmobile.ChatAssistantApplication
import com.chatassistantmobile.MainActivity
import com.chatassistantmobile.R
import com.chatassistantmobile.data.local.AnalysisHistoryStore
import com.chatassistantmobile.data.local.CurrentScreenCaptureStore
import com.chatassistantmobile.data.local.FloatingBubbleStateStore
import com.chatassistantmobile.data.local.FloatingRuleStore
import com.chatassistantmobile.data.local.LatestAnalysisWidgetStore
import com.chatassistantmobile.data.local.NotificationDraftStore
import com.chatassistantmobile.data.local.PrivacyConsentStore
import com.chatassistantmobile.data.model.ChatMessage
import com.chatassistantmobile.domain.model.AnalysisHistoryItem
import com.chatassistantmobile.domain.model.AnalysisSuggestion
import com.chatassistantmobile.domain.usecase.ConversationParser
import com.chatassistantmobile.service.accessibility.ChatAccessibilityService
import com.chatassistantmobile.ui.widget.glance.WidgetSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class FloatingBubbleService : Service() {
    companion object {
        private const val ACTION_START = "floating_bubble_start"
        private const val ACTION_STOP = "floating_bubble_stop"

        private const val CHANNEL_ID = "floating_bubble_channel"
        private const val NOTIFICATION_ID = 7001

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
                .setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun isRunning(context: Context): Boolean {
            return FloatingBubbleStateStore(context).isRunning()
        }

        fun hasOverlayPermission(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        fun openOverlayPermissionSettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private enum class BubbleMode(val label: String) {
        SetRule("Set Rule"),
        Analyze("Analyze"),
        Options("Options")
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var resultOverlayView: View? = null
    private var overlayRecomposer: Recomposer? = null
    private var overlayRecomposerJob: Job? = null
    private lateinit var overlayComposeOwner: OverlayComposeOwner

    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var panelParams: WindowManager.LayoutParams? = null

    private var modeTextView: TextView? = null
    private var roleTextView: TextView? = null
    private var statusTextView: TextView? = null
    private var resultSummaryTextView: TextView? = null
    private var resultReplyTextView: TextView? = null
    private var optionsLayout: LinearLayout? = null
    private var isAnalyzingCurrentScreen: Boolean = false
    private var latestSummary: String = "-"
    private var latestReply: String = "-"

    private var currentMode: BubbleMode = BubbleMode.Analyze

    private val appContainer by lazy {
        (application as ChatAssistantApplication).appContainer
    }

    private val chatRepository by lazy { appContainer.chatRepository }
    private val ruleStore by lazy { FloatingRuleStore(applicationContext) }
    private val bubbleStateStore by lazy { FloatingBubbleStateStore(applicationContext) }
    private val privacyConsentStore by lazy { PrivacyConsentStore(applicationContext) }
    private val screenCaptureStore by lazy { CurrentScreenCaptureStore(applicationContext) }
    private val notificationDraftStore by lazy { NotificationDraftStore(applicationContext) }
    private val analysisHistoryStore by lazy { AnalysisHistoryStore(applicationContext) }
    private val latestAnalysisWidgetStore by lazy { LatestAnalysisWidgetStore(applicationContext) }
    private val widgetSyncManager by lazy { WidgetSyncManager(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayComposeOwner = OverlayComposeOwner()
        overlayComposeOwner.markActive()
        overlayRecomposer = Recomposer(AndroidUiDispatcher.Main)
        overlayRecomposerJob = serviceScope.launch(AndroidUiDispatcher.Main) {
            overlayRecomposer?.runRecomposeAndApplyChanges()
        }
        latestAnalysisWidgetStore.read()?.let { latest ->
            latestSummary = latest.summary
            latestReply = latest.sampleReply.ifBlank { "-" }
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        bubbleStateStore.setRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                if (!hasOverlayPermission(this)) {
                    showToast("Grant overlay permission to use floating bubble.")
                    openOverlayPermissionSettings(this)
                    stopSelf()
                    return START_NOT_STICKY
                }

                ensureBubbleVisible()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        removeResultOverlay()
        overlayComposeOwner.markDestroyed()
        overlayRecomposer?.cancel()
        overlayRecomposerJob?.cancel()
        overlayRecomposer = null
        overlayRecomposerJob = null
        removePanel()
        removeBubble()
        bubbleStateStore.setRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureBubbleVisible() {
        if (bubbleView != null) return

        val size = 58.dp()
        val container = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1B3A8A"))
            }
        }

        val label = TextView(this).apply {
            text = "AI"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
        }

        container.addView(
            label,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        bubbleParams = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 18.dp()
            y = 220.dp()
        }

        container.setOnTouchListener(createBubbleTouchListener())

        windowManager.addView(container, bubbleParams)
        bubbleView = container
    }

    private fun createBubbleTouchListener(): View.OnTouchListener {
        var startX = 0
        var startY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = bubbleParams.x
                    startY = bubbleParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    bubbleParams.x = startX + (event.rawX - touchStartX).toInt()
                    bubbleParams.y = startY + (event.rawY - touchStartY).toInt()
                    bubbleView?.let { windowManager.updateViewLayout(it, bubbleParams) }
                    updatePanelPosition()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val deltaX = kotlin.math.abs(event.rawX - touchStartX)
                    val deltaY = kotlin.math.abs(event.rawY - touchStartY)
                    if (deltaX < 12f && deltaY < 12f) {
                        togglePanel()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun togglePanel() {
        if (panelView == null) {
            showPanel()
        } else {
            removePanel()
        }
    }

    private fun showPanel() {
        if (panelView != null) return

        val panel = createPanelView()
        val width = 280.dp()
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        panelView = panel
        panelParams = params
        windowManager.addView(panel, params)
        updatePanelPosition()
        refreshPanelValues()
    }

    private fun createPanelView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18.dp().toFloat()
                setColor(Color.parseColor("#EDF3FF"))
                setStroke(1.dp(), Color.parseColor("#B7C7E6"))
            }
        }

        root.addView(createTitleText("Floating Assistant"))

        modeTextView = createBodyText("Mode: ${currentMode.label}")
        root.addView(modeTextView)

        roleTextView = createBodyText("")
        root.addView(roleTextView)

        statusTextView = createBodyText("Status: Ready")
        root.addView(statusTextView)

        root.addView(createMainButton("Set Rule") {
            currentMode = BubbleMode.SetRule
            updateModeLabel()
            showRuleDialog()
        })

        root.addView(createMainButton("Analyze Screen") {
            currentMode = BubbleMode.Analyze
            updateModeLabel()
            analyzeCurrentScreen()
        })

        root.addView(createSectionText("Latest analysis"))

        resultSummaryTextView = createResultBodyText("Summary\n$latestSummary")
        root.addView(resultSummaryTextView)

        resultReplyTextView = createResultBodyText("Reply\n$latestReply")
        root.addView(resultReplyTextView)

        root.addView(createMainButton("Copy Reply") {
            copyLatestReply()
        })

        root.addView(createMainButton("Options") {
            currentMode = BubbleMode.Options
            updateModeLabel()
            optionsLayout?.visibility = if (optionsLayout?.visibility == View.VISIBLE) {
                View.GONE
            } else {
                View.VISIBLE
            }
        })

        optionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 8.dp(), 0, 0)
            addView(createMainButton("Open App") {
                val intent = Intent(this@FloatingBubbleService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
            })
            addView(createMainButton("Hide Panel") {
                removePanel()
            })
            addView(createMainButton("Stop Bubble") {
                stopSelf()
            })
        }
        root.addView(optionsLayout)

        return root
    }

    private fun createTitleText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.parseColor("#102A43"))
            setPadding(0, 0, 0, 6.dp())
        }
    }

    private fun createBodyText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#243B53"))
            setPadding(0, 0, 0, 6.dp())
        }
    }

    private fun createSectionText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor("#486581"))
            setPadding(0, 6.dp(), 0, 6.dp())
        }
    }

    private fun createResultBodyText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#102A43"))
            setPadding(10.dp(), 8.dp(), 10.dp(), 8.dp())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10.dp().toFloat()
                setColor(Color.parseColor("#FFFFFF"))
                setStroke(1.dp(), Color.parseColor("#B7C7E6"))
            }
        }
    }

    private fun createMainButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }
    }

    private fun showRuleDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp(), 12.dp(), 22.dp(), 0)
        }

        val roleSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@FloatingBubbleService,
                android.R.layout.simple_spinner_dropdown_item,
                FloatingRuleStore.ROLE_OPTIONS
            )
            setSelection(FloatingRuleStore.ROLE_OPTIONS.indexOf(ruleStore.getRelationshipRole()))
        }

        val customRuleInput = EditText(this).apply {
            hint = "Custom rule (optional)"
            setText(ruleStore.getCustomRule())
        }

        container.addView(createBodyText("Relationship role"))
        container.addView(roleSpinner)
        container.addView(createBodyText("Rule note"))
        container.addView(customRuleInput)

        val dialog = AlertDialog.Builder(
            ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
        )
            .setTitle("Set Rule")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val selectedRole = FloatingRuleStore.ROLE_OPTIONS[roleSpinner.selectedItemPosition]
                ruleStore.setRelationshipRole(selectedRole)
                ruleStore.setCustomRule(customRuleInput.text.toString())
                refreshPanelValues()
                statusTextView?.text = "Rule updated: $selectedRole"
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun analyzeCurrentScreen() {
        if (!privacyConsentStore.isPrivacyAccepted()) {
            statusTextView?.text = "Status: privacy consent is required before analysis."
            return
        }

        var sourceLabel = ""
        var capturedScreen = ""
        if (privacyConsentStore.isAccessibilityCaptureEnabled()) {
            val previousCaptureTime = screenCaptureStore.readCapturedAtEpochMillis()
            val capturedNow = ChatAccessibilityService.requestImmediateCapture()
            val latestCaptureTime = screenCaptureStore.readCapturedAtEpochMillis()
            val isFreshCapture = latestCaptureTime > 0L &&
                latestCaptureTime >= previousCaptureTime &&
                (System.currentTimeMillis() - latestCaptureTime) <= 8_000L

            capturedScreen = if (capturedNow || isFreshCapture) {
                screenCaptureStore.readCapturedText()
            } else {
                ""
            }

            if (capturedScreen.isNotBlank()) {
                sourceLabel = "accessibility"
            }
        }

        val fromNotifications = if (privacyConsentStore.canCaptureNotifications()) {
            notificationDraftStore.toConversationDraft()
        } else {
            ""
        }

        val sourceText = if (capturedScreen.isNotBlank()) {
            capturedScreen
        } else {
            if (fromNotifications.isNotBlank()) {
                sourceLabel = "notification"
            }
            fromNotifications
        }
        if (sourceText.isBlank()) {
            statusTextView?.text = "Status: no readable data. Turn on accessibility capture, then open target chat screen."
            return
        }

        val parsedMessages = ConversationParser.parse(sourceText)
        if (parsedMessages.isEmpty()) {
            statusTextView?.text = "Status: could not parse current screen content."
            return
        }

        val messages = parsedMessages.toMutableList()
        val customRule = ruleStore.getCustomRule()
        if (customRule.isNotBlank()) {
            messages.add(0, ChatMessage(sender = "me", text = "Rule: $customRule"))
        }

        if (isAnalyzingCurrentScreen) {
            statusTextView?.text = "Status: already analyzing. Please wait..."
            return
        }

        val role = ruleStore.getRelationshipRole()
        isAnalyzingCurrentScreen = true
        statusTextView?.text = "Status: analyzing current screen..."

        serviceScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    chatRepository.analyze(
                        relationshipRole = role,
                        chatHistory = messages
                    )
                }

                if (result.isSuccess) {
                    val analysis = result.getOrThrow()
                    val summary = analysis.summary?.takeIf { it.isNotBlank() } ?: "No summary"
                    val sampleReply = analysis.suggestions
                        .firstOrNull { it.sampleReply.isNotBlank() }
                        ?.sampleReply
                        .orEmpty()
                    val normalizedSource = sourceLabel.ifBlank { "current screen" }

                    latestSummary = summary
                    latestReply = sampleReply.ifBlank { "-" }
                    applyLatestAnalysisToPanel()

                    saveAnalysisToLocal(role = role, summary = summary, sampleReply = sampleReply)
                    statusTextView?.text = "Status: analyze success from $normalizedSource."
                    showAnalysisResultOverlay(
                        sourceLabel = normalizedSource,
                        summary = summary,
                        suggestions = analysis.suggestions
                    )
                } else {
                    statusTextView?.text = "Status: analyze failed - ${result.exceptionOrNull()?.message.orEmpty()}"
                }
            } finally {
                isAnalyzingCurrentScreen = false
            }
        }
    }

    private fun saveAnalysisToLocal(role: String, summary: String, sampleReply: String) {
        analysisHistoryStore.save(
            AnalysisHistoryItem(
                id = UUID.randomUUID().toString(),
                createdAtEpochMillis = System.currentTimeMillis(),
                relationshipRole = role,
                summary = summary,
                sampleReply = sampleReply
            )
        )

        latestAnalysisWidgetStore.save(summary = summary, sampleReply = sampleReply)
        serviceScope.launch {
            runCatching { widgetSyncManager.refreshLatestSuggestionWidget() }
        }
    }

    private fun copyLatestReply() {
        val text = resultReplyTextView?.text?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: "Reply\n$latestReply"
        val prefix = "Reply"
        val reply = if (text.startsWith(prefix)) {
            text.removePrefix(prefix).trim().removePrefix(":").trim()
        } else {
            text.trim()
        }

        if (reply.isBlank() || reply == "-") {
            showToast("No reply to copy.")
            return
        }

        copyTextToClipboard(label = "reply", text = reply)
        showToast("Reply copied")
    }

    private fun showAnalysisResultOverlay(
        sourceLabel: String,
        summary: String,
        suggestions: List<AnalysisSuggestion>
    ) {
        removeResultOverlay()

        val topReply = suggestions
            .firstOrNull { it.sampleReply.isNotBlank() }
            ?.sampleReply
            .orEmpty()
        val visibleSuggestions = suggestions.take(3)
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayComposeOwner)
            setViewTreeSavedStateRegistryOwner(overlayComposeOwner)
            overlayRecomposer?.let { setParentCompositionContext(it) }
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme {
                    AnalysisResultOverlayContent(
                        sourceLabel = sourceLabel,
                        summary = summary,
                        suggestions = visibleSuggestions,
                        onCopyTopReply = {
                            if (topReply.isBlank()) {
                                showToast("No reply to copy.")
                            } else {
                                copyTextToClipboard(label = "reply", text = topReply)
                                showToast("Top reply copied")
                            }
                        },
                        onClose = ::removeResultOverlay
                    )
                }
            }
        }

        val width = (resources.displayMetrics.widthPixels - 24.dp()).coerceAtMost(340.dp())
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 72.dp()
        }

        runCatching {
            windowManager.addView(composeView, params)
            resultOverlayView = composeView
        }.onFailure { throwable ->
            statusTextView?.text = "Status: cannot show overlay result - ${throwable.message.orEmpty()}"
            showToast("Unable to show analyze result popup.")
        }
    }

    @Composable
    private fun AnalysisResultOverlayContent(
        sourceLabel: String,
        summary: String,
        suggestions: List<AnalysisSuggestion>,
        onCopyTopReply: () -> Unit,
        onClose: () -> Unit
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = ComposeColor(0xFFEDF3FF)
            ),
            border = BorderStroke(1.dp, ComposeColor(0xFFB7C7E6))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ComposeText(
                    text = "Analysis complete",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ComposeColor(0xFF102A43)
                )
                ComposeText(
                    text = "Source: $sourceLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = ComposeColor(0xFF243B53)
                )

                ComposeText(
                    text = "Summary",
                    style = MaterialTheme.typography.labelMedium,
                    color = ComposeColor(0xFF486581)
                )
                OverlayContentBlock(text = summary)

                ComposeText(
                    text = "Suggestions",
                    style = MaterialTheme.typography.labelMedium,
                    color = ComposeColor(0xFF486581)
                )
                if (suggestions.isEmpty()) {
                    OverlayContentBlock(text = "No suggestion available.")
                } else {
                    suggestions.forEachIndexed { index, suggestion ->
                        val content = suggestion.sampleReply
                            .takeIf { it.isNotBlank() }
                            ?: suggestion.interpretation
                                .takeIf { it.isNotBlank() }
                            ?: "-"
                        OverlayContentBlock(
                            text = "${index + 1}. ${suggestion.title}\n$content"
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ComposeButton(
                        onClick = onCopyTopReply,
                        modifier = Modifier.weight(1f)
                    ) {
                        ComposeText("Copy top reply")
                    }
                    ComposeButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    ) {
                        ComposeText("Close")
                    }
                }
            }
        }
    }

    @Composable
    private fun OverlayContentBlock(text: String) {
        Surface(
            color = ComposeColor(0xFFF7FAFF),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, ComposeColor(0xFFB7C7E6))
        ) {
            ComposeText(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = ComposeColor(0xFF102A43)
            )
        }
    }

    private fun removeResultOverlay() {
        resultOverlayView?.let { attachedView ->
            runCatching { windowManager.removeView(attachedView) }
        }
        resultOverlayView = null
    }

    private fun copyTextToClipboard(label: String, text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun refreshPanelValues() {
        roleTextView?.text = "Role: ${ruleStore.getRelationshipRole()}"
        updateModeLabel()
        applyLatestAnalysisToPanel()
    }

    private fun applyLatestAnalysisToPanel() {
        resultSummaryTextView?.text = "Summary\n$latestSummary"
        resultReplyTextView?.text = "Reply\n${latestReply.ifBlank { "-" }}"
    }

    private fun updateModeLabel() {
        modeTextView?.text = "Mode: ${currentMode.label}"
    }

    private fun updatePanelPosition() {
        val params = panelParams ?: return
        val attachedPanel = panelView ?: return

        params.x = (bubbleParams.x + 70.dp()).coerceAtMost(resources.displayMetrics.widthPixels - 300.dp())
        params.y = bubbleParams.y.coerceAtLeast(12.dp())

        runCatching {
            windowManager.updateViewLayout(attachedPanel, params)
        }.onFailure {
            // Panel might already be detached if user toggles quickly while dragging bubble.
            panelView = null
            panelParams = null
        }
    }

    private fun removePanel() {
        panelView?.let {
            runCatching { windowManager.removeView(it) }
        }
        panelView = null
        panelParams = null
        modeTextView = null
        roleTextView = null
        statusTextView = null
        resultSummaryTextView = null
        resultReplyTextView = null
        optionsLayout = null
    }

    private fun removeBubble() {
        bubbleView?.let {
            runCatching { windowManager.removeView(it) }
        }
        bubbleView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Floating Bubble",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_bubble_notification)
        .setContentTitle("Chat Assistant Bubble")
        .setContentText("Bubble is running. Tap to open app.")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private class OverlayComposeOwner : SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        init {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        fun markActive() {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun markDestroyed() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
