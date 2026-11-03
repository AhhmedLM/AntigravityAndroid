package com.antigravity.studio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.studio.service.AgentDaemonService
import com.antigravity.studio.ui.model.AntigravityUiState
import com.antigravity.studio.ui.model.BackgroundCommandItem
import com.antigravity.studio.ui.model.ChatTimelineItem
import com.antigravity.studio.ui.model.CommandStatus
import com.antigravity.studio.ui.model.WorkspaceProject
import com.antigravity.studio.ui.theme.AntigravityPalette
import com.antigravity.studio.ui.theme.AntigravityStudioTheme
import kotlinx.coroutines.launch

/**
 * Module 4: Adaptive Antigravity UI Layer (Jetpack Compose Material 3)
 *
 * Pure OLED Dark Theme (`#111215` Background, `#1A1B1E` Surfaces, `#2A2B2F` Borders)
 * with responsive layout switching via `WindowWidthSizeClass`:
 * - Phone (`Compact` / `Medium`): Collapsible Navigation Drawer for projects/sessions,
 *   single main chat pane, and a swipeable `ModalBottomSheet` for Terminal/Inspection logs.
 * - Tablet / Desktop (`Expanded`): Multi-Pane IDE layout:
 *   `[ Collapsible Projects Sidebar | Center Agent Chat Console | Right-hand Terminal & Tool Inspector ]`
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntigravityStudioScreen(
    uiState: AntigravityUiState,
    windowWidthSizeClass: WindowWidthSizeClass,
    onSelectProject: (String) -> Unit,
    onNewConversation: () -> Unit,
    onPromptChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onSelectModel: (String) -> Unit,
    onCyclePermissionPreset: () -> Unit,
    onToggleThoughtExpanded: (String) -> Unit,
    onOpenDiffReview: (ChatTimelineItem.GitDiffCard?) -> Unit,
    onSetInspectorVisible: (Boolean) -> Unit,
    onTriggerQuickAction: (String) -> Unit,
    onRunTerminalCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpanded = windowWidthSizeClass == WindowWidthSizeClass.Expanded
    var isTabletSidebarCollapsed by remember { mutableStateOf(false) }

    // Diff Review Modal Dialog when user taps "Review" on any Git Diff indicator
    uiState.activeDiffForReview?.let { diffCard ->
        GitDiffReviewDialog(
            diffCard = diffCard,
            onDismiss = { onOpenDiffReview(null) }
        )
    }

    if (isExpanded) {
        // TABLET / EXPANDED MULTI-PANE IDE LAYOUT
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(AntigravityPalette.BackgroundOled)
        ) {
            AnimatedVisibility(visible = !isTabletSidebarCollapsed) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    ProjectsSidebarContent(
                        uiState = uiState,
                        onSelectProject = onSelectProject,
                        onNewConversation = onNewConversation,
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                    )
                    VerticalDivider(
                        thickness = 1.dp,
                        color = AntigravityPalette.BorderSubtle
                    )
                }
            }

            // Center Workspace Chat Console
            WorkspaceChatConsole(
                uiState = uiState,
                isExpandedLayout = true,
                onToggleSidebarOrDrawer = { isTabletSidebarCollapsed = !isTabletSidebarCollapsed },
                onOpenInspectorSheet = { onSetInspectorVisible(true) },
                onPromptChange = onPromptChange,
                onSendPrompt = onSendPrompt,
                onSelectModel = onSelectModel,
                onCyclePermissionPreset = onCyclePermissionPreset,
                onToggleThoughtExpanded = onToggleThoughtExpanded,
                onOpenDiffReview = onOpenDiffReview,
                onTriggerQuickAction = onTriggerQuickAction,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            VerticalDivider(
                thickness = 1.dp,
                color = AntigravityPalette.BorderSubtle
            )

            // Right-hand Terminal & Tool Inspector Pane
            InspectorAndTerminalPane(
                uiState = uiState,
                onTriggerQuickAction = onTriggerQuickAction,
                onRunTerminalCommand = onRunTerminalCommand,
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
            )
        }
    } else {
        // PHONE / COMPACT LAYOUT: ModalNavigationDrawer + Main Chat Pane + ModalBottomSheet
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val coroutineScope = rememberCoroutineScope()
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = AntigravityPalette.SurfaceDark,
                    drawerContentColor = AntigravityPalette.TextPrimary,
                    modifier = Modifier.width(300.dp)
                ) {
                    ProjectsSidebarContent(
                        uiState = uiState,
                        onSelectProject = { projectId ->
                            onSelectProject(projectId)
                            coroutineScope.launch { drawerState.close() }
                        },
                        onNewConversation = {
                            onNewConversation()
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        ) {
            WorkspaceChatConsole(
                uiState = uiState,
                isExpandedLayout = false,
                onToggleSidebarOrDrawer = {
                    coroutineScope.launch {
                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                    }
                },
                onOpenInspectorSheet = { onSetInspectorVisible(true) },
                onPromptChange = onPromptChange,
                onSendPrompt = onSendPrompt,
                onSelectModel = onSelectModel,
                onCyclePermissionPreset = onCyclePermissionPreset,
                onToggleThoughtExpanded = onToggleThoughtExpanded,
                onOpenDiffReview = onOpenDiffReview,
                onTriggerQuickAction = onTriggerQuickAction,
                modifier = Modifier.fillMaxSize()
            )

            if (uiState.isInspectorSheetVisible) {
                ModalBottomSheet(
                    onDismissRequest = { onSetInspectorVisible(false) },
                    sheetState = bottomSheetState,
                    containerColor = AntigravityPalette.SurfaceDark,
                    contentColor = AntigravityPalette.TextPrimary
                ) {
                    InspectorAndTerminalPane(
                        uiState = uiState,
                        onTriggerQuickAction = onTriggerQuickAction,
                        onRunTerminalCommand = onRunTerminalCommand,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(520.dp)
                    )
                }
            }
        }
    }
}

/**
 * Component 1: Projects Drawer / Sidebar
 * Includes "+ New Conversation" button, project hierarchy with active status dots,
 * Automations, Customizations, and Settings items.
 */
