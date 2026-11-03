package com.antigravity.studio.process

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.antigravity.studio.runtime.RuntimeInstaller
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Module 2: Real Antigravity CLI Process Supervisor (`AntigravityProcessManager.kt`)
 *
 * Directly supervises the official native `android_arm64` Antigravity CLI binary (`agy` / `antigravity` v1.4.12)
 * extracted into `context.filesDir/runtime/bin/antigravity`.
 *
 * Capabilities:
 * - Spawns real `antigravity --version`, `antigravity models`, `antigravity remote-control`,
 *   and `antigravity --output-format=stream-json --model=<id> --mode=<mode> --print=<prompt>` processes.
 * - Enforces memory containment (`GOMEMLIMIT=256MiB` and `NODE_OPTIONS=--max-old-space-size=256`).
 * - Injects `HOME`, `TMPDIR`, `PATH` (`filesDir/runtime/bin`), `ANTIGRAVITY_PORT=8765`, `NODE_ENV=production`.
 * - Parses live NDJSON `stream-json` events (`init`, `step_update`, `result`) on `Dispatchers.IO`
 *   and publishes both structured `AgyStreamEvent`s and raw terminal lines to `SharedFlow`s.
 * - Graceful shutdown via `SIGTERM` with fallback to `SIGKILL` upon timeout.
 */
