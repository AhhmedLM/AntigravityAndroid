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
 * Module 2: CLI Process Supervisor (`AntigravityProcessManager.kt`)
 *
 * Manages the lifecycle of the embedded Node.js CLI daemon (`antigravity serve --headless`)
 * and interactive workspace commands via `ProcessBuilder`.
 *
 * Key Architectural Features:
 * - Strict Memory Containment: Enforces `NODE_OPTIONS=--max-old-space-size=256` and explicit CLI flag
 *   `--max-old-space-size=256` to survive comfortably on 6GB RAM Android devices.
 * - Environment Variable Injection: Configures `HOME`, `TMPDIR`, prepends `filesDir/runtime/bin`
 *   to `PATH`, sets `ANTIGRAVITY_PORT=8765`, and `NODE_ENV=production`.
 * - Non-blocking Asynchronous I/O: Streams `stdout` and `stderr` concurrently on `Dispatchers.IO`,
 *   publishing live lines to a reactive `SharedFlow<String>` (`outputFlow`).
 * - Graceful Shutdown Lifecycle: Dispatches POSIX `SIGTERM` (`Process.destroy()` + `Os.kill(pid, SIGTERM)`),
 *   waits for a configurable grace period, and falls back to `SIGKILL` (`destroyForcibly()`).
 */
