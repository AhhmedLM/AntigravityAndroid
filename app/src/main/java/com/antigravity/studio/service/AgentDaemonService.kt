package com.antigravity.studio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.antigravity.studio.MainActivity
import com.antigravity.studio.process.AntigravityProcessManager
import com.antigravity.studio.runtime.RuntimeInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Module 3: Persistent Background Service (`AgentDaemonService.kt`)
 *
 * Hosts the native `android_arm64` Antigravity CLI (`agy` v1.4.12) runtime inside an
 * Android `ForegroundService` configured with `foregroundServiceType="dataSync"` and `START_STICKY`.
 */
class AgentDaemonService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "antigravity_daemon_channel"
        private const val NOTIFICATION_ID = 8765

        const val ACTION_START_DAEMON = "com.antigravity.studio.action.START_DAEMON"
        const val ACTION_STOP_DAEMON = "com.antigravity.studio.action.STOP_DAEMON"

        fun startService(context: Context) {
            val intent = Intent(context, AgentDaemonService::class.java).apply {
                action = ACTION_START_DAEMON
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    enum class EngineStatus(val title: String, val detail: String) {
        STARTING("Starting Antigravity CLI...", "Extracting native arm64-v8a agy v1.4.12 binary"),
        LISTENING("Listening on port 8765", "Antigravity CLI v1.4.12 Ready (256MB Heap Cap)"),
        EXECUTING_TASK("Executing Agent Turn", "Streaming live NDJSON events from agy"),
        ERROR("Engine Error", "Antigravity CLI process reported an issue")
    }

    inner class LocalBinder : Binder() {
        fun getService(): AgentDaemonService = this@AgentDaemonService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var runtimeInstaller: RuntimeInstaller
        private set

    lateinit var processManager: AntigravityProcessManager
        private set

    private val _engineStatus = MutableStateFlow(EngineStatus.STARTING)
    val engineStatus: StateFlow<EngineStatus> = _engineStatus.asStateFlow()

    val streamEvents: SharedFlow<AntigravityProcessManager.AgyStreamEvent>
        get() = processManager.streamEvents

    val terminalLogs: SharedFlow<String>
        get() = processManager.outputFlow

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        runtimeInstaller = RuntimeInstaller(applicationContext)
        processManager = AntigravityProcessManager(runtimeInstaller)

        promoteToForeground(EngineStatus.STARTING)
        observeProcessState()
        bootCliEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_DAEMON -> {
                serviceScope.launch {
                    processManager.stopActiveProcess()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            else -> {
                if (processManager.processState.value is AntigravityProcessManager.DaemonProcessState.Stopped) {
                    bootCliEngine()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        serviceScope.launch {
            processManager.stopActiveProcess()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun bootCliEngine() {
        serviceScope.launch {
            updateEngineStatus(EngineStatus.STARTING, "Unpacking official arm64-v8a agy v1.4.12 binary...")
            processManager.initializeCliEngine(AntigravityProcessManager.DEFAULT_PORT)
                .onSuccess { version ->
                    updateEngineStatus(
                        EngineStatus.LISTENING,
                        "Antigravity CLI v$version active • Port ${AntigravityProcessManager.DEFAULT_PORT}"
                    )
                }
                .onFailure { err ->
                    updateEngineStatus(
                        EngineStatus.ERROR,
                        err.message ?: "Failed to initialize Antigravity CLI"
                    )
                }
        }
    }

    private fun observeProcessState() {
        serviceScope.launch {
            processManager.processState.collect { state ->
                when (state) {
                    is AntigravityProcessManager.DaemonProcessState.Starting -> {
                        updateEngineStatus(EngineStatus.STARTING, "Initializing agy CLI runtime...")
                    }
                    is AntigravityProcessManager.DaemonProcessState.Ready -> {
                        updateEngineStatus(
                            EngineStatus.LISTENING,
                            "Antigravity CLI v${state.cliVersion} Ready • Port ${state.port}"
                        )
                    }
                    is AntigravityProcessManager.DaemonProcessState.ExecutingTurn -> {
                        updateEngineStatus(
                            EngineStatus.EXECUTING_TASK,
                            "Running ${state.model} in ${state.workspace} (PID ${state.pid})"
                        )
                    }
                    is AntigravityProcessManager.DaemonProcessState.Error -> {
                        updateEngineStatus(EngineStatus.ERROR, state.message)
                    }
                    is AntigravityProcessManager.DaemonProcessState.Stopped -> Unit
                }
            }
        }
    }

    fun dispatchPromptToAgy(
        prompt: String,
        projectName: String,
        modelId: String,
        permissionPolicy: String,
        conversationId: String?,
        useSandbox: Boolean
    ) {
        serviceScope.launch {
            processManager.executeAgentTurn(
                prompt = prompt,
                projectName = projectName,
                modelId = modelId,
                permissionPolicy = permissionPolicy,
                conversationId = conversationId,
                useSandbox = useSandbox
            )
        }
    }

    fun runTerminalCommand(commandLine: String, projectName: String) {
        serviceScope.launch {
            processManager.executeWorkspaceCommand(commandLine, projectName)
        }
    }

    fun setAntigravityAuthToken(token: String) {
        processManager.antigravityAuthToken = token.trim()
    }

    private fun promoteToForeground(status: EngineStatus, customDetail: String? = null) {
        val notification = buildNotification(status, customDetail ?: status.detail)
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
    }

    private fun updateEngineStatus(status: EngineStatus, customDetail: String? = null) {
        _engineStatus.value = status
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(status, customDetail ?: status.detail))
    }

    private fun buildNotification(status: EngineStatus, detailText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingLaunch = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AgentDaemonService::class.java).apply {
            action = ACTION_STOP_DAEMON
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Antigravity • ${status.title}")
            .setContentText(detailText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingLaunch)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Antigravity CLI Engine Daemon",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent Antigravity CLI (agy) status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