@Composable
fun ProjectsSidebarContent(
    uiState: AntigravityUiState,
    onSelectProject: (String) -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(AntigravityPalette.SurfaceDark)
            .padding(14.dp)
    ) {
        // Brand Header + Engine Daemon Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AntigravityPalette.AccentPrimary.copy(alpha = 0.18f))
                        .border(1.dp, AntigravityPalette.AccentPrimary, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AntigravityPalette.AccentPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Antigravity Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "arm64-v8a • Node :${uiState.daemonPort}",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 11.sp,
                        color = AntigravityPalette.TextSecondary
                    )
                }
            }

            StatusDot(
                color = when (uiState.engineStatus) {
                    AgentDaemonService.EngineStatus.LISTENING -> AntigravityPalette.StatusActiveGreen
                    AgentDaemonService.EngineStatus.EXECUTING_TASK -> AntigravityPalette.AccentCyan
                    AgentDaemonService.EngineStatus.STARTING -> AntigravityPalette.StatusWarningAmber
                    AgentDaemonService.EngineStatus.ERROR -> AntigravityPalette.StatusErrorRed
                }
            )
        }

        // "+ New Conversation" Action Button
        Button(
            onClick = onNewConversation,
            colors = ButtonDefaults.buttonColors(
                containerColor = AntigravityPalette.AccentPrimary,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "+ New Conversation",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "WORKSPACES & SESSIONS",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            color = AntigravityPalette.TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        // Project Hierarchy List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(uiState.projects, key = { it.id }) { project ->
                ProjectHierarchyItem(
                    project = project,
                    onClick = { onSelectProject(project.id) }
                )
            }
        }

        HorizontalDivider(
            color = AntigravityPalette.BorderSubtle,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        // Bottom Navigation Items: Automations, Customizations, Settings
        SidebarFooterItem(
            icon = Icons.Default.Schedule,
            title = "Automations",
            badge = "Cron • 2"
        )
        SidebarFooterItem(
            icon = Icons.Default.Tune,
            title = "Customizations",
            badge = "Skills • MCP"
        )
        SidebarFooterItem(
            icon = Icons.Default.Settings,
            title = "Settings",
            badge = "Heap ${uiState.heapLimitMb}MB"
        )
    }
}

