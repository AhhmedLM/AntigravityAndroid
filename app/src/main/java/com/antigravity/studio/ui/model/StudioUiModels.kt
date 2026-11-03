package com.antigravity.studio.ui.model

import com.antigravity.studio.service.AgentDaemonService

/**
 * State-driven UI models allowing the Compose UI and `@Preview` annotations
 * to render immediately with realistic Antigravity IDE workspace state.
 */
data class WorkspaceProject(
    val id: String,
    val name: String,
    val pathBreadcrumb: String,
    val branch: String,
    val isActive: Boolean,
    val sessions: List<ConversationSession>
)

data class ConversationSession(
    val id: String,
    val title: String,
    val updatedAt: String,
    val isRunning: Boolean = false
)

enum class PermissionPreset(val label: String, val badge: String) {
    AUTO_SAFE("Auto (Safe W^X)", "AUTO"),
    TURBO("Turbo Mode", "TURBO"),
    ASK_EVERY("Ask Before Exec", "STRICT")
}

sealed interface ChatTimelineItem {
    val id: String
    val timestamp: String

    data class UserPromptItem(
        override val id: String,
        override val timestamp: String,
        val prompt: String
    ) : ChatTimelineItem

    data class AgentThoughtCard(
        override val id: String,
        override val timestamp: String,
        val workedSeconds: Int,
        val summary: String,
        val steps: List<String>,
        val isExpanded: Boolean = false
    ) : ChatTimelineItem

    data class GitDiffCard(
        override val id: String,
        override val timestamp: String,
        val filePath: String,
        val additions: Int,
        val deletions: Int,
        val summary: String,
        val patchPreview: String
    ) : ChatTimelineItem

    data class AgentResponseItem(
        override val id: String,
        override val timestamp: String,
        val markdownText: String
    ) : ChatTimelineItem
}

data class BackgroundCommandItem(
    val id: String,
    val command: String,
    val workingDir: String,
    val status: CommandStatus,
    val pid: Long,
    val durationLabel: String
)

enum class CommandStatus {
    RUNNING,
    LISTENING,
    COMPLETED,
    FAILED
}

data class AntigravityUiState(
    val engineStatus: AgentDaemonService.EngineStatus = AgentDaemonService.EngineStatus.LISTENING,
    val daemonPid: Long = 14892L,
    val daemonPort: Int = 8765,
    val heapLimitMb: Int = 256,
    val projects: List<WorkspaceProject> = SampleStudioData.defaultProjects,
    val selectedProjectId: String = "proj-antigravity-android",
    val selectedSessionId: String = "sess-01",
    val availableModels: List<String> = listOf(
        "Gemini 2.5 Pro",
        "Gemini 2.5 Flash",
        "Claude 3.7 Sonnet"
    ),
    val selectedModel: String = "Gemini 2.5 Pro",
    val permissionPreset: PermissionPreset = PermissionPreset.TURBO,
    val promptInput: String = "",
    val chatTimeline: List<ChatTimelineItem> = SampleStudioData.defaultTimeline,
    val backgroundCommands: List<BackgroundCommandItem> = SampleStudioData.defaultCommands,
    val terminalLogs: List<String> = SampleStudioData.defaultTerminalLogs,
    val isInspectorSheetVisible: Boolean = false,
    val activeDiffForReview: ChatTimelineItem.GitDiffCard? = null
) {
    val activeProject: WorkspaceProject
        get() = projects.firstOrNull { it.id == selectedProjectId } ?: projects.first()
}

object SampleStudioData {
    val defaultProjects = listOf(
        WorkspaceProject(
            id = "proj-antigravity-android",
            name = "AntigravityAndroid",
            pathBreadcrumb = "filesDir / home / workspaces / AntigravityAndroid",
            branch = "main",
            isActive = true,
            sessions = listOf(
                ConversationSession(
                    id = "sess-01",
                    title = "Embedded Node.js Daemon & Adaptive UI",
                    updatedAt = "Now",
                    isRunning = true
                ),
                ConversationSession(
                    id = "sess-02",
                    title = "SELinux W^X Binary Verification",
                    updatedAt = "18m ago",
                    isRunning = false
                )
            )
        ),
        WorkspaceProject(
            id = "proj-wear-sensor",
            name = "wear-os-sensor-bridge",
            pathBreadcrumb = "filesDir / home / workspaces / wear-os-sensor-bridge",
            branch = "feat/ble-stream",
            isActive = false,
            sessions = listOf(
                ConversationSession(
                    id = "sess-03",
                    title = "Low-latency ProtoDataStore sync",
                    updatedAt = "2h ago"
                )
            )
        ),
        WorkspaceProject(
            id = "proj-cmp-core",
            name = "compose-multiplatform-core",
            pathBreadcrumb = "filesDir / home / workspaces / compose-multiplatform-core",
            branch = "develop",
            isActive = false,
            sessions = listOf(
                ConversationSession(
                    id = "sess-04",
                    title = "Adaptive WindowSizeClass navigation",
                    updatedAt = "Yesterday"
                )
            )
        )
    )

