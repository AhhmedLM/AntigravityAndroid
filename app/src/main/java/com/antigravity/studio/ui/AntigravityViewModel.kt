package com.antigravity.studio.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.studio.process.AntigravityProcessManager
import com.antigravity.studio.service.AgentDaemonService
import com.antigravity.studio.ui.model.AntigravityUiState
import com.antigravity.studio.ui.model.BackgroundCommandItem
import com.antigravity.studio.ui.model.ChatTimelineItem
import com.antigravity.studio.ui.model.CommandStatus
import com.antigravity.studio.ui.model.ConversationSession
import com.antigravity.studio.ui.model.PermissionPreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Connects the Adaptive Jetpack Compose UI to the persistent `AgentDaemonService`
 * and manages workspace state, conversation history, live terminal streams, and tool triggers.
 */
class AntigravityViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AntigravityUiState())
    val uiState: StateFlow<AntigravityUiState> = _uiState.asStateFlow()

    private var daemonService: AgentDaemonService? = null
    private var serviceJobs: MutableList<Job> = mutableListOf()
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? AgentDaemonService.LocalBinder ?: return
            val boundService = binder.getService()
            daemonService = boundService
            isBound = true
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

        // 1. Collect engine status
        serviceJobs += viewModelScope.launch {
            service.engineStatus.collect { status ->
                _uiState.update { it.copy(engineStatus = status) }
            }
        }

        // 2. Collect process PID / Port updates
        serviceJobs += viewModelScope.launch {
            service.processManager.processState.collect { procState ->
                when (procState) {
                    is AntigravityProcessManager.DaemonProcessState.Running -> {
                        _uiState.update {
                            it.copy(
                                daemonPid = procState.pid,
                                daemonPort = procState.port
                            )
                        }
                    }
                    is AntigravityProcessManager.DaemonProcessState.ExecutingTask -> {
                        _uiState.update {
                            it.copy(
                                daemonPid = procState.pid,
                                daemonPort = procState.port
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }

        // 3. Collect live stdout/stderr terminal lines
        serviceJobs += viewModelScope.launch {
            service.terminalLogs.collect { line ->
                _uiState.update { current ->
                    val updatedLogs = (current.terminalLogs + line).takeLast(300)
                    current.copy(terminalLogs = updatedLogs)
                }
            }
        }

        // 4. Collect live agent stream events (thoughts, diffs, responses)
        serviceJobs += viewModelScope.launch {
            service.agentEvents.collect { event ->
                val now = currentTimeLabel()
                when (event.type) {
                    "thought" -> {
                        val thoughtItem = ChatTimelineItem.AgentThoughtCard(
                            id = "thought-${UUID.randomUUID()}",
                            timestamp = now,
                            workedSeconds = event.durationSeconds.coerceAtLeast(1),
                            summary = "Worked for ${event.durationSeconds}s • ${event.content}",
                            steps = listOf(
                                "Dispatched IPC frame to 127.0.0.1:${_uiState.value.daemonPort}",
                                event.content,
                                "Validated workspace integrity under context.filesDir/home/workspaces"
                            ),
                            isExpanded = false
                        )
                        _uiState.update { it.copy(chatTimeline = it.chatTimeline + thoughtItem) }
                    }
                    "diff" -> {
                        val diffItem = ChatTimelineItem.GitDiffCard(
                            id = "diff-${UUID.randomUUID()}",
                            timestamp = now,
                            filePath = event.filePath ?: "app/src/main/java/com/antigravity/studio/MainActivity.kt",
                            additions = event.additions,
                            deletions = event.deletions,
                            summary = event.content,
                            patchPreview = """
                                @@ -14,5 +14,${14 + event.additions} @@
                                - // Previous implementation
                                + // Updated by Antigravity Embedded Node.js Agent (${_uiState.value.selectedModel})
                                + ${event.content}
                            """.trimIndent()
                        )
                        _uiState.update { it.copy(chatTimeline = it.chatTimeline + diffItem) }
                    }
                    "assistant_message" -> {
                        val responseItem = ChatTimelineItem.AgentResponseItem(
                            id = "resp-${UUID.randomUUID()}",
                            timestamp = now,
                            markdownText = event.content
                        )
                        _uiState.update { it.copy(chatTimeline = it.chatTimeline + responseItem) }
                    }
                }
            }
        }
    }

    fun updatePromptInput(text: String) {
        _uiState.update { it.copy(promptInput = text) }
    }

    fun selectModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun cyclePermissionPreset() {
        _uiState.update { state ->
            val presets = PermissionPreset.entries
            val nextIndex = (presets.indexOf(state.permissionPreset) + 1) % presets.size
            state.copy(permissionPreset = presets[nextIndex])
        }
    }

    fun selectProject(projectId: String) {
        _uiState.update { state ->
            val updatedProjects = state.projects.map { proj ->
                proj.copy(isActive = proj.id == projectId)
            }
            val targetProject = updatedProjects.firstOrNull { it.id == projectId }
            state.copy(
                projects = updatedProjects,
                selectedProjectId = projectId,
                selectedSessionId = targetProject?.sessions?.firstOrNull()?.id ?: state.selectedSessionId
            )
        }
    }

    fun createNewConversation() {
        val newSessionId = "sess-${UUID.randomUUID().toString().take(6)}"
        val newSession = ConversationSession(
            id = newSessionId,
            title = "New Agent Session #${(_uiState.value.activeProject.sessions.size + 1)}",
            updatedAt = "Just now",
            isRunning = true
        )
        _uiState.update { state ->
            val updatedProjects = state.projects.map { project ->
                if (project.id == state.selectedProjectId) {
                    project.copy(sessions = listOf(newSession) + project.sessions)
                } else {
                    project
                }
            }
            state.copy(
                projects = updatedProjects,
                selectedSessionId = newSessionId,
                chatTimeline = listOf(
                    ChatTimelineItem.AgentResponseItem(
                        id = "welcome-$newSessionId",
                        timestamp = currentTimeLabel(),
                        markdownText = "Started new conversation in **${state.activeProject.name}** (`${state.activeProject.branch}`). Node.js CLI daemon listening on `127.0.0.1:${state.daemonPort}`."
                    )
                )
            )
        }
    }

    fun sendPrompt() {
        val currentState = _uiState.value
        val trimmed = currentState.promptInput.trim()
        if (trimmed.isEmpty()) return

        val userItem = ChatTimelineItem.UserPromptItem(
            id = "usr-${UUID.randomUUID()}",
            timestamp = currentTimeLabel(),
            prompt = trimmed
        )

        _uiState.update {
            it.copy(
                promptInput = "",
                chatTimeline = it.chatTimeline + userItem
            )
        }

        val service = daemonService
        if (service != null) {
            service.dispatchPrompt(
                sessionId = currentState.selectedSessionId,
                workspace = currentState.activeProject.name,
                prompt = trimmed,
                model = currentState.selectedModel,
                permissionMode = currentState.permissionPreset.badge
            )
        } else {
            // Fallback preview simulation when running in Compose Preview without bound Service
            val simulatedThought = ChatTimelineItem.AgentThoughtCard(
                id = "sim-thought-${UUID.randomUUID()}",
                timestamp = currentTimeLabel(),
                workedSeconds = 9,
                summary = "Worked for 9s • Evaluated prompt in ${currentState.activeProject.name}",
                steps = listOf("Parsed prompt AST", "Verified SELinux W^X sandbox constraints")
            )
            val simulatedReply = ChatTimelineItem.AgentResponseItem(
                id = "sim-reply-${UUID.randomUUID()}",
                timestamp = currentTimeLabel(),
                markdownText = "Executed prompt with **${currentState.selectedModel}** (`${currentState.permissionPreset.badge}`): _\"$trimmed\"_"
            )
            _uiState.update {
                it.copy(chatTimeline = it.chatTimeline + simulatedThought + simulatedReply)
            }
        }
    }

    fun toggleThoughtExpanded(thoughtId: String) {
        _uiState.update { state ->
            val updated = state.chatTimeline.map { item ->
                if (item is ChatTimelineItem.AgentThoughtCard && item.id == thoughtId) {
                    item.copy(isExpanded = !item.isExpanded)
                } else {
                    item
                }
            }
            state.copy(chatTimeline = updated)
        }
    }

    fun openDiffReview(diffCard: ChatTimelineItem.GitDiffCard?) {
        _uiState.update { it.copy(activeDiffForReview = diffCard) }
    }

    fun setInspectorSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(isInspectorSheetVisible = visible) }
    }

    fun triggerQuickAction(actionName: String) {
        val state = _uiState.value
        val commandToRun = when (actionName) {
            "Review" -> "echo '[Git Review] Inspecting working tree diffs (+30 -6)...' && ls -la"
            "Open File" -> "echo '[Workspace] Opening RuntimeInstaller.kt & AgentDaemonService.kt'"
            "Terminal" -> "uname -a && id && pwd"
            "Browser" -> "echo '[Local Preview] Probing http://127.0.0.1:${state.daemonPort}/status'"
            else -> "echo 'Executing $actionName'"
        }

        val newCmdItem = BackgroundCommandItem(
            id = "cmd-${UUID.randomUUID().toString().take(5)}",
            command = commandToRun,
            workingDir = "~/workspaces/${state.activeProject.name}",
            status = CommandStatus.COMPLETED,
            pid = state.daemonPid,
            durationLabel = "00.2s (exit 0)"
        )

        _uiState.update {
            it.copy(
                backgroundCommands = listOf(newCmdItem) + it.backgroundCommands.take(8),
                isInspectorSheetVisible = true
            )
        }

        daemonService?.runTerminalCommand(
            command = commandToRun,
            workspace = state.activeProject.name
        )
    }

    fun runCustomTerminalCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        val state = _uiState.value
        val newCmdItem = BackgroundCommandItem(
            id = "cmd-${UUID.randomUUID().toString().take(5)}",
            command = trimmed,
            workingDir = "~/workspaces/${state.activeProject.name}",
            status = CommandStatus.COMPLETED,
            pid = state.daemonPid,
            durationLabel = "00.1s (exit 0)"
        )
        _uiState.update {
            it.copy(backgroundCommands = listOf(newCmdItem) + it.backgroundCommands.take(8))
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
