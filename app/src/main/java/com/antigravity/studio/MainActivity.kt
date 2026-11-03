package com.antigravity.studio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.antigravity.studio.ui.AntigravityStudioScreen
import com.antigravity.studio.ui.AntigravityViewModel
import com.antigravity.studio.ui.theme.AntigravityStudioTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AntigravityViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.startAndBindDaemon()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF111215.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF111215.toInt())
        )
        super.onCreate(savedInstanceState)

        requestNotificationPermissionAndBootEngine()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(activity = this)
            val uiState by viewModel.uiState.collectAsState()

            AntigravityStudioTheme {
                AntigravityStudioScreen(
                    uiState = uiState,
                    windowWidthSizeClass = windowSizeClass.widthSizeClass,
                    onSelectProject = viewModel::selectProject,
                    onNewConversation = viewModel::createNewConversation,
                    onPromptChange = viewModel::updatePromptInput,
                    onSendPrompt = viewModel::sendPrompt,
                    onSelectModel = viewModel::selectModel,
                    onCyclePermissionPreset = viewModel::cyclePermissionPreset,
                    onToggleThoughtExpanded = viewModel::toggleThoughtExpanded,
                    onOpenDiffReview = viewModel::openDiffReview,
                    onSetInspectorVisible = viewModel::setInspectorSheetVisible,
                    onTriggerQuickAction = viewModel::triggerQuickAction,
                    onRunTerminalCommand = viewModel::runCustomTerminalCommand,
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                )
            }
        }
    }

    private fun requestNotificationPermissionAndBootEngine() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        viewModel.startAndBindDaemon()
    }
}
