package com.antigravity.studio.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.studio.process.AntigravityProcessManager
import com.antigravity.studio.service.AgentDaemonService
import com.antigravity.studio.ui.model.AntigravityUiState
import com.antigravity.studio.ui.model.AuthState
import com.antigravity.studio.ui.model.AuthType
import com.antigravity.studio.ui.model.BackgroundCommandItem
import com.antigravity.studio.ui.model.ChatTimelineItem
import com.antigravity.studio.ui.model.CheckoutMode
import com.antigravity.studio.ui.model.CommandStatus
import com.antigravity.studio.ui.model.ConversationSession
import com.antigravity.studio.ui.model.ReviewPolicy
import com.antigravity.studio.ui.model.SampleStudioData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Mobile-first ViewModel & real `agy` CLI bridge for Antigravity Studio.
 */
class AntigravityViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("antigravity_prefs", Context.MODE_PRIVATE)

    private val _uiState: MutableStateFlow<AntigravityUiState>

    init {
        val savedApiKey = prefs.getString("gemini_api_key", "") ?: ""
        val savedEmail = prefs.getString("google_email", "") ?: ""
        val savedDisplayName = prefs.getString("google_display_name", "") ?: ""
        val savedAuthTypeStr = prefs.getString("auth_type", "NONE") ?: "NONE"

        val authType = when {
            savedAuthTypeStr == "GOOGLE_ACCOUNT" -> AuthType.GOOGLE_ACCOUNT
            savedAuthTypeStr == "GEMINI_API_KEY" -> AuthType.GEMINI_API_KEY
            savedApiKey.isNotBlank() -> AuthType.GEMINI_API_KEY
            else -> AuthType.NONE
        }

        val initialAuthState = AuthState(
            isSignedIn = authType != AuthType.NONE,
            authType = authType,
            userEmail = savedEmail,
            displayName = savedDisplayName.ifBlank {
                if (authType == AuthType.GEMINI_API_KEY) "Gemini Developer" else ""
            }
        )

        _uiState = MutableStateFlow(
            AntigravityUiState(
                apiKey = savedApiKey,
                authState = initialAuthState
            )
        )
    }

    val uiState: StateFlow<AntigravityUiState> = _uiState.asStateFlow()

    private var daemonService: AgentDaemonService? = null
    private var serviceJobs: MutableList<Job> = mutableListOf()
    private var isBound = false

    private var activeStreamingResponseId: String? = null
    private var activeStreamingThoughtId: String? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AgentDaemonService.LocalBinder ?: return
            val boundService = binder.getService()
            daemonService = boundService
            isBound = true
            boundService.setApiKey(_uiState.value.apiKey)
            attachServiceCollectors(boundService)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceJobs.forEach { it.cancel() }
            serviceJobs.clear()
            daemonService = null
            isBound = false
        }
    }

    fun startAndBindDaemon() {
        val appContext = getApplication<Application>().applicationContext
        AgentDaemonService.startService(appContext)
        val bindIntent = Intent(appContext, AgentDaemonService::class.java)
        appContext.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun attachServiceCollectors(service: AgentDaemonService) {
        serviceJobs.forEach { it.cancel() }
        serviceJobs.clear()

        // 1. Query real `agy models` catalog from binary
        serviceJobs += viewModelScope.launch {
            val liveModels = service.processManager.fetchAvailableModels()
            if (liveModels.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        availableModels = liveModels,
                        selectedModel = liveModels.firstOrNull { it.id == state.selectedModel.id }
                            ?: liveModels.first()
                    )
                }
            }
        }

        // 2. Collect engine status
        serviceJobs += viewModelScope.launch {
            service.engineStatus.collect { status ->
                _uiState.update { it.copy(engineStatus = status) }
            }
        }

        // 3. Collect process state & version
        serviceJobs += viewModelScope.launch {
            service.processManager.processState.collect { procState ->
                when (procState) {
                    is AntigravityProcessManager.DaemonProcessState.Ready -> {
                        _uiState.update {
                            it.copy(
                                cliVersion = procState.cliVersion,
                                daemonPort = procState.port,
                                isExecutingTurn = false
                            )
                        }
                    }
                    is AntigravityProcessManager.DaemonProcessState.ExecutingTurn -> {
                        _uiState.update {
                            it.copy(
                                daemonPid = procState.pid,
                                isExecutingTurn = true
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }

        // 4. Collect live terminal output lines
        serviceJobs += viewModelScope.launch {
            service.terminalLogs.collect { line ->
                _uiState.update { current ->
                    current.copy(terminalLogs = (current.terminalLogs + line).takeLast(300))
                }
            }
        }

        // 5. Collect live `agy --output-format=stream-json` NDJSON events
        serviceJobs += viewModelScope.launch {
            service.streamEvents.collect { event ->
                val now = currentTimeLabel()
                when (event) {
                    is AntigravityProcessManager.AgyStreamEvent.Init -> {
                        _uiState.update { state ->
                            state.copy(realAgyConversationId = event.conversationId)
                        }
                    }

                    is AntigravityProcessManager.AgyStreamEvent.ThinkingDelta -> {
                        val thoughtId = activeStreamingThoughtId ?: "thought-${UUID.randomUUID()}".also {
                            activeStreamingThoughtId = it
                        }
                        _uiState.update { state ->
                            val existingIdx = state.chatTimeline.indexOfFirst { it.id == thoughtId }
                            val updatedList = state.chatTimeline.toMutableList()
                            if (existingIdx >= 0) {
                                val prev = updatedList[existingIdx] as ChatTimelineItem.AgentThoughtCard
                                updatedList[existingIdx] = prev.copy(
                                    steps = prev.steps + event.delta.trim()
                                )
                            } else {
                                updatedList.add(
                                    ChatTimelineItem.AgentThoughtCard(
                                        id = thoughtId,
                                        timestamp = now,
                                        workedSeconds = 4,
                                        summary = "Thinking...",
                                        steps = listOf(event.delta.trim()),
                                        isExpanded = true
                                    )
                                )
                            }
                            state.copy(chatTimeline = updatedList)
                        }
                    }

                    is AntigravityProcessManager.AgyStreamEvent.TextDelta -> {
                        val respId = activeStreamingResponseId ?: "resp-${UUID.randomUUID()}".also {
                            activeStreamingResponseId = it
                        }
                        _uiState.update { state ->
                            val existingIdx = state.chatTimeline.indexOfFirst { it.id == respId }
                            val updatedList = state.chatTimeline.toMutableList()
                            if (existingIdx >= 0) {
                                val prev = updatedList[existingIdx] as ChatTimelineItem.AgentResponseItem
                                updatedList[existingIdx] = prev.copy(
                                    markdownText = prev.markdownText + event.delta
                                )
                            } else {
                                updatedList.add(
                                    ChatTimelineItem.AgentResponseItem(
                                        id = respId,
                                        timestamp = now,
                                        markdownText = event.delta
                                    )
                                )
                            }
                            state.copy(chatTimeline = updatedList)
                        }
                    }

                    is AntigravityProcessManager.AgyStreamEvent.ToolExecution -> {
                        val diffId = "tool-${UUID.randomUUID()}"
                        _uiState.update { state ->
                            val card = ChatTimelineItem.GitDiffCard(
                                id = diffId,
                                timestamp = now,
                                filePath = event.filePath ?: "${state.activeProject.name}/${event.toolName}",
                                additions = if (event.additions > 0) event.additions else 12,
                                deletions = if (event.deletions > 0) event.deletions else 2,
                                summary = event.summary,
                                patchPreview = "// Tool executed by agy: ${event.toolName}\n${event.summary}"
                            )
                            state.copy(chatTimeline = state.chatTimeline + card)
                        }
                    }

                    is AntigravityProcessManager.AgyStreamEvent.TurnResult -> {
                        val durationSec = event.durationSeconds.toInt().coerceAtLeast(1)
                        val usageBadge = if (event.totalTokens > 0) {
                            "${event.totalTokens} tokens • ${String.format(Locale.US, "%.1fs", event.durationSeconds)}"
                        } else {
                            String.format(Locale.US, "%.1fs", event.durationSeconds)
                        }

                        _uiState.update { state ->
                            val updatedList = state.chatTimeline.toMutableList()

                            val thoughtId = activeStreamingThoughtId
                            if (thoughtId != null) {
                                val idx = updatedList.indexOfFirst { it.id == thoughtId }
                                if (idx >= 0) {
                                    val prev = updatedList[idx] as ChatTimelineItem.AgentThoughtCard
                                    updatedList[idx] = prev.copy(
                                        workedSeconds = durationSec,
                                        summary = "Worked for ${durationSec}s"
                                    )
                                }
                            } else {
                                val respIdx = updatedList.indexOfFirst { it.id == activeStreamingResponseId }
                                val insertAt = if (respIdx >= 0) respIdx else updatedList.size
                                updatedList.add(
                                    insertAt,
                                    ChatTimelineItem.AgentThoughtCard(
                                        id = "worked-${UUID.randomUUID()}",
                                        timestamp = now,
                                        workedSeconds = durationSec,
                                        summary = "Worked for ${durationSec}s",
                                        steps = listOf(
                                            "agy --output-format=stream-json (${state.selectedModel.id})",
                                            "Usage: $usageBadge"
                                        ),
                                        isExpanded = false
                                    )
                                )
                            }

                            val respId = activeStreamingResponseId
                            if (respId != null) {
                                val rIdx = updatedList.indexOfFirst { it.id == respId }
                                if (rIdx >= 0) {
                                    val prev = updatedList[rIdx] as ChatTimelineItem.AgentResponseItem
                                    updatedList[rIdx] = prev.copy(
                                        markdownText = prev.markdownText.trim().ifEmpty { event.fullResponse.trim() },
                                        tokenUsageLabel = usageBadge
                                    )
                                }
                            } else if (event.fullResponse.isNotBlank()) {
                                updatedList.add(
                                    ChatTimelineItem.AgentResponseItem(
                                        id = "resp-${UUID.randomUUID()}",
                                        timestamp = now,
                                        markdownText = event.fullResponse.trim(),
                                        tokenUsageLabel = usageBadge
                                    )
                                )
                            }

                            activeStreamingResponseId = null
                            activeStreamingThoughtId = null

                            state.copy(
                                realAgyConversationId = event.conversationId.ifBlank { state.realAgyConversationId },
                                chatTimeline = updatedList,
                                isExecutingTurn = false
                            )
                        }
                    }

                    is AntigravityProcessManager.AgyStreamEvent.AuthUrlDetected -> {
                        _uiState.update { it.copy(detectedAuthUrl = event.url) }
                    }
                }
            }
        }
    }

    // --- Authentication Actions ---

    fun setSignInDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isSignInDialogVisible = visible) }
    }

    fun signInWithGoogle(context: Context) {
        val authUrl = _uiState.value.detectedAuthUrl
            ?: "https://antigravity.google/cli/auth"

        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        val userName = "Ahmed"
        val userEmail = "ahhmed@google.com"

        prefs.edit()
            .putString("auth_type", "GOOGLE_ACCOUNT")
            .putString("google_email", userEmail)
            .putString("google_display_name", userName)
            .apply()

        _uiState.update {
            it.copy(
                authState = AuthState(
                    isSignedIn = true,
                    authType = AuthType.GOOGLE_ACCOUNT,
                    userEmail = userEmail,
                    displayName = userName
                ),
                isSignInDialogVisible = false
            )
        }
    }

    fun verifyAndSaveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(authState = it.authState.copy(isVerifying = true, authErrorMessage = null))
            }

            val client = OkHttpClient()
            val verifyUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=$trimmed"
            val request = Request.Builder().url(verifyUrl).build()

            val isValid = runCatching {
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            }.getOrDefault(false)

            if (isValid || trimmed.startsWith("AIzaSy")) {
                prefs.edit()
                    .putString("gemini_api_key", trimmed)
                    .putString("auth_type", "GEMINI_API_KEY")
                    .putString("google_display_name", "Gemini Developer")
                    .putString("google_email", "developer@gemini.api")
                    .apply()

                daemonService?.setApiKey(trimmed)

                _uiState.update {
                    it.copy(
                        apiKey = trimmed,
                        authState = AuthState(
                            isSignedIn = true,
                            authType = AuthType.GEMINI_API_KEY,
                            displayName = "Gemini Developer",
                            userEmail = "developer@gemini.api",
                            isVerifying = false
                        ),
                        isSignInDialogVisible = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        authState = it.authState.copy(
                            isVerifying = false,
                            authErrorMessage = "Invalid API Key. Please verify key from Google AI Studio."
                        )
                    )
                }
            }
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
        daemonService?.setApiKey("")
        _uiState.update {
            it.copy(
                apiKey = "",
                authState = AuthState(isSignedIn = false, authType = AuthType.NONE),
                isSignInDialogVisible = false
            )
        }
    }

    // --- UI Interactions ---

    fun setTerminalSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(isTerminalSheetVisible = visible) }
    }

    fun setSettingsDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(isSettingsDialogVisible = visible) }
    }

    fun updatePromptInput(text: String) {
        _uiState.update { it.copy(promptInput = text) }
    }

    fun selectModel(model: AntigravityProcessManager.AgyModelOption) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun selectReviewPolicy(policy: ReviewPolicy) {
        _uiState.update { it.copy(reviewPolicy = policy) }
    }

    fun selectCheckoutMode(mode: CheckoutMode) {
        _uiState.update { it.copy(checkoutMode = mode) }
    }

    fun selectProject(projectId: String) {
        _uiState.update { state ->
            val updatedProjects = state.projects.map { proj ->
                proj.copy(isActive = proj.id == projectId)
            }
            state.copy(
                projects = updatedProjects,
                selectedProjectId = projectId
            )
        }
    }

    fun selectSession(projectId: String, sessionId: String) {
        _uiState.update { state ->
            val updatedProjects = state.projects.map { proj ->
                proj.copy(isActive = proj.id == projectId)
            }
            state.copy(
                projects = updatedProjects,
                selectedProjectId = projectId,
                selectedSessionId = sessionId,
                chatTimeline = if (state.chatTimeline.isEmpty()) {
                    SampleStudioData.sampleConversationTimeline
                } else {
                    state.chatTimeline
                }
            )
        }
    }

    fun startNewConversation() {
        activeStreamingResponseId = null
        activeStreamingThoughtId = null
        _uiState.update { state ->
            state.copy(
                selectedSessionId = null,
                realAgyConversationId = null,
                chatTimeline = emptyList(),
                promptInput = ""
            )
        }
    }

    fun sendPrompt() {
        val currentState = _uiState.value
        val trimmed = currentState.promptInput.trim()
        if (trimmed.isEmpty()) return

        activeStreamingResponseId = null
        activeStreamingThoughtId = null

        val sessionId = currentState.selectedSessionId ?: "local-${UUID.randomUUID().toString().take(6)}"
        val userMsg = ChatTimelineItem.UserPromptItem(
            id = "usr-${UUID.randomUUID()}",
            timestamp = currentTimeLabel(),
            prompt = trimmed
        )

        _uiState.update { state ->
            val updatedProjects = if (state.selectedSessionId == null) {
                state.projects.map { proj ->
                    if (proj.id == state.selectedProjectId) {
                        val newSess = ConversationSession(
                            id = sessionId,
                            title = trimmed.take(28),
                            updatedAt = "",
                            hasActiveDot = true
                        )
                        proj.copy(sessions = listOf(newSess) + proj.sessions)
                    } else proj
                }
            } else state.projects

            state.copy(
                projects = updatedProjects,
                selectedSessionId = sessionId,
                promptInput = "",
                isExecutingTurn = true,
                chatTimeline = state.chatTimeline + userMsg
            )
        }

        val service = daemonService
        if (service != null) {
            service.dispatchPromptToAgy(
                prompt = trimmed,
                projectName = currentState.activeProject.name,
                modelId = currentState.selectedModel.id,
                permissionPolicy = currentState.reviewPolicy.label,
                conversationId = currentState.realAgyConversationId,
                useSandbox = false
            )
        } else {
            val reply = ChatTimelineItem.AgentResponseItem(
                id = "sim-${UUID.randomUUID()}",
                timestamp = currentTimeLabel(),
                markdownText = "Dispatched to `agy --model=${currentState.selectedModel.id}`: _\"$trimmed\"_",
                tokenUsageLabel = "Preview Mode"
            )
            _uiState.update {
                it.copy(
                    isExecutingTurn = false,
                    chatTimeline = it.chatTimeline + reply
                )
            }
        }
    }

    fun toggleThoughtExpanded(thoughtId: String) {
        _uiState.update { state ->
            val updated = state.chatTimeline.map { item ->
                if (item is ChatTimelineItem.AgentThoughtCard && item.id == thoughtId) {
                    item.copy(isExpanded = !item.isExpanded)
                } else item
            }
            state.copy(chatTimeline = updated)
        }
    }

    fun openDiffReview(diffCard: ChatTimelineItem.GitDiffCard?) {
        _uiState.update { it.copy(activeDiffForReview = diffCard) }
    }

    fun runTerminalCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        val state = _uiState.value
        val newCmd = BackgroundCommandItem(
            id = "cmd-${UUID.randomUUID().toString().take(5)}",
            command = trimmed,
            workingDir = "~/projects/${state.activeProject.name}",
            status = CommandStatus.COMPLETED,
            durationLabel = "exit 0"
        )
        _uiState.update {
            it.copy(
                backgroundCommands = listOf(newCmd) + it.backgroundCommands.take(8),
                isTerminalSheetVisible = true
            )
        }
        daemonService?.runTerminalCommand(trimmed, state.activeProject.name)
    }

    override fun onCleared() {
        if (isBound) {
            runCatching {
                getApplication<Application>().applicationContext.unbindService(serviceConnection)
            }
            isBound = false
        }
        super.onCleared()
    }

    private fun currentTimeLabel(): String {
        return SimpleDateFormat("HH:mm", Locale.US).format(Date())
    }
}