@Composable
private fun ProjectHierarchyItem(
    project: WorkspaceProject,
    onClick: () -> Unit
) {
    val borderColor = if (project.isActive) {
        AntigravityPalette.AccentPrimary.copy(alpha = 0.6f)
    } else {
        AntigravityPalette.BorderSubtle
    }
    val containerColor = if (project.isActive) {
        AntigravityPalette.SurfaceElevated
    } else {
        AntigravityPalette.BackgroundOled.copy(alpha = 0.6f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(
                    color = if (project.isActive) {
                        AntigravityPalette.StatusActiveGreen
                    } else {
                        AntigravityPalette.TextMuted
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (project.isActive) AntigravityPalette.AccentPrimary else AntigravityPalette.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = project.branch,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 10.sp,
                color = AntigravityPalette.AccentCyan
            )
        }

        if (project.sessions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            project.sessions.forEach { session ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 3.dp, bottom = 3.dp)
                ) {
                    Text(
                        text = "• ${session.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (session.isRunning) AntigravityPalette.TextPrimary else AntigravityPalette.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = session.updatedAt,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 10.sp,
                        color = AntigravityPalette.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarFooterItem(
    icon: ImageVector,
    title: String,
    badge: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { }
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AntigravityPalette.TextSecondary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = AntigravityPalette.TextPrimary
            )
        }
        Text(
            text = badge,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 10.sp,
            color = AntigravityPalette.TextSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(AntigravityPalette.SurfaceElevated)
                .border(1.dp, AntigravityPalette.BorderSubtle, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Component 2: Workspace Chat Console
 * Includes Project title path breadcrumb, agent thought cards ("Worked for 21s"),
 * git diff indicators (`+30 -6`) with Review buttons, and a bottom floating prompt bar
 * (model selector dropdown, permission presets, action triggers).
 */
@Composable
fun WorkspaceChatConsole(
    uiState: AntigravityUiState,
    isExpandedLayout: Boolean,
    onToggleSidebarOrDrawer: () -> Unit,
    onOpenInspectorSheet: () -> Unit,
    onPromptChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onSelectModel: (String) -> Unit,
    onCyclePermissionPreset: () -> Unit,
    onToggleThoughtExpanded: (String) -> Unit,
    onOpenDiffReview: (ChatTimelineItem.GitDiffCard) -> Unit,
    onTriggerQuickAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = AntigravityPalette.BackgroundOled,
        topBar = {
            WorkspaceHeaderBreadcrumbBar(
                uiState = uiState,
                isExpandedLayout = isExpandedLayout,
                onToggleSidebarOrDrawer = onToggleSidebarOrDrawer,
                onOpenInspectorSheet = onOpenInspectorSheet
            )
        },
        bottomBar = {
            FloatingAgentPromptBar(
                uiState = uiState,
                onPromptChange = onPromptChange,
                onSendPrompt = onSendPrompt,
                onSelectModel = onSelectModel,
                onCyclePermissionPreset = onCyclePermissionPreset,
                onTriggerQuickAction = onTriggerQuickAction
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(uiState.chatTimeline, key = { it.id }) { timelineItem ->
                when (timelineItem) {
                    is ChatTimelineItem.UserPromptItem -> UserPromptBubble(timelineItem)
                    is ChatTimelineItem.AgentThoughtCard -> AgentThoughtCardView(
                        item = timelineItem,
                        onToggleExpand = { onToggleThoughtExpanded(timelineItem.id) }
                    )
                    is ChatTimelineItem.GitDiffCard -> GitDiffIndicatorCard(
                        item = timelineItem,
                        onReviewClick = { onOpenDiffReview(timelineItem) }
                    )
                    is ChatTimelineItem.AgentResponseItem -> AgentResponseCardView(timelineItem)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceHeaderBreadcrumbBar(
    uiState: AntigravityUiState,
    isExpandedLayout: Boolean,
    onToggleSidebarOrDrawer: () -> Unit,
    onOpenInspectorSheet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AntigravityPalette.SurfaceDark)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onToggleSidebarOrDrawer,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Toggle Workspaces",
                        tint = AntigravityPalette.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiState.activeProject.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.activeProject.branch,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 11.sp,
                            color = AntigravityPalette.AccentCyan,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AntigravityPalette.SurfaceElevated)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = uiState.activeProject.pathBreadcrumb,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 11.sp,
                        color = AntigravityPalette.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Live Engine Status Pill + Compact Terminal Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(AntigravityPalette.SurfaceElevated)
                        .border(1.dp, AntigravityPalette.BorderSubtle, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    StatusDot(
                        color = when (uiState.engineStatus) {
                            AgentDaemonService.EngineStatus.LISTENING -> AntigravityPalette.StatusActiveGreen
                            AgentDaemonService.EngineStatus.EXECUTING_TASK -> AntigravityPalette.AccentCyan
                            AgentDaemonService.EngineStatus.STARTING -> AntigravityPalette.StatusWarningAmber
                            AgentDaemonService.EngineStatus.ERROR -> AntigravityPalette.StatusErrorRed
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "127.0.0.1:${uiState.daemonPort}",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 11.sp,
                        color = AntigravityPalette.TextPrimary
                    )
                }

                if (!isExpandedLayout) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onOpenInspectorSheet,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AntigravityPalette.SurfaceElevated)
                            .border(1.dp, AntigravityPalette.BorderSubtle, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Open Terminal & Inspector Sheet",
                            tint = AntigravityPalette.AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = AntigravityPalette.BorderSubtle)
    }
}

@Composable
private fun UserPromptBubble(item: ChatTimelineItem.UserPromptItem) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = AntigravityPalette.SurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AntigravityPalette.AccentPrimary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "YOU",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 10.sp,
                        color = AntigravityPalette.AccentPrimary
                    )
                    Text(
                        text = item.timestamp,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 10.sp,
                        color = AntigravityPalette.TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AntigravityPalette.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun AgentThoughtCardView(
    item: ChatTimelineItem.AgentThoughtCard,
    onToggleExpand: () -> Unit
) {
    Surface(
        color = AntigravityPalette.SurfaceDark,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AntigravityPalette.BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = AntigravityPalette.AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Worked for ${item.workedSeconds}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = AntigravityPalette.AccentCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.summary.substringAfter("• ", item.summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = AntigravityPalette.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = if (item.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand Thought",
                    tint = AntigravityPalette.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = item.isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    HorizontalDivider(color = AntigravityPalette.BorderSubtle)
                    Spacer(modifier = Modifier.height(4.dp))
                    item.steps.forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AntigravityPalette.StatusActiveGreen,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = AntigravityPalette.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitDiffIndicatorCard(
    item: ChatTimelineItem.GitDiffCard,
    onReviewClick: () -> Unit
) {
    Surface(
        color = AntigravityPalette.SurfaceDark,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AntigravityPalette.BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = AntigravityPalette.AccentPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.filePath.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // +30 -6 git diff indicators
                    Text(
                        text = "+${item.additions}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AntigravityPalette.DiffAddGreen,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AntigravityPalette.DiffAddBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "-${item.deletions}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AntigravityPalette.DiffDelRed,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AntigravityPalette.DiffDelBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                OutlinedButton(
                    onClick = onReviewClick,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AntigravityPalette.AccentPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = "Review",
                        fontSize = 12.sp,
                        color = AntigravityPalette.AccentPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.filePath,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                color = AntigravityPalette.TextMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = AntigravityPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun AgentResponseCardView(item: ChatTimelineItem.AgentResponseItem) {
    Surface(
        color = AntigravityPalette.SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AntigravityPalette.BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AntigravityPalette.AccentPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ANTIGRAVITY AGENT",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 11.sp,
                        color = AntigravityPalette.AccentPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = item.timestamp,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 10.sp,
                    color = AntigravityPalette.TextMuted
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.markdownText,
                style = MaterialTheme.typography.bodyMedium,
                color = AntigravityPalette.TextPrimary
            )
        }
    }
}

@Composable
private fun FloatingAgentPromptBar(
    uiState: AntigravityUiState,
    onPromptChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onSelectModel: (String) -> Unit,
    onCyclePermissionPreset: () -> Unit,
    onTriggerQuickAction: (String) -> Unit
) {
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        color = AntigravityPalette.SurfaceDark,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, AntigravityPalette.BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            OutlinedTextField(
                value = uiState.promptInput,
                onValueChange = onPromptChange,
                placeholder = {
                    Text(
                        text = "Ask Antigravity Agent or run `/plan`, `/goal`, `@file`...",
                        style = MaterialTheme.typography.bodySmall,
                        color = AntigravityPalette.TextMuted
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AntigravityPalette.BackgroundOled,
                    unfocusedContainerColor = AntigravityPalette.BackgroundOled,
                    focusedBorderColor = AntigravityPalette.AccentPrimary,
                    unfocusedBorderColor = AntigravityPalette.BorderSubtle,
                    focusedTextColor = AntigravityPalette.TextPrimary,
                    unfocusedTextColor = AntigravityPalette.TextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Control Row: Model Dropdown | Permission Preset | Quick Triggers | Send
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Model Selector Dropdown
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AntigravityPalette.SurfaceElevated)
                                .border(1.dp, AntigravityPalette.BorderSubtle, RoundedCornerShape(8.dp))
                                .clickable { modelDropdownExpanded = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = uiState.selectedModel,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                color = AntigravityPalette.TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = "Select Model",
                                tint = AntigravityPalette.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = modelDropdownExpanded,
                            onDismissRequest = { modelDropdownExpanded = false },
                            modifier = Modifier.background(AntigravityPalette.SurfaceElevated)
                        ) {
                            uiState.availableModels.forEach { modelName ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = modelName,
                                            color = AntigravityPalette.TextPrimary,
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = {
                                        onSelectModel(modelName)
                                        modelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Permission Preset Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AntigravityPalette.SurfaceElevated)
                            .border(1.dp, AntigravityPalette.BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable(onClick = onCyclePermissionPreset)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AntigravityPalette.AccentCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = uiState.permissionPreset.badge,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 11.sp,
                            color = AntigravityPalette.AccentCyan
                        )
                    }

                    // Action Trigger Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AntigravityPalette.SurfaceElevated)
                            .border(1.dp, AntigravityPalette.BorderSubtle, RoundedCornerShape(8.dp))
                            .clickable { onTriggerQuickAction("Terminal") }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Trigger Action",
                            tint = AntigravityPalette.TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tools",
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 11.sp,
                            color = AntigravityPalette.TextSecondary
                        )
                    }
                }

                // Dispatch Prompt Button
                IconButton(
                    onClick = onSendPrompt,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AntigravityPalette.AccentPrimary)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Dispatch Prompt",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Component 3: Inspector / Background Commands Pane
 * Includes Quick action tiles (Review, Open File, Terminal, Browser),
 * live command status items, and live Node.js / shell stdout/stderr stream.
 */
@Composable
fun InspectorAndTerminalPane(
    uiState: AntigravityUiState,
    onTriggerQuickAction: (String) -> Unit,
    onRunTerminalCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var shellInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .background(AntigravityPalette.SurfaceDark)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "INSPECTOR & BACKGROUND COMMANDS",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                color = AntigravityPalette.TextMuted
            )
            Text(
                text = "PID ${uiState.daemonPid}",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                color = AntigravityPalette.StatusActiveGreen
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Action Tiles: Review | Open File | Terminal | Browser
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            QuickActionTile(
                icon = Icons.Default.Code,
                label = "Review",
                onClick = { onTriggerQuickAction("Review") },
                modifier = Modifier.weight(1f)
            )
            QuickActionTile(
                icon = Icons.Default.Description,
                label = "Open File",
                onClick = { onTriggerQuickAction("Open File") },
                modifier = Modifier.weight(1f)
            )
            QuickActionTile(
                icon = Icons.Default.Terminal,
                label = "Terminal",
                onClick = { onTriggerQuickAction("Terminal") },
                modifier = Modifier.weight(1f)
            )
            QuickActionTile(
                icon = Icons.Default.Language,
                label = "Browser",
                onClick = { onTriggerQuickAction("Browser") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "ACTIVE & RECENT PROCESSES",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            color = AntigravityPalette.TextMuted
        )

        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            uiState.backgroundCommands.take(3).forEach { cmd ->
                BackgroundCommandStatusRow(cmd)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "LIVE ENGINE STDOUT / STDERR STREAM",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            color = AntigravityPalette.TextMuted
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Live Terminal Output Console
        Surface(
            color = AntigravityPalette.BackgroundOled,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AntigravityPalette.BorderSubtle),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.terminalLogs) { logLine ->
                    Text(
                        text = logLine,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 11.sp,
                        color = if (logLine.contains("ERROR")) {
                            AntigravityPalette.StatusErrorRed
                        } else if (logLine.contains("Listening") || logLine.contains("listening")) {
                            AntigravityPalette.StatusActiveGreen
                        } else {
                            AntigravityPalette.TextSecondary
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Shell Command Input Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = shellInput,
                onValueChange = { shellInput = it },
                placeholder = {
                    Text(
                        text = "$ busybox ls -la runtime/bin",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 11.sp,
                        color = AntigravityPalette.TextMuted
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AntigravityPalette.BackgroundOled,
                    unfocusedContainerColor = AntigravityPalette.BackgroundOled,
                    focusedBorderColor = AntigravityPalette.AccentCyan,
                    unfocusedBorderColor = AntigravityPalette.BorderSubtle,
                    focusedTextColor = AntigravityPalette.TextPrimary,
                    unfocusedTextColor = AntigravityPalette.TextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    onRunTerminalCommand(shellInput)
                    shellInput = ""
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AntigravityPalette.SurfaceElevated)
                    .border(1.dp, AntigravityPalette.BorderSubtle, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Execute Command",
                    tint = AntigravityPalette.AccentCyan
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AntigravityPalette.SurfaceElevated)
            .border(1.dp, AntigravityPalette.BorderSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = AntigravityPalette.AccentPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            color = AntigravityPalette.TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun BackgroundCommandStatusRow(item: BackgroundCommandItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AntigravityPalette.BackgroundOled)
            .border(1.dp, AntigravityPalette.BorderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            StatusDot(
                color = when (item.status) {
                    CommandStatus.LISTENING, CommandStatus.RUNNING -> AntigravityPalette.StatusActiveGreen
                    CommandStatus.COMPLETED -> AntigravityPalette.AccentCyan
                    CommandStatus.FAILED -> AntigravityPalette.StatusErrorRed
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = item.command,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    color = AntigravityPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.workingDir,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = AntigravityPalette.TextMuted
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.durationLabel,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 10.sp,
            color = AntigravityPalette.TextSecondary
        )
    }
}

@Composable
private fun GitDiffReviewDialog(
    diffCard: ChatTimelineItem.GitDiffCard,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AntigravityPalette.SurfaceDark,
        titleContentColor = AntigravityPalette.TextPrimary,
        textContentColor = AntigravityPalette.TextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = AntigravityPalette.AccentPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Diff Review • +${diffCard.additions} -${diffCard.deletions}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column {
                Text(
                    text = diffCard.filePath,
                    style = MaterialTheme.typography.labelMedium,
                    color = AntigravityPalette.AccentCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = AntigravityPalette.BackgroundOled,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AntigravityPalette.BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = diffCard.patchPreview,
                        style = MaterialTheme.typography.labelMedium,
                        color = AntigravityPalette.TextPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Approve & Close", color = AntigravityPalette.AccentPrimary)
            }
        }
    )
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Preview(
    name = "Antigravity Phone Compact",
    widthDp = 412,
    heightDp = 892,
    showBackground = true
)
@Composable
fun AntigravityStudioCompactPreview() {
    AntigravityStudioTheme {
        AntigravityStudioScreen(
            uiState = AntigravityUiState(),
            windowWidthSizeClass = WindowWidthSizeClass.Compact,
            onSelectProject = {},
            onNewConversation = {},
            onPromptChange = {},
            onSendPrompt = {},
            onSelectModel = {},
            onCyclePermissionPreset = {},
            onToggleThoughtExpanded = {},
            onOpenDiffReview = {},
            onSetInspectorVisible = {},
            onTriggerQuickAction = {},
            onRunTerminalCommand = {}
        )
    }
}

@Preview(
    name = "Antigravity Tablet Expanded 3-Pane IDE",
    widthDp = 1280,
    heightDp = 800,
    showBackground = true
)
@Composable
fun AntigravityStudioExpandedPreview() {
    AntigravityStudioTheme {
        AntigravityStudioScreen(
            uiState = AntigravityUiState(),
            windowWidthSizeClass = WindowWidthSizeClass.Expanded,
            onSelectProject = {},
            onNewConversation = {},
            onPromptChange = {},
            onSendPrompt = {},
            onSelectModel = {},
            onCyclePermissionPreset = {},
            onToggleThoughtExpanded = {},
            onOpenDiffReview = {},
            onSetInspectorVisible = {},
            onTriggerQuickAction = {},
            onRunTerminalCommand = {}
        )
    }
}