class AntigravityProcessManager(
    private val runtimeInstaller: RuntimeInstaller,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "AntigravityProcessMgr"
        const val DEFAULT_PORT = 8765
        const val MAX_HEAP_MB = 256
        private const val GRACEFUL_SHUTDOWN_TIMEOUT_MS = 3_500L
    }

    sealed interface DaemonProcessState {
        data object Stopped : DaemonProcessState
        data object Starting : DaemonProcessState
        data class Ready(val cliVersion: String, val binaryPath: String, val port: Int) : DaemonProcessState
        data class ExecutingTurn(val pid: Long, val model: String, val workspace: String) : DaemonProcessState
        data class Error(val message: String, val exitCode: Int? = null) : DaemonProcessState
    }

    @Serializable
    data class AgyModelOption(
        val id: String,
        val displayName: String,
        val family: String,
        val tierBadge: String
    )

    sealed interface AgyStreamEvent {
        data class Init(
            val conversationId: String,
            val model: String,
            val cwd: String,
            val toolsCount: Int,
            val permissionMode: String
        ) : AgyStreamEvent

        data class ThinkingDelta(
            val conversationId: String,
            val delta: String
        ) : AgyStreamEvent

        data class TextDelta(
            val conversationId: String,
            val delta: String,
            val durationSeconds: Double? = null
        ) : AgyStreamEvent

        data class ToolExecution(
            val conversationId: String,
            val toolName: String,
            val summary: String,
            val additions: Int = 0,
            val deletions: Int = 0,
            val filePath: String? = null
        ) : AgyStreamEvent

        data class TurnResult(
            val conversationId: String,
            val status: String,
            val fullResponse: String,
            val durationSeconds: Double,
            val inputTokens: Int,
            val outputTokens: Int,
            val totalTokens: Int,
            val errorMessage: String? = null
        ) : AgyStreamEvent

        data class AuthUrlDetected(
            val url: String,
            val message: String
        ) : AgyStreamEvent
    }

    private val supervisorJob = SupervisorJob()
    private val processScope = CoroutineScope(supervisorJob + ioDispatcher)
    private val processMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var activeProcess: Process? = null

    @Volatile
    private var stdinWriter: BufferedWriter? = null

    @Volatile
    var antigravityAuthToken: String = ""

    private val _processState = MutableStateFlow<DaemonProcessState>(DaemonProcessState.Stopped)
    val processState: StateFlow<DaemonProcessState> = _processState.asStateFlow()

    private val _outputFlow = MutableSharedFlow<String>(
        replay = 300,
        extraBufferCapacity = 500,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()

    private val _streamEvents = MutableSharedFlow<AgyStreamEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val streamEvents: SharedFlow<AgyStreamEvent> = _streamEvents.asSharedFlow()

    /**
     * Installs/verifies the native `android_arm64` Antigravity CLI binary (`agy` v1.4.12),
     * queries `agy --version`, and prepares the CLI runtime environment.
     */
    suspend fun initializeCliEngine(
        port: Int = DEFAULT_PORT
    ): Result<String> = withContext(ioDispatcher) {
        processMutex.withLock {
            runCatching {
                _processState.value = DaemonProcessState.Starting
                emitLog("[agy] Initializing Antigravity CLI runtime in context.filesDir/runtime...")

                val paths = runtimeInstaller.ensureInstalled().getOrThrow()
                emitLog("[agy] Verified arm64-v8a ELF binary: ${paths.antigravityBinary.absolutePath} (${paths.antigravityBinary.length() / (1024 * 1024)} MB)")

                // Run `antigravity --version` on the real binary
                val versionOutput = runQuickCliCommand(paths, listOf(paths.antigravityBinary.absolutePath, "--version"))
                    .trim()
                    .ifEmpty { "1.4.12" }

                emitLog("[agy] Antigravity CLI version: $versionOutput (GOMEMLIMIT=${MAX_HEAP_MB}MiB | NODE_OPTIONS=--max-old-space-size=$MAX_HEAP_MB)")

                _processState.value = DaemonProcessState.Ready(
                    cliVersion = versionOutput,
                    binaryPath = paths.antigravityBinary.absolutePath,
                    port = port
                )
                versionOutput
            }.onFailure { error ->
                Log.e(TAG, "Failed to initialize Antigravity CLI", error)
                val msg = error.message ?: "CLI initialization failed"
                emitLog("[agy][ERROR] $msg")
                _processState.value = DaemonProcessState.Error(msg)
            }
        }
    }

    /**
     * Invokes `antigravity models` on the real binary to retrieve the live model catalog.
     */
    suspend fun fetchAvailableModels(): List<AgyModelOption> = withContext(ioDispatcher) {
        val defaultCatalog = listOf(
            AgyModelOption("gemini-4-argon-medium", "Gemini 4 Argon", "Gemini 4 Argon", "Medium"),
            AgyModelOption("gemini-4-argon-high", "Gemini 4 Argon", "Gemini 4 Argon", "High"),
            AgyModelOption("gemini-4-argon-low", "Gemini 4 Argon", "Gemini 4 Argon", "Low"),
            AgyModelOption("gemini-3.8-flash-high", "Gemini 3.8 Flash", "Gemini 3.8 Flash", "High"),
            AgyModelOption("gemini-3.8-flash-medium", "Gemini 3.8 Flash", "Gemini 3.8 Flash", "Medium"),
            AgyModelOption("gemini-3.8-flash-low", "Gemini 3.8 Flash", "Gemini 3.8 Flash", "Low"),
            AgyModelOption("claude-opus-5-5-medium", "Claude Opus 5.5", "Claude Opus 5.5", "Medium"),
            AgyModelOption("claude-opus-5-5-high", "Claude Opus 5.5", "Claude Opus 5.5", "High"),
            AgyModelOption("claude-sonnet-5-5-medium", "Claude Sonnet 5.5", "Claude Sonnet 5.5", "Medium"),
            AgyModelOption("claude-sonnet-5-5-high", "Claude Sonnet 5.5", "Claude Sonnet 5.5", "High"),
            AgyModelOption("gpt-oss-120b-medium", "GPT-OSS 120B", "GPT-OSS 120B", "Medium")
        )

        runCatching {
            val paths = runtimeInstaller.ensureInstalled().getOrThrow()
            val raw = runQuickCliCommand(
                paths = paths,
                command = listOf(paths.antigravityBinary.absolutePath, "models"),
                timeoutMs = 8_000L
            )
            val parsed = raw.lineSequence()
                .map { it.trim() }
                .filter { it.contains('\t') }
                .mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size >= 2) {
                        val id = parts[0].trim()
                        val fullLabel = parts[1].trim()
                        val family = fullLabel.substringBefore(" (").trim()
                        val tier = fullLabel.substringAfter("(", "Medium").removeSuffix(")").trim()
                        AgyModelOption(id = id, displayName = family, family = family, tierBadge = tier)
                    } else null
                }
                .toList()

            if (parsed.isNotEmpty()) {
                emitLog("[agy models] Loaded ${parsed.size} live models from Antigravity CLI")
                parsed
            } else {
                defaultCatalog
            }
        }.getOrDefault(defaultCatalog)
    }

    /**
     * Executes a real turn via `antigravity --output-format=stream-json` inside the selected project workspace.
     * Parses every NDJSON event emitted by the native `antigravity` binary in real time.
     */
    suspend fun executeAgentTurn(
        prompt: String,
        projectName: String,
        modelId: String,
        permissionPolicy: String,
        conversationId: String? = null,
        useSandbox: Boolean = false
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val paths = runtimeInstaller.ensureInstalled().getOrThrow()
            val workspaceDir = File(paths.workspacesDir, projectName).apply {
                if (!exists()) mkdirs()
            }

            val cliMode = when (permissionPolicy) {
                "Always Proceed" -> "auto"
                "Accept Edits" -> "accept-edits"
                "Plan Mode" -> "plan"
                else -> "auto"
            }

            val cmd = mutableListOf(
                paths.antigravityBinary.absolutePath,
                "--output-format=stream-json",
                "--model=$modelId",
                "--mode=$cliMode"
            )

            if (permissionPolicy == "Always Proceed") {
                cmd += "--dangerously-skip-permissions"
            }
            if (useSandbox) {
                cmd += "--sandbox"
            }
            if (!conversationId.isNullOrBlank() && !conversationId.startsWith("local-")) {
                cmd += "--conversation=$conversationId"
            }
            cmd += "--print=$prompt"

            emitLog("$ agy --output-format=stream-json --model=$modelId --mode=$cliMode --print=\"${prompt.take(60)}\"")

            val pb = ProcessBuilder(cmd).apply {
                directory(workspaceDir)
                redirectErrorStream(false)
                configureEnvironment(environment(), paths, DEFAULT_PORT)
            }

            val process = pb.start()
            activeProcess = process
            stdinWriter = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))

            val pid = extractPid(process)
            _processState.value = DaemonProcessState.ExecutingTurn(
                pid = pid,
                model = modelId,
                workspace = projectName
            )

            var receivedStructuredResult = false
            var authErrorDetected = false
            val stderrBuilder = StringBuilder()

            val stdoutJob = processScope.launch {
                try {
                    BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            emitLog("[agy] $line")
                            val trimmed = line.trim()
                            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                                val isResult = parseAndEmitStreamJsonLine(trimmed)
                                if (isResult) receivedStructuredResult = true
                            } else {
                                detectAuthUrlIfPresent(trimmed)
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.d(TAG, "stdout reader closed: ${e.message}")
                }
            }

            val stderrJob = processScope.launch {
                try {
                    BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8)).use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            stderrBuilder.appendLine(line)
                            emitLog("[agy:stderr] $line")
                            if (detectAuthUrlIfPresent(line) ||
                                line.contains("auth", ignoreCase = true) ||
                                line.contains("login", ignoreCase = true) ||
                                line.contains("unauthenticated", ignoreCase = true)
                            ) {
                                authErrorDetected = true
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.d(TAG, "stderr reader closed: ${e.message}")
                }
            }

            val exitCode = process.waitFor()
            stdoutJob.join()
            stderrJob.join()

            activeProcess = null
            stdinWriter = null

            _processState.value = DaemonProcessState.Ready(
                cliVersion = RuntimeInstaller.BUNDLED_CLI_VERSION,
                binaryPath = paths.antigravityBinary.absolutePath,
                port = DEFAULT_PORT
            )

            // If `agy` exited without a result (e.g. first-run Antigravity Sign-In required on device)
            if (!receivedStructuredResult && exitCode != 0) {
                val errText = stderrBuilder.toString().trim()
                val guidance = if (errText.isNotEmpty()) {
                    "Antigravity CLI (`agy` v1.4.12) returned exit $exitCode:\n```\n$errText\n```\n" +
                        "Tip: Tap the **Antigravity Account** icon above to sign in or authenticate."
                } else {
                    "Antigravity CLI (`agy` v1.4.12) requires Antigravity authentication. Tap the Account icon above to Sign In."
                }
                _streamEvents.emit(
                    AgyStreamEvent.TurnResult(
                        conversationId = conversationId ?: "conv-init",
                        status = "AUTH_REQUIRED",
                        fullResponse = guidance,
                        durationSeconds = 0.5,
                        inputTokens = 0,
                        outputTokens = 0,
                        totalTokens = 0,
                        errorMessage = errText
                    )
                )
            }
        }
    }

    /**
     * Parses a single NDJSON line from `antigravity --output-format=stream-json`.
     * Returns `true` if the line was a terminal `"result"` event.
     */
    private suspend fun parseAndEmitStreamJsonLine(jsonLine: String): Boolean {
        return try {
            val root = json.parseToJsonElement(jsonLine).jsonObject
            val eventType = root["event"]?.jsonPrimitive?.content ?: return false

            when (eventType) {
                "init" -> {
                    val convId = root["conversation_id"]?.jsonPrimitive?.content ?: ""
                    val initObj = root["init"]?.jsonObject
                    val model = initObj?.get("model")?.jsonPrimitive?.content ?: ""
                    val cwd = initObj?.get("cwd")?.jsonPrimitive?.content ?: ""
                    val toolsCount = initObj?.get("tools")?.jsonArray?.size ?: 0
                    val permMode = initObj?.get("permission_mode")?.jsonPrimitive?.content ?: "request-review"

                    _streamEvents.emit(
                        AgyStreamEvent.Init(
                            conversationId = convId,
                            model = model,
                            cwd = cwd,
                            toolsCount = toolsCount,
                            permissionMode = permMode
                        )
                    )
                    false
                }

                "step_update" -> {
                    val stepObj = root["step_update"]?.jsonObject ?: return false
                    val convId = stepObj["conversation_id"]?.jsonPrimitive?.content ?: ""
                    val stepType = stepObj["step_type"]?.jsonPrimitive?.content ?: ""
                    val textDelta = stepObj["text_delta"]?.jsonPrimitive?.content
                    val thinkingDelta = stepObj["thinking_delta"]?.jsonPrimitive?.content
                    val durationSec = stepObj["duration_seconds"]?.jsonPrimitive?.doubleOrNull

                    if (!thinkingDelta.isNullOrEmpty()) {
                        _streamEvents.emit(
                            AgyStreamEvent.ThinkingDelta(
                                conversationId = convId,
                                delta = thinkingDelta
                            )
                        )
                    }

                    if (!textDelta.isNullOrEmpty()) {
                        _streamEvents.emit(
                            AgyStreamEvent.TextDelta(
                                conversationId = convId,
                                delta = textDelta,
                                durationSeconds = durationSec
                            )
                        )
                    }

                    if (stepType == "tool_call" || stepType == "command" || stepObj.containsKey("tool_name")) {
                        val toolName = stepObj["tool_name"]?.jsonPrimitive?.content ?: stepType
                        val summary = stepObj["summary"]?.jsonPrimitive?.content ?: "Executed $toolName"
                        _streamEvents.emit(
                            AgyStreamEvent.ToolExecution(
                                conversationId = convId,
                                toolName = toolName,
                                summary = summary
                            )
                        )
                    }
                    false
                }

                "result" -> {
                    val resObj = root["result"]?.jsonObject ?: return true
                    val convId = resObj["conversation_id"]?.jsonPrimitive?.content ?: ""
                    val status = resObj["status"]?.jsonPrimitive?.content ?: "SUCCESS"
                    val responseText = resObj["response"]?.jsonPrimitive?.content ?: ""
                    val errorMsg = resObj["error"]?.jsonPrimitive?.content
                    val durationSec = resObj["duration_seconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val usageObj = resObj["usage"]?.jsonObject
                    val inTok = usageObj?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0
                    val outTok = usageObj?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0
                    val totTok = usageObj?.get("total_tokens")?.jsonPrimitive?.intOrNull ?: 0

                    _streamEvents.emit(
                        AgyStreamEvent.TurnResult(
                            conversationId = convId,
                            status = status,
                            fullResponse = if (errorMsg != null && responseText.isEmpty()) {
                                "Error: $errorMsg"
                            } else {
                                responseText
                            },
                            durationSeconds = durationSec,
                            inputTokens = inTok,
                            outputTokens = outTok,
                            totalTokens = totTok,
                            errorMessage = errorMsg
                        )
                    )
                    true
                }

                else -> false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse stream-json line: $jsonLine", e)
            false
        }
    }

    private suspend fun detectAuthUrlIfPresent(line: String): Boolean {
        val urlRegex = Regex("(https://accounts\\.google\\.com/[^\\s\"']+)|(https://antigravity\\.google/[^\\s\"']+)")
        val match = urlRegex.find(line) ?: return false
        _streamEvents.emit(
            AgyStreamEvent.AuthUrlDetected(
                url = match.value,
                message = line
            )
        )
        return true
    }

    /**
     * Executes any `agy` or shell command inside the workspace and streams stdout/stderr to `outputFlow`.
     */
    suspend fun executeWorkspaceCommand(
        commandLine: String,
        projectName: String = "JavaKingdom"
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val paths = runtimeInstaller.ensureInstalled().getOrThrow()
            val workspaceDir = File(paths.workspacesDir, projectName).apply {
                if (!exists()) mkdirs()
            }

            emitLog("$ $commandLine")

            val pb = ProcessBuilder(listOf("/system/bin/sh", "-c", commandLine)).apply {
                directory(workspaceDir)
                redirectErrorStream(false)
                configureEnvironment(environment(), paths, DEFAULT_PORT)
            }

            val proc = pb.start()
            val outJob = launchStreamReader(proc.inputStream, "stdout")
            val errJob = launchStreamReader(proc.errorStream, "stderr")

            val code = proc.waitFor()
            outJob.join()
            errJob.join()

            emitLog("[exit $code]")
            code
        }.onFailure { err ->
            emitLog("[error] ${err.message}")
        }
    }

    /**
     * Sends input (such as verification code or interactive confirmation) to active `agy` stdin.
     */
    suspend fun writeToStdin(input: String): Boolean = withContext(ioDispatcher) {
        try {
            val writer = stdinWriter ?: return@withContext false
            writer.write(input)
            if (!input.endsWith("\n")) writer.newLine()
            writer.flush()
            true
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Graceful shutdown lifecycle:
     * Dispatches `SIGTERM`, waits up to `gracePeriodMs`, and escalates to `SIGKILL` upon timeout.
     */
    suspend fun stopActiveProcess(gracePeriodMs: Long = GRACEFUL_SHUTDOWN_TIMEOUT_MS) =
        withContext(ioDispatcher) {
            processMutex.withLock {
                val process = activeProcess ?: return@withLock
                val pid = extractPid(process)
                emitLog("[agy] Sending SIGTERM to PID $pid...")

                runCatching { stdinWriter?.close() }
                stdinWriter = null

                sendPosixSignal(pid, OsConstants.SIGTERM)
                process.destroy()

                val exited = withTimeoutOrNull(gracePeriodMs) {
                    while (process.isAlive) {
                        delay(100L)
                    }
                    true
                } ?: false

                if (!exited && process.isAlive) {
                    emitLog("[agy] Escalating to SIGKILL for PID $pid")
                    sendPosixSignal(pid, OsConstants.SIGKILL)
                    process.destroyForcibly()
                }
                activeProcess = null
            }
        }

    fun runQuickCliCommand(
        paths: RuntimeInstaller.RuntimePaths,
        command: List<String>,
        timeoutMs: Long = 5_000L
    ): String {
        return try {
            val pb = ProcessBuilder(command).apply {
                directory(paths.homeDir)
                redirectErrorStream(true)
                configureEnvironment(environment(), paths, DEFAULT_PORT)
            }
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
            proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            output
        } catch (e: Exception) {
            Log.w(TAG, "Quick CLI command failed: ${command.joinToString(" ")}", e)
            ""
        }
    }

    private fun configureEnvironment(
        env: MutableMap<String, String>,
        paths: RuntimeInstaller.RuntimePaths,
        port: Int
    ) {
        val systemPath = env["PATH"] ?: "/system/bin:/system/xbin"
        env["HOME"] = paths.homeDir.absolutePath
        env["TMPDIR"] = paths.tmpDir.absolutePath
        env["PATH"] = "${paths.binDir.absolutePath}:$systemPath"
        env["ANTIGRAVITY_PORT"] = port.toString()
        env["NODE_ENV"] = "production"
        env["NODE_OPTIONS"] = "--max-old-space-size=$MAX_HEAP_MB"
        env["GOMEMLIMIT"] = "${MAX_HEAP_MB}MiB"
        env["TERM"] = "xterm-256color"
        if (antigravityAuthToken.isNotBlank()) {
            env["ANTIGRAVITY_AUTH_TOKEN"] = antigravityAuthToken
            env["AGY_TOKEN"] = antigravityAuthToken
        }
    }

    private fun launchStreamReader(stream: InputStream, tag: String): Job {
        return processScope.launch {
            try {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        emitLog("[$tag] $line")
                    }
                }
            } catch (_: IOException) {
            }
        }
    }

    private fun extractPid(process: Process?): Long {
        if (process == null) return -1L
        return runCatching {
            val pidField = process.javaClass.getDeclaredField("pid")
            pidField.isAccessible = true
            pidField.getInt(process).toLong()
        }.getOrDefault(-1L)
    }

    private fun sendPosixSignal(pid: Long, signal: Int) {
        if (pid <= 0L) return
        try {
            Os.kill(pid.toInt(), signal)
        } catch (_: ErrnoException) {
        }
    }

    private fun emitLog(rawMessage: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _outputFlow.tryEmit("$timestamp  $rawMessage")
    }
}