class AntigravityProcessManager(
    private val runtimeInstaller: RuntimeInstaller,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "AntigravityProcessMgr"
        const val DEFAULT_PORT = 8765
        const val MAX_OLD_SPACE_SIZE_MB = 256
        private const val GRACEFUL_SHUTDOWN_TIMEOUT_MS = 3_500L
    }

    sealed interface DaemonProcessState {
        data object Stopped : DaemonProcessState
        data object Starting : DaemonProcessState
        data class Running(val pid: Long, val port: Int, val workspacePath: String) : DaemonProcessState
        data class ExecutingTask(val pid: Long, val port: Int, val activeCommand: String) : DaemonProcessState
        data class Error(val message: String, val exitCode: Int? = null) : DaemonProcessState
    }

    private val supervisorJob = SupervisorJob()
    private val processScope = CoroutineScope(supervisorJob + ioDispatcher)
    private val processMutex = Mutex()

    @Volatile
    private var activeProcess: Process? = null

    @Volatile
    private var stdinWriter: BufferedWriter? = null

    private var stdoutReaderJob: Job? = null
    private var stderrReaderJob: Job? = null
    private var monitorJob: Job? = null

    private val _processState = MutableStateFlow<DaemonProcessState>(DaemonProcessState.Stopped)
    val processState: StateFlow<DaemonProcessState> = _processState.asStateFlow()

    private val _outputFlow = MutableSharedFlow<String>(
        replay = 250,
        extraBufferCapacity = 500,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()

    /**
     * Spawns the persistent headless Node.js CLI daemon (`antigravity serve --port 8765`)
     * inside `context.filesDir/runtime/bin`.
     */
    suspend fun startDaemon(
        workspaceName: String = "AntigravityAndroid",
        port: Int = DEFAULT_PORT
    ): Result<Long> = withContext(ioDispatcher) {
        processMutex.withLock {
            runCatching {
                if (activeProcess?.isAlive == true) {
                    val existingPid = extractPid(activeProcess)
                    emitLog("[Supervisor] Daemon already active (PID=$existingPid, port=$port)")
                    return@runCatching existingPid
                }

                _processState.value = DaemonProcessState.Starting
                emitLog("[Supervisor] Verifying runtime binaries in context.filesDir/runtime...")

                val paths = runtimeInstaller.ensureInstalled().getOrThrow()
                val workspaceDir = File(paths.workspacesDir, workspaceName).apply {
                    if (!exists()) mkdirs()
                }

                val command = buildDaemonCommand(paths, port)
                val processBuilder = ProcessBuilder(command).apply {
                    directory(workspaceDir)
                    redirectErrorStream(false)
                    configureEnvironment(environment(), paths, port)
                }

                emitLog("[Supervisor] Spawning: ${command.joinToString(" ")}")
                emitLog("[Supervisor] ENV: HOME=${paths.homeDir.absolutePath} | ANTIGRAVITY_PORT=$port | NODE_ENV=production | HEAP=${MAX_OLD_SPACE_SIZE_MB}MB")

                val process = processBuilder.start()
                activeProcess = process
                stdinWriter = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))

                val pid = extractPid(process)
                _processState.value = DaemonProcessState.Running(
                    pid = pid,
                    port = port,
                    workspacePath = workspaceDir.absolutePath
                )

                // Launch non-blocking coroutine stream readers for stdout & stderr
                stdoutReaderJob = launchStreamReader(process.inputStream, "STDOUT")
                stderrReaderJob = launchStreamReader(process.errorStream, "STDERR")
                monitorJob = launchProcessExitMonitor(process, pid, port)

                pid
            }.onFailure { error ->
                Log.e(TAG, "Failed to start Antigravity CLI daemon", error)
                val errMsg = error.message ?: "Process spawn error"
                emitLog("[Supervisor][ERROR] $errMsg")
                _processState.value = DaemonProcessState.Error(errMsg)
            }
        }
    }

    /**
     * Executes an asynchronous shell/CLI command inside the active workspace environment
     * and streams every stdout/stderr line into `outputFlow`.
     */
    suspend fun executeWorkspaceCommand(
        commandLine: String,
        workspaceName: String = "AntigravityAndroid"
    ): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val paths = runtimeInstaller.ensureInstalled().getOrThrow()
            val workspaceDir = File(paths.workspacesDir, workspaceName).apply {
                if (!exists()) mkdirs()
            }

            val currentPid = extractPid(activeProcess)
            _processState.value = DaemonProcessState.ExecutingTask(
                pid = currentPid,
                port = DEFAULT_PORT,
                activeCommand = commandLine
            )

            emitLog("$ $commandLine")

            val shellExecutable = if (File("/system/bin/sh").exists()) {
                "/system/bin/sh"
            } else {
                paths.busyboxBinary.absolutePath
            }

            val pb = ProcessBuilder(listOf(shellExecutable, "-c", commandLine)).apply {
                directory(workspaceDir)
                redirectErrorStream(false)
                configureEnvironment(environment(), paths, DEFAULT_PORT)
            }

            val cmdProcess = pb.start()
            val outJob = launchStreamReader(cmdProcess.inputStream, "CMD")
            val errJob = launchStreamReader(cmdProcess.errorStream, "CMD-ERR")

            val exitCode = cmdProcess.waitFor()
            outJob.join()
            errJob.join()

            emitLog("[Command completed with exit code $exitCode]")

            if (activeProcess?.isAlive == true) {
                _processState.value = DaemonProcessState.Running(
                    pid = extractPid(activeProcess),
                    port = DEFAULT_PORT,
                    workspacePath = workspaceDir.absolutePath
                )
            }

            exitCode
        }.onFailure { error ->
            emitLog("[CMD-ERROR] ${error.message}")
        }
    }

    /**
     * Sends a raw JSON-RPC or CLI command line to the running daemon's standard input.
     */
    suspend fun writeToStdin(payload: String): Boolean = withContext(ioDispatcher) {
        try {
            val writer = stdinWriter ?: return@withContext false
            writer.write(payload)
            if (!payload.endsWith("\n")) {
                writer.newLine()
            }
            writer.flush()
            true
        } catch (e: IOException) {
            Log.w(TAG, "Failed writing to daemon stdin", e)
            false
        }
    }

    /**
     * Graceful shutdown lifecycle:
     * 1. Sends POSIX `SIGTERM` (`Os.kill(pid, SIGTERM)` and `process.destroy()`).
     * 2. Awaits termination up to `gracePeriodMs`.
     * 3. Falls back to `SIGKILL` (`Os.kill(pid, SIGKILL)` and `process.destroyForcibly()`) upon timeout.
     */
    suspend fun stopDaemon(gracePeriodMs: Long = GRACEFUL_SHUTDOWN_TIMEOUT_MS) =
        withContext(ioDispatcher) {
            processMutex.withLock {
                val process = activeProcess ?: run {
                    _processState.value = DaemonProcessState.Stopped
                    return@withLock
                }

                val pid = extractPid(process)
                emitLog("[Supervisor] Initiating graceful shutdown (SIGTERM) for PID=$pid...")

                try {
                    stdinWriter?.close()
                } catch (_: IOException) {
                } finally {
                    stdinWriter = null
                }

                // Step 1: Send SIGTERM
                sendPosixSignal(pid, OsConstants.SIGTERM)
                process.destroy()

                // Step 2: Await graceful exit within timeout
                val exitedGracefully = withTimeoutOrNull(gracePeriodMs) {
                    while (process.isAlive) {
                        delay(100L)
                    }
                    true
                } ?: false

                // Step 3: Fallback to SIGKILL if still alive
                if (!exitedGracefully && process.isAlive) {
                    emitLog("[Supervisor] Grace period (${gracePeriodMs}ms) expired; escalating to SIGKILL for PID=$pid")
                    sendPosixSignal(pid, OsConstants.SIGKILL)
                    process.destroyForcibly()
                    runCatching { process.waitFor(1_000L, TimeUnit.MILLISECONDS) }
                } else {
                    emitLog("[Supervisor] Daemon PID=$pid terminated gracefully via SIGTERM.")
                }

                stdoutReaderJob?.cancel()
                stderrReaderJob?.cancel()
                monitorJob?.cancel()

                activeProcess = null
                _processState.value = DaemonProcessState.Stopped
            }
        }

    private fun buildDaemonCommand(
        paths: RuntimeInstaller.RuntimePaths,
        port: Int
    ): List<String> {
        // Execute via /system/bin/sh if the node binary is a POSIX shell wrapper, or direct ELF binary execution
        val nodeHeader = runCatching {
            paths.nodeBinary.inputStream().use { stream ->
                val magic = ByteArray(4)
                val read = stream.read(magic)
                if (read == 4) magic else ByteArray(0)
            }
        }.getOrDefault(ByteArray(0))

        val isElfBinary = nodeHeader.size == 4 &&
            nodeHeader[0] == 0x7F.toByte() &&
            nodeHeader[1] == 'E'.code.toByte() &&
            nodeHeader[2] == 'L'.code.toByte() &&
            nodeHeader[3] == 'F'.code.toByte()

        return if (isElfBinary) {
            listOf(
                paths.nodeBinary.absolutePath,
                "--max-old-space-size=$MAX_OLD_SPACE_SIZE_MB",
                paths.serverScript.absolutePath,
                "serve",
                "--port",
                port.toString(),
                "--headless"
            )
        } else {
            listOf(
                "/system/bin/sh",
                paths.antigravityCliBinary.absolutePath,
                "serve",
                "--port",
                port.toString(),
                "--headless"
            )
        }
    }

    private fun configureEnvironment(
        env: MutableMap<String, String>,
        paths: RuntimeInstaller.RuntimePaths,
        port: Int
    ) {
        val existingPath = env["PATH"] ?: "/system/bin:/system/xbin"
        env["HOME"] = paths.homeDir.absolutePath
        env["TMPDIR"] = paths.tmpDir.absolutePath
        env["PATH"] = "${paths.binDir.absolutePath}:$existingPath"
        env["ANTIGRAVITY_PORT"] = port.toString()
        env["NODE_ENV"] = "production"
        env["NODE_OPTIONS"] = "--max-old-space-size=$MAX_OLD_SPACE_SIZE_MB"
        env["TERM"] = "xterm-256color"
    }

    private fun launchStreamReader(stream: InputStream, streamTag: String): Job {
        return processScope.launch {
            try {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        emitLog("[$streamTag] $line")
                    }
                }
            } catch (e: IOException) {
                if (isActive) {
                    Log.d(TAG, "Stream $streamTag closed: ${e.message}")
                }
            }
        }
    }

    private fun launchProcessExitMonitor(process: Process, pid: Long, port: Int): Job {
        return processScope.launch {
            val exitCode = runCatching { process.waitFor() }.getOrDefault(-1)
            emitLog("[Supervisor] Process PID=$pid exited with status=$exitCode")
            if (_processState.value !is DaemonProcessState.Stopped) {
                if (exitCode == 0 || exitCode == 143) {
                    _processState.value = DaemonProcessState.Stopped
                } else {
                    _processState.value = DaemonProcessState.Error(
                        message = "Daemon exited unexpectedly (code $exitCode) on port $port",
                        exitCode = exitCode
                    )
                }
            }
        }
    }

    private fun extractPid(process: Process?): Long {
        if (process == null) return -1L
        return runCatching {
            // Reflection fallback for Android API 29-35 java.lang.UNIXProcess.pid
            val pidField = process.javaClass.getDeclaredField("pid")
            pidField.isAccessible = true
            pidField.getInt(process).toLong()
        }.getOrElse {
            runCatching {
                val pidMethod = Process::class.java.getMethod("pid")
                (pidMethod.invoke(process) as? Long) ?: -1L
            }.getOrDefault(-1L)
        }
    }

    private fun sendPosixSignal(pid: Long, signal: Int) {
        if (pid <= 0L) return
        try {
            Os.kill(pid.toInt(), signal)
        } catch (errno: ErrnoException) {
            Log.d(TAG, "Os.kill(pid=$pid, sig=$signal) returned errno=${errno.errno}")
        }
    }

    private fun emitLog(rawMessage: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        _outputFlow.tryEmit("$timestamp  $rawMessage")
    }
}
