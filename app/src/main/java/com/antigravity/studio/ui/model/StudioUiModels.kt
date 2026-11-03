package com.antigravity.studio.ui.model

import com.antigravity.studio.process.AntigravityProcessManager
import com.antigravity.studio.service.AgentDaemonService

/**
 * State-driven UI models for the Mobile & Tablet Antigravity Studio application.
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
    val hasActiveDot: Boolean = false
)

enum class ReviewPolicy(val label: String, val cliMode: String) {
    REQUEST_REVIEW("Request Review", "request-review"),
    ALWAYS_PROCEED("Always Proceed", "auto"),
    ACCEPT_EDITS("Accept Edits", "accept-edits"),
    PLAN_MODE("Plan Mode", "plan")
}

enum class CheckoutMode(val label: String) {
    LOCAL_CHECKOUT("Local Checkout"),
    WORKTREE_BRANCH("Worktree Branch"),
    REMOTE_CONTROL("Remote Control Daemon")
}

enum class AuthType {
    NONE,
    GOOGLE_ACCOUNT,
    GEMINI_API_KEY
}

data class AuthState(
    val isSignedIn: Boolean = false,
    val authType: AuthType = AuthType.NONE,
    val userEmail: String = "",
    val displayName: String = "",
    val isVerifying: Boolean = false,
    val authErrorMessage: String? = null
)

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
        val markdownText: String,
        val tokenUsageLabel: String? = null
    ) : ChatTimelineItem
}

data class BackgroundCommandItem(
    val id: String,
    val command: String,
    val workingDir: String,
    val status: CommandStatus,
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
    val cliVersion: String = "1.4.12",
    val daemonPid: Long = 14892L,
    val daemonPort: Int = 8765,
    val heapLimitMb: Int = 256,
    val authState: AuthState = AuthState(),
    val isSignInDialogVisible: Boolean = false,
    val isTerminalSheetVisible: Boolean = false,
    val isSettingsDialogVisible: Boolean = false,
    val projects: List<WorkspaceProject> = SampleStudioData.defaultProjects,
    val selectedProjectId: String = "proj-javakingdom",
    val selectedSessionId: String? = null,
    val realAgyConversationId: String? = null,
    val availableModels: List<AntigravityProcessManager.AgyModelOption> = SampleStudioData.defaultModels,
    val selectedModel: AntigravityProcessManager.AgyModelOption = SampleStudioData.defaultModels.first(),
    val reviewPolicy: ReviewPolicy = ReviewPolicy.REQUEST_REVIEW,
    val checkoutMode: CheckoutMode = CheckoutMode.LOCAL_CHECKOUT,
    val promptInput: String = "",
    val isExecutingTurn: Boolean = false,
    val chatTimeline: List<ChatTimelineItem> = emptyList(),
    val backgroundCommands: List<BackgroundCommandItem> = SampleStudioData.defaultCommands,
    val terminalLogs: List<String> = SampleStudioData.defaultTerminalLogs,
    val apiKey: String = "",
    val detectedAuthUrl: String? = null,
    val activeDiffForReview: ChatTimelineItem.GitDiffCard? = null
) {
    val activeProject: WorkspaceProject
        get() = projects.firstOrNull { it.id == selectedProjectId } ?: projects.first()
}

object SampleStudioData {
    val defaultModels = listOf(
        AntigravityProcessManager.AgyModelOption(
            id = "gemini-4-argon-medium",
            displayName = "Gemini 4 Argon",
            family = "Gemini 4 Argon",
            tierBadge = "Medium"
        ),
        AntigravityProcessManager.AgyModelOption(
            id = "gemini-4-argon-high",
            displayName = "Gemini 4 Argon",
            family = "Gemini 4 Argon",
            tierBadge = "High"
        ),
        AntigravityProcessManager.AgyModelOption(
            id = "gemini-4-argon-low",
            displayName = "Gemini 4 Argon",
            family = "Gemini 4 Argon",
            tierBadge = "Low"
        ),
        AntigravityProcessManager.AgyModelOption(
            id = "gemini-3.8-flash-high",
            displayName = "Gemini 3.8 Flash",
            family = "Gemini 3.8 Flash",
            tierBadge = "High"
        ),
        AntigravityProcessManager.AgyModelOption(
            id = "gemini-3.8-flash-medium",
            displayName = "Gemini 3.8 Flash",
            family = "Gemini 3.8 Flash",
            tierBadge = "Medium"
        ),
        AntigravityProcessManager.AgyModelOption(
            id = "claude-opus-5-5-medium",
            displayName = "Claude Opus 5.5",
            family = "Claude Opus 5.5",
            tierBadge = "Medium"
        ),
        AntigravityProcessManager.AgyModelOption(
            id = "claude-sonnet-5-5-medium",
            displayName = "Claude Sonnet 5.5",
            family = "Claude Sonnet 5.5",
            tierBadge = "Medium"
        ),
        AntigravityProcessManager.AgyModelOption(
            id = "gpt-oss-120b-medium",
            displayName = "GPT-OSS 120B",
            family = "GPT-OSS 120B",
            tierBadge = "Medium"
        )
    )

    val defaultProjects = listOf(
        WorkspaceProject(
            id = "proj-javakingdom",
            name = "JavaKingdom",
            pathBreadcrumb = "~/projects/JavaKingdom",
            branch = "main",
            isActive = true,
            sessions = listOf(
                ConversationSession(
                    id = "sess-jk-1",
                    title = "Developing A Minecraft K...",
                    updatedAt = "5mo",
                    hasActiveDot = false
                )
            )
        ),
        WorkspaceProject(
            id = "proj-lively-noether",
            name = "lively-noether",
            pathBreadcrumb = "~/projects/lively-noether",
            branch = "main",
            isActive = false,
            sessions = listOf(
                ConversationSession(
                    id = "sess-ln-1",
                    title = "Minecraft Diamond Nugge...",
                    updatedAt = "6mo",
                    hasActiveDot = false
                )
            )
        ),
        WorkspaceProject(
            id = "proj-trademachine",
            name = "TradeMachine",
            pathBreadcrumb = "~/projects/TradeMachine",
            branch = "main",
            isActive = false,
            sessions = listOf(
                ConversationSession(
                    id = "sess-tm-1",
                    title = "Creating Minecraft Bedroc...",
                    updatedAt = "",
                    hasActiveDot = true
                )
            )
        ),
        WorkspaceProject(
            id = "proj-mcpearabicfixer",
            name = "McpeArabicFixer",
            pathBreadcrumb = "~/projects/McpeArabicFixer",
            branch = "main",
            isActive = false,
            sessions = listOf(
                ConversationSession(
                    id = "sess-maf-1",
                    title = "Fixing Minecraft Arabic Tex...",
                    updatedAt = "",
                    hasActiveDot = true
                )
            )
        ),
        WorkspaceProject(
            id = "proj-morexp",
            name = "MoreXp",
            pathBreadcrumb = "~/projects/MoreXp",
            branch = "main",
            isActive = false,
            sessions = listOf(
                ConversationSession(
                    id = "sess-mxp-1",
                    title = "Granting Administrative In...",
                    updatedAt = "",
                    hasActiveDot = true
                )
            )
        ),
        WorkspaceProject(
            id = "proj-backpack",
            name = "pack%281%29-DAu0...",
            pathBreadcrumb = "~/projects/pack-DAu0",
            branch = "main",
            isActive = false,
            sessions = listOf(
                ConversationSession(
                    id = "sess-bp-1",
                    title = "Bypassing Backpack Owner...",
                    updatedAt = "",
                    hasActiveDot = true
                )
            )
        )
    )

    val sampleConversationTimeline = listOf(
        ChatTimelineItem.UserPromptItem(
            id = "msg-1",
            timestamp = "16:20",
            prompt = "Run agy --version and verify workspace setup for JavaKingdom"
        ),
        ChatTimelineItem.AgentThoughtCard(
            id = "msg-2",
            timestamp = "16:20",
            workedSeconds = 21,
            summary = "Worked for 21s",
            steps = listOf(
                "Invoked context.filesDir/runtime/bin/agy --output-format=stream-json",
                "Verified native Bionic arm64-v8a ELF binary v1.4.12",
                "Inspected project files in ~/projects/JavaKingdom"
            ),
            isExpanded = false
        ),
        ChatTimelineItem.GitDiffCard(
            id = "msg-3",
            timestamp = "16:20",
            filePath = "src/main/java/com/javakingdom/KingdomCore.java",
            additions = 30,
            deletions = 6,
            summary = "Updated KingdomCore command registrar and permissions handler",
            patchPreview = """
                @@ -18,6 +18,14 @@ public final class KingdomCore extends JavaPlugin {
                +    @Override
                +    public void onEnable() {
                +        getLogger().info("JavaKingdom initialized via Antigravity CLI v1.4.12");
                +        registerKingdomCommands();
                +    }
            """.trimIndent()
        ),
        ChatTimelineItem.AgentResponseItem(
            id = "msg-4",
            timestamp = "16:21",
            markdownText = "Connected to the real **Antigravity CLI (`agy` v1.4.12 `android_arm64`)** engine.\n\nAll prompts sent from this console execute directly through `context.filesDir/runtime/bin/agy --output-format=stream-json`.",
            tokenUsageLabel = "12,076 tokens • 4.8s"
        )
    )

    val defaultCommands = listOf(
        BackgroundCommandItem(
            id = "cmd-agy",
            command = "agy --output-format=stream-json --model=gemini-4-argon-medium",
            workingDir = "~/projects/JavaKingdom",
            status = CommandStatus.LISTENING,
            durationLabel = "v1.4.12 Ready"
        ),
        BackgroundCommandItem(
            id = "cmd-models",
            command = "agy models",
            workingDir = "~/projects/JavaKingdom",
            status = CommandStatus.COMPLETED,
            durationLabel = "13 models"
        )
    )

    val defaultTerminalLogs = listOf(
        "16:21:01  [agy] Verified arm64-v8a ELF binary: .../files/runtime/bin/antigravity (184 MB)",
        "16:21:02  [agy] Antigravity CLI version: 1.4.12 (GOMEMLIMIT=256MiB | NODE_OPTIONS=--max-old-space-size=256)",
        "16:21:02  [agy models] gemini-4-argon-medium  Gemini 4 Argon (Medium)",
        "16:21:02  [agy models] gemini-3.8-flash-high  Gemini 3.8 Flash (High)",
        "16:21:02  [agy models] claude-opus-5-5-medium Claude Opus 5.5 (Medium)"
    )
}