    val defaultTimeline = listOf(
        ChatTimelineItem.UserPromptItem(
            id = "msg-1",
            timestamp = "15:32",
            prompt = "Architect the embedded Node.js CLI runtime supervisor with 256MB V8 heap cap and wire it to the ForegroundService + Adaptive Material 3 console."
        ),
        ChatTimelineItem.AgentThoughtCard(
            id = "msg-2",
            timestamp = "15:32",
            workedSeconds = 21,
            summary = "Worked for 21s • Verified SELinux W^X binary paths & spawned Node.js daemon",
            steps = listOf(
                "Extracted runtime/bin/{node, antigravity, busybox} to context.filesDir/runtime",
                "Applied POSIX chmod 0755 & verified setExecutable(true, false)",
                "Configured ProcessBuilder with --max-old-space-size=256 and ANTIGRAVITY_PORT=8765",
                "Bound AgentDaemonService (foregroundServiceType=dataSync) to Compose state stream"
            ),
            isExpanded = true
        ),
        ChatTimelineItem.GitDiffCard(
            id = "msg-3",
            timestamp = "15:33",
            filePath = "app/src/main/java/com/antigravity/studio/process/AntigravityProcessManager.kt",
            additions = 30,
            deletions = 6,
            summary = "Added Coroutine stdout/stderr SharedFlow streaming & SIGTERM -> SIGKILL graceful shutdown",
            patchPreview = """
                @@ -48,6 +48,14 @@ class AntigravityProcessManager(
                +    env["HOME"] = paths.homeDir.absolutePath
                +    env["TMPDIR"] = paths.tmpDir.absolutePath
                +    env["PATH"] = "${'$'}{paths.binDir.absolutePath}:${'$'}existingPath"
                +    env["ANTIGRAVITY_PORT"] = port.toString()
                +    env["NODE_ENV"] = "production"
                +    env["NODE_OPTIONS"] = "--max-old-space-size=256"
            """.trimIndent()
        ),
        ChatTimelineItem.GitDiffCard(
            id = "msg-4",
            timestamp = "15:33",
            filePath = "app/src/main/java/com/antigravity/studio/service/AgentDaemonService.kt",
            additions = 42,
            deletions = 4,
            summary = "Implemented START_STICKY foreground service with live localhost:8765 WebSocket/HTTP IPC",
            patchPreview = """
                @@ -92,4 +92,12 @@ class AgentDaemonService : Service() {
                +    ServiceCompat.startForeground(
                +        this,
                +        NOTIFICATION_ID,
                +        notification,
                +        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                +    )
            """.trimIndent()
        ),
        ChatTimelineItem.AgentResponseItem(
            id = "msg-5",
            timestamp = "15:33",
            markdownText = "The embedded **Antigravity Agent Studio** runtime is active on `127.0.0.1:8765` (`arm64-v8a`).\n\n" +
                "- **W^X Compliance**: Executables run strictly from `context.filesDir/runtime/bin`.\n" +
                "- **Memory Containment**: Node.js V8 heap capped at `--max-old-space-size=256`.\n" +
                "- **Adaptive Layout**: Automatically switches between Compact Drawer + BottomSheet and Expanded 3-Pane IDE."
        )
    )

    val defaultCommands = listOf(
        BackgroundCommandItem(
            id = "cmd-daemon",
            command = "antigravity serve --port 8765 --headless",
            workingDir = "~/workspaces/AntigravityAndroid",
            status = CommandStatus.LISTENING,
            pid = 14892L,
            durationLabel = "Listening :8765"
        ),
        BackgroundCommandItem(
            id = "cmd-gradle",
            command = "./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a",
            workingDir = "~/workspaces/AntigravityAndroid",
            status = CommandStatus.COMPLETED,
            pid = 15024L,
            durationLabel = "04.2s (exit 0)"
        ),
        BackgroundCommandItem(
            id = "cmd-git",
            command = "git status --short && git diff --stat",
            workingDir = "~/workspaces/AntigravityAndroid",
            status = CommandStatus.COMPLETED,
            pid = 15089L,
            durationLabel = "00.3s (exit 0)"
        )
    )

    val defaultTerminalLogs = listOf(
        "15:32:01.104  [Supervisor] Verifying runtime binaries in context.filesDir/runtime...",
        "15:32:01.142  [RuntimeInstaller] chmod 0755 applied to runtime/bin/{node, antigravity, busybox}",
        "15:32:01.188  [Supervisor] Spawning: /system/bin/sh .../runtime/bin/antigravity serve --port 8765 --headless",
        "15:32:01.190  [Supervisor] ENV: HOME=.../files/home | ANTIGRAVITY_PORT=8765 | NODE_ENV=production | HEAP=256MB",
        "15:32:01.310  [STDOUT] [Node.js v22.11.0-arm64] V8 Heap constrained via --max-old-space-size=256",
        "15:32:01.345  [STDOUT] [Antigravity] Daemon listening on http://127.0.0.1:8765 (NODE_ENV=production)"
    )
}
