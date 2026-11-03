package com.antigravity.studio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.studio.process.AntigravityProcessManager
import com.antigravity.studio.ui.model.AntigravityUiState
import com.antigravity.studio.ui.model.BackgroundCommandItem
import com.antigravity.studio.ui.model.ChatTimelineItem
import com.antigravity.studio.ui.model.CheckoutMode
import com.antigravity.studio.ui.model.CommandStatus
import com.antigravity.studio.ui.model.ReviewPolicy
import com.antigravity.studio.ui.model.WorkspaceProject
import com.antigravity.studio.ui.theme.AntigravityPalette
import com.antigravity.studio.ui.theme.AntigravityStudioTheme
import kotlinx.coroutines.launch

/**
 * Module 4: Exact Antigravity 2.0 Adaptive UI Layer (`AntigravityStudioScreen.kt`)
 *
 * Matches the Antigravity 2.0 Desktop visual style 1:1 while adapting seamlessly
 * between Phone (`Compact`/`Medium`) and Tablet/Desktop (`Expanded`) via `WindowWidthSizeClass`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntigravityStudioScreen(
    uiState: AntigravityUiState,
    windowWidthSizeClass: WindowWidthSizeClass,
    onSelectProject: (String) -> Unit,
    onSelectSession: (String, String) -> Unit,
    onNewConversation: () -> Unit,
    onPromptChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onSelectModel: (AntigravityProcessManager.AgyModelOption) -> Unit,
    onSelectReviewPolicy: (ReviewPolicy) -> Unit,
    onSelectCheckoutMode: (CheckoutMode) -> Unit,
    onToggleThoughtExpanded: (String) -> Unit,
    onOpenDiffReview: (ChatTimelineItem.GitDiffCard?) -> Unit,
    onToggleOpenIdePane: () -> Unit,
    onSetOpenIdePaneVisible: (Boolean) -> Unit,
    onSetSettingsVisible: (Boolean) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onRunTerminalCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpanded = windowWidthSizeClass == WindowWidthSizeClass.Expanded
    var isSidebarCollapsedOnTablet by remember { mutableStateOf(false) }

    // Git Diff Review Modal
    uiState.activeDiffForReview?.let { diffCard ->
        GitDiffReviewDialog(
            diffCard = diffCard,
            onDismiss = { onOpenDiffReview(null) }
        )
    }

    // Settings & Real Antigravity CLI Auth/Binary Modal
    if (uiState.isSettingsDialogVisible) {
        AntigravitySettingsDialog(
            uiState = uiState,
            onSaveApiKey = onSaveApiKey,
            onRunCliCommand = onRunTerminalCommand,
            onDismiss = { onSetSettingsVisible(false) }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AntigravityPalette.BackgroundOled)
    ) {
        // Top Window Menu Bar: Antigravity | Workspace | View | Window
        AntigravityTopMenuBar(
            cliVersion = uiState.cliVersion,
            isExecuting = uiState.isExecutingTurn,
            onOpenSettings = { onSetSettingsVisible(true) },
            onToggleIde = onToggleOpenIdePane
        )

        HorizontalDivider(thickness = 1.dp, color = AntigravityPalette.BorderSubtle)

        if (isExpanded) {
            // TABLET / DESKTOP EXPANDED MULTI-PANE LAYOUT
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AnimatedVisibility(visible = !isSidebarCollapsedOnTablet) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        AntigravitySidebar(
                            uiState = uiState,
                            onToggleSidebar = { isSidebarCollapsedOnTablet = !isSidebarCollapsedOnTablet },
                            onSelectProject = onSelectProject,
                            onSelectSession = onSelectSession,
                            onNewConversation = onNewConversation,
                            onOpenSettings = { onSetSettingsVisible(true) },
                            modifier = Modifier
                                .width(252.dp)
                                .fillMaxHeight()
                        )
                        VerticalDivider(thickness = 1.dp, color = AntigravityPalette.BorderSubtle)
                    }
                }

                // Main Center Antigravity Canvas
                AntigravityMainCanvas(
                    uiState = uiState,
                    showSidebarToggleInCanvas = isSidebarCollapsedOnTablet,
                    onToggleSidebar = { isSidebarCollapsedOnTablet = !isSidebarCollapsedOnTablet },
                    onToggleOpenIde = onToggleOpenIdePane,
                    onSelectProject = onSelectProject,
                    onPromptChange = onPromptChange,
                    onSendPrompt = onSendPrompt,
                    onSelectModel = onSelectModel,
                    onSelectReviewPolicy = onSelectReviewPolicy,
                    onSelectCheckoutMode = onSelectCheckoutMode,
                    onToggleThoughtExpanded = onToggleThoughtExpanded,
                    onOpenDiffReview = onOpenDiffReview,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                // Right-hand IDE / Terminal & Inspector Pane (toggled by "Open IDE")
                AnimatedVisibility(visible = uiState.isOpenIdePaneVisible) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        VerticalDivider(thickness = 1.dp, color = AntigravityPalette.BorderSubtle)
                        OpenIdeInspectorPane(
                            uiState = uiState,
                            onRunTerminalCommand = onRunTerminalCommand,
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        } else {
            // PHONE COMPACT LAYOUT: Collapsible Sidebar Drawer + Center Canvas + ModalBottomSheet IDE
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = AntigravityPalette.SidebarBg,
                        drawerContentColor = AntigravityPalette.TextPrimary,
                        modifier = Modifier.width(276.dp)
                    ) {
                        AntigravitySidebar(
                            uiState = uiState,
                            onToggleSidebar = { scope.launch { drawerState.close() } },
                            onSelectProject = { projId ->
                                onSelectProject(projId)
                                scope.launch { drawerState.close() }
                            },
                            onSelectSession = { projId, sessId ->
                                onSelectSession(projId, sessId)
                                scope.launch { drawerState.close() }
                            },
                            onNewConversation = {
                                onNewConversation()
                                scope.launch { drawerState.close() }
                            },
                            onOpenSettings = {
                                scope.launch { drawerState.close() }
                                onSetSettingsVisible(true)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                AntigravityMainCanvas(
                    uiState = uiState,
                    showSidebarToggleInCanvas = true,
                    onToggleSidebar = {
                        scope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    },
                    onToggleOpenIde = onToggleOpenIdePane,
                    onSelectProject = onSelectProject,
                    onPromptChange = onPromptChange,
                    onSendPrompt = onSendPrompt,
                    onSelectModel = onSelectModel,
                    onSelectReviewPolicy = onSelectReviewPolicy,
                    onSelectCheckoutMode = onSelectCheckoutMode,
                    onToggleThoughtExpanded = onToggleThoughtExpanded,
                    onOpenDiffReview = onOpenDiffReview,
                    modifier = Modifier.fillMaxSize()
                )

                if (uiState.isOpenIdePaneVisible) {
                    ModalBottomSheet(
                        onDismissRequest = { onSetOpenIdePaneVisible(false) },
                        sheetState = sheetState,
                        containerColor = AntigravityPalette.SidebarBg,
                        contentColor = AntigravityPalette.TextPrimary
                    ) {
                        OpenIdeInspectorPane(
                            uiState = uiState,
                            onRunTerminalCommand = onRunTerminalCommand,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(500.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top Window Menu Bar (`Antigravity   Workspace   View   Window`) matching the screenshot.
 */
@Composable
private fun AntigravityTopMenuBar(
    cliVersion: String,
    isExecuting: Boolean,
    onOpenSettings: () -> Unit,
    onToggleIde: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(AntigravityPalette.TopBarBg)
            .padding(horizontal = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Antigravity",
                fontSize = 11.5.sp,
                color = AntigravityPalette.TextSecondary,
                modifier = Modifier.clickable(onClick = onOpenSettings)
            )
            Text(
                text = "Workspace",
                fontSize = 11.5.sp,
                color = AntigravityPalette.TextSecondary,
                modifier = Modifier.clickable(onClick = onToggleIde)
            )
            Text(
                text = "View",
                fontSize = 11.5.sp,
                color = AntigravityPalette.TextSecondary,
                modifier = Modifier.clickable(onClick = onToggleIde)
            )
            Text(
                text = "Window",
                fontSize = 11.5.sp,
                color = AntigravityPalette.TextSecondary
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isExecuting) AntigravityPalette.AccentBlueDot
                        else AntigravityPalette.StatusActiveGreen
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "agy v$cliVersion",
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                color = AntigravityPalette.TextMuted
            )
        }
    }
}

/**
 * Left Sidebar matching the screenshot 1:1:
 * - Header: Antigravity 'A' Logo | Sidebar Toggle | Back | Forward | Search
 * - `+ New Conversation` dark neutral button (`#25262B`)
 * - `Automations` & `Customizations`
 * - `Projects` section with filter & new folder icons
 * - Folder + Indented Conversation rows with timestamps (`5mo`, `6mo`) or blue active dot (`#3B82F6`)
 * - Bottom pinned `Settings`
 */
@Composable
fun AntigravitySidebar(
    uiState: AntigravityUiState,
    onToggleSidebar: () -> Unit,
    onSelectProject: (String) -> Unit,
    onSelectSession: (String, String) -> Unit,
    onNewConversation: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(AntigravityPalette.SidebarBg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Top Header Icon Row: [A Logo] [Sidebar Toggle] [<-] [->] [Search]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            AntigravityArchLogo(modifier = Modifier.size(18.dp))

            SidebarToggleIcon(
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onToggleSidebar)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = AntigravityPalette.TextMuted,
                modifier = Modifier.size(15.dp)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Forward",
                tint = AntigravityPalette.TextMuted,
                modifier = Modifier.size(15.dp)
            )

            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = AntigravityPalette.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // "+ New Conversation" Button (#25262B pill, exact match to screenshot)
        Surface(
            color = AntigravityPalette.NewConvoButtonBg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AntigravityPalette.NewConvoButtonBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clickable(onClick = onNewConversation)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = AntigravityPalette.TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Conversation",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AntigravityPalette.TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Automations & Customizations rows
        SidebarNavRow(
            iconType = "clock",
            label = "Automations",
            onClick = {}
        )
        SidebarNavRow(
            iconType = "toolbox",
            label = "Customizations",
            onClick = {}
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(thickness = 1.dp, color = AntigravityPalette.BorderSubtle)
        Spacer(modifier = Modifier.height(10.dp))

        // "Projects" Header with Filter + New Folder icons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Projects",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = AntigravityPalette.TextMuted
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterLinesIcon(modifier = Modifier.size(13.dp))
                FolderOutlineIcon(
                    color = AntigravityPalette.TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Scrollable Projects & Indented Sessions List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(uiState.projects, key = { it.id }) { project ->
                SidebarProjectGroup(
                    project = project,
                    selectedSessionId = uiState.selectedSessionId,
                    onSelectProject = { onSelectProject(project.id) },
                    onSelectSession = { sessId -> onSelectSession(project.id, sessId) }
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = AntigravityPalette.BorderSubtle)
        Spacer(modifier = Modifier.height(6.dp))

        // Bottom Pinned "Settings" Item
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = AntigravityPalette.TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Settings",
                fontSize = 13.sp,
                color = AntigravityPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun SidebarNavRow(
    iconType: String,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp)
    ) {
        if (iconType == "clock") {
            ClockOutlineIcon(
                color = AntigravityPalette.TextSecondary,
                modifier = Modifier.size(15.dp)
            )
        } else {
            ToolboxOutlineIcon(
                color = AntigravityPalette.TextSecondary,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = AntigravityPalette.TextSecondary
        )
    }
}

@Composable
private fun SidebarProjectGroup(
    project: WorkspaceProject,
    selectedSessionId: String?,
    onSelectProject: () -> Unit,
    onSelectSession: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Folder Row: [Folder Icon] JavaKingdom
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onSelectProject)
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            FolderOutlineIcon(
                color = if (project.isActive) AntigravityPalette.TextPrimary else AntigravityPalette.TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = project.name,
                fontSize = 13.sp,
                color = if (project.isActive) AntigravityPalette.TextPrimary else AntigravityPalette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Indented Session Rows underneath folder
        project.sessions.forEach { session ->
            val isSelected = session.id == selectedSessionId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) AntigravityPalette.SurfaceElevated
                        else Color.Transparent
                    )
                    .clickable { onSelectSession(session.id) }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = session.title,
                    fontSize = 13.sp,
                    color = AntigravityPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (session.hasActiveDot) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AntigravityPalette.AccentBlueDot)
                    )
                } else if (session.updatedAt.isNotEmpty()) {
                    Text(
                        text = session.updatedAt,
                        fontSize = 11.5.sp,
                        color = AntigravityPalette.TextMuted
                    )
                }
            }
        }
    }
}

/**
 * Main Antigravity Canvas (`#0E0E10` background):
 * - Top-right `⋮` + `Open IDE` button
 * - When in New Conversation state (`chatTimeline.isEmpty()`): Centers the project selector (`📁 JavaKingdom ⌄`)
 *   and the iconic `AntigravityPromptComposerCard` in the middle of the screen!
 * - When a conversation is active: Displays the conversation timeline above and docks the prompt card below.
 */
@Composable
fun AntigravityMainCanvas(
    uiState: AntigravityUiState,
    showSidebarToggleInCanvas: Boolean,
    onToggleSidebar: () -> Unit,
    onToggleOpenIde: () -> Unit,
    onSelectProject: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onSelectModel: (AntigravityProcessManager.AgyModelOption) -> Unit,
    onSelectReviewPolicy: (ReviewPolicy) -> Unit,
    onSelectCheckoutMode: (CheckoutMode) -> Unit,
    onToggleThoughtExpanded: (String) -> Unit,
    onOpenDiffReview: (ChatTimelineItem.GitDiffCard) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(AntigravityPalette.BackgroundOled)
    ) {
        // Top Action Bar inside Main Canvas (Sidebar toggle on compact + `⋮` and `Open IDE` on right)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .align(Alignment.TopCenter)
        ) {
            if (showSidebarToggleInCanvas) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onToggleSidebar)
                        .padding(6.dp)
                ) {
                    AntigravityArchLogo(modifier = Modifier.size(18.dp))
                    SidebarToggleIcon(modifier = Modifier.size(16.dp))
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            // Top-right: Three vertical dots + "Open IDE" button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = AntigravityPalette.TextSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onToggleOpenIde)
                )

                Surface(
                    color = AntigravityPalette.SurfaceDark,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AntigravityPalette.BorderCard),
                    modifier = Modifier.clickable(onClick = onToggleOpenIde)
                ) {
                    Text(
                        text = "Open IDE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AntigravityPalette.TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (uiState.chatTimeline.isEmpty()) {
            // CENTERED NEW CONVERSATION VIEW (1:1 match with uploaded screenshot!)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                ) {
                    // Folder Project Selector right above the prompt card: [Folder] JavaKingdom v
                    ProjectDropdownHeader(
                        activeProject = uiState.activeProject,
                        allProjects = uiState.projects,
                        onSelectProject = onSelectProject
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // The Iconic Antigravity Prompt Card
                    AntigravityPromptComposerCard(
                        uiState = uiState,
                        showModelTooltip = true,
                        onPromptChange = onPromptChange,
                        onSendPrompt = onSendPrompt,
                        onSelectModel = onSelectModel,
                        onSelectReviewPolicy = onSelectReviewPolicy,
                        onSelectCheckoutMode = onSelectCheckoutMode
                    )
                }
            }
        } else {
            // ACTIVE CONVERSATION TIMELINE VIEW
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                ) {
                    items(uiState.chatTimeline, key = { it.id }) { item ->
                        when (item) {
                            is ChatTimelineItem.UserPromptItem -> UserMessageBubble(item)
                            is ChatTimelineItem.AgentThoughtCard -> ThoughtTimelineRow(
                                item = item,
                                onToggle = { onToggleThoughtExpanded(item.id) }
                            )
                            is ChatTimelineItem.GitDiffCard -> DiffTimelineCard(
                                item = item,
                                onReview = { onOpenDiffReview(item) }
                            )
                            is ChatTimelineItem.AgentResponseItem -> AssistantMessageView(item)
                        }
                    }
                }

                // Bottom-docked Antigravity Prompt Card
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    ProjectDropdownHeader(
                        activeProject = uiState.activeProject,
                        allProjects = uiState.projects,
                        onSelectProject = onSelectProject
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AntigravityPromptComposerCard(
                        uiState = uiState,
                        showModelTooltip = false,
                        onPromptChange = onPromptChange,
                        onSendPrompt = onSendPrompt,
                        onSelectModel = onSelectModel,
                        onSelectReviewPolicy = onSelectReviewPolicy,
                        onSelectCheckoutMode = onSelectCheckoutMode
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectDropdownHeader(
    activeProject: WorkspaceProject,
    allProjects: List<WorkspaceProject>,
    onSelectProject: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            FolderOutlineIcon(
                color = AntigravityPalette.TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = activeProject.name,
                fontSize = 13.5.sp,
                color = AntigravityPalette.TextSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select Project",
                tint = AntigravityPalette.TextMuted,
                modifier = Modifier.size(15.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AntigravityPalette.SurfaceElevated)
        ) {
            allProjects.forEach { proj ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FolderOutlineIcon(
                                color = AntigravityPalette.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = proj.name,
                                fontSize = 13.sp,
                                color = AntigravityPalette.TextPrimary
                            )
                        }
                    },
                    onClick = {
                        onSelectProject(proj.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * The Iconic Antigravity 2.0 Prompt Card matching the screenshot:
 * - Upper input area: `Ask anything, @ to mention, / for actions`
 * - Control bar: `+` | `Gemini 4 Argon Medium ⌄` (with `Select Model Ctrl+/` badge) | `🛡️ Request Review ⌄` | `🎤` | Circular `→`
 * - Bottom footer strip: `💻 Local Checkout ⌄`
 */
@Composable
fun AntigravityPromptComposerCard(
    uiState: AntigravityUiState,
    showModelTooltip: Boolean,
    onPromptChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onSelectModel: (AntigravityProcessManager.AgyModelOption) -> Unit,
    onSelectReviewPolicy: (ReviewPolicy) -> Unit,
    onSelectCheckoutMode: (CheckoutMode) -> Unit
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var reviewMenuExpanded by remember { mutableStateOf(false) }
    var checkoutMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = AntigravityPalette.SurfaceDark,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AntigravityPalette.BorderCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Upper Section: Input + Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Input Row with optional floating "Select Model Ctrl+/" pill matching screenshot
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 38.dp)
                ) {
                    if (uiState.promptInput.isEmpty()) {
                        Text(
                            text = "Ask anything, @ to mention, / for actions",
                            fontSize = 14.sp,
                            color = AntigravityPalette.TextMuted
                        )
                    }

                    BasicTextField(
                        value = uiState.promptInput,
                        onValueChange = onPromptChange,
                        textStyle = TextStyle(
                            color = AntigravityPalette.TextPrimary,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(AntigravityPalette.AccentPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (showModelTooltip && uiState.promptInput.isEmpty()) {
                        Surface(
                            color = AntigravityPalette.SurfaceElevated,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, AntigravityPalette.NewConvoButtonBorder),
                            modifier = Modifier
                                .padding(start = 26.dp)
                                .clickable { modelMenuExpanded = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Select Model ",
                                    fontSize = 11.5.sp,
                                    color = AntigravityPalette.TextPrimary
                                )
                                Text(
                                    text = "Ctrl+/",
                                    fontSize = 11.sp,
                                    color = AntigravityPalette.TextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Middle Control Row: [+] [Gemini 4 Argon Medium v] ... [Shield Request Review v] [Mic] [(->)]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Left controls: + and Model Dropdown
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach context",
                            tint = AntigravityPalette.TextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onPromptChange(uiState.promptInput + "@") }
                        )

                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { modelMenuExpanded = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = uiState.selectedModel.displayName,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AntigravityPalette.TextSecondary
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = uiState.selectedModel.tierBadge,
                                    fontSize = 12.5.sp,
                                    color = AntigravityPalette.TextMuted
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select Model",
                                    tint = AntigravityPalette.TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = modelMenuExpanded,
                                onDismissRequest = { modelMenuExpanded = false },
                                modifier = Modifier.background(AntigravityPalette.SurfaceElevated)
                            ) {
                                uiState.availableModels.forEach { modelOption ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = modelOption.displayName,
                                                    fontSize = 13.sp,
                                                    color = AntigravityPalette.TextPrimary
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = modelOption.tierBadge,
                                                    fontSize = 12.sp,
                                                    color = AntigravityPalette.TextMuted
                                                )
                                            }
                                        },
                                        onClick = {
                                            onSelectModel(modelOption)
                                            modelMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Right controls: [Shield Request Review v] [Mic] [(->)]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { reviewMenuExpanded = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                ShieldCheckIcon(
                                    color = AntigravityPalette.TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = uiState.reviewPolicy.label,
                                    fontSize = 12.sp,
                                    color = AntigravityPalette.TextSecondary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Review Policy",
                                    tint = AntigravityPalette.TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = reviewMenuExpanded,
                                onDismissRequest = { reviewMenuExpanded = false },
                                modifier = Modifier.background(AntigravityPalette.SurfaceElevated)
                            ) {
                                ReviewPolicy.entries.forEach { policy ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = policy.label,
                                                fontSize = 13.sp,
                                                color = AntigravityPalette.TextPrimary
                                            )
                                        },
                                        onClick = {
                                            onSelectReviewPolicy(policy)
                                            reviewMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        MicrophoneOutlineIcon(
                            color = AntigravityPalette.TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )

                        // Circular Send Button (->) matching screenshot
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(AntigravityPalette.SendButtonCircleBg)
                                .clickable(onClick = onSendPrompt)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Send Prompt",
                                tint = AntigravityPalette.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Divider before footer
            HorizontalDivider(thickness = 1.dp, color = AntigravityPalette.BorderSubtle)

            // Bottom Footer Strip: [Laptop Icon] Local Checkout v
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AntigravityPalette.SurfaceFooter)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { checkoutMenuExpanded = true }
                ) {
                    LaptopTerminalIcon(
                        color = AntigravityPalette.TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = uiState.checkoutMode.label,
                        fontSize = 11.5.sp,
                        color = AntigravityPalette.TextMuted
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Checkout Mode",
                        tint = AntigravityPalette.TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }

                DropdownMenu(
                    expanded = checkoutMenuExpanded,
                    onDismissRequest = { checkoutMenuExpanded = false },
                    modifier = Modifier.background(AntigravityPalette.SurfaceElevated)
                ) {
                    CheckoutMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = mode.label,
                                    fontSize = 12.5.sp,
                                    color = AntigravityPalette.TextPrimary
                                )
                            },
                            onClick = {
                                onSelectCheckoutMode(mode)
                                checkoutMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(item: ChatTimelineItem.UserPromptItem) {
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = AntigravityPalette.SurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AntigravityPalette.BorderCard),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Text(
                text = item.prompt,
                fontSize = 14.sp,
                color = AntigravityPalette.TextPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun ThoughtTimelineRow(
    item: ChatTimelineItem.AgentThoughtCard,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClockOutlineIcon(
                color = AntigravityPalette.TextMuted,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Worked for ${item.workedSeconds}s",
                fontSize = 12.5.sp,
                color = AntigravityPalette.TextMuted
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (item.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = AntigravityPalette.TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }

        AnimatedVisibility(visible = item.isExpanded) {
            Surface(
                color = AntigravityPalette.SurfaceDark,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AntigravityPalette.BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(10.dp)
                ) {
                    item.steps.forEach { step ->
                        Text(
                            text = "• $step",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AntigravityPalette.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffTimelineCard(
    item: ChatTimelineItem.GitDiffCard,
    onReview: () -> Unit
) {
    Surface(
        color = AntigravityPalette.SurfaceDark,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AntigravityPalette.BorderCard),
        modifier = Modifier.fillMaxWidth()
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
                Text(
                    text = item.filePath.substringAfterLast('/'),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = AntigravityPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+${item.additions}",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AntigravityPalette.DiffAddGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AntigravityPalette.DiffAddBg)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "-${item.deletions}",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AntigravityPalette.DiffDelRed,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AntigravityPalette.DiffDelBg)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }

            Surface(
                color = AntigravityPalette.SurfaceElevated,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, AntigravityPalette.NewConvoButtonBorder),
                modifier = Modifier.clickable(onClick = onReview)
            ) {
                Text(
                    text = "Review",
                    fontSize = 11.5.sp,
                    color = AntigravityPalette.TextPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AssistantMessageView(item: ChatTimelineItem.AgentResponseItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.markdownText,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = AntigravityPalette.TextPrimary
        )
        if (!item.tokenUsageLabel.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.tokenUsageLabel,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = AntigravityPalette.TextMuted
            )
        }
    }
}

/**
 * Right-hand / Bottom-Sheet IDE & Real Antigravity CLI Terminal Inspector
 */
@Composable
fun OpenIdeInspectorPane(
    uiState: AntigravityUiState,
    onRunTerminalCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var commandInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .background(AntigravityPalette.SidebarBg)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ANTIGRAVITY CLI & TERMINAL",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = AntigravityPalette.TextMuted
            )
            Text(
                text = "agy v${uiState.cliVersion}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = AntigravityPalette.StatusActiveGreen
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick real `agy` CLI actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            IdeQuickChip("agy --version") { onRunTerminalCommand("agy --version") }
            IdeQuickChip("agy models") { onRunTerminalCommand("agy models") }
            IdeQuickChip("agy mcp list") { onRunTerminalCommand("agy mcp list") }
            IdeQuickChip("agy agents") { onRunTerminalCommand("agy agents") }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Background Commands List
        uiState.backgroundCommands.take(2).forEach { cmd ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AntigravityPalette.BackgroundOled)
                    .border(1.dp, AntigravityPalette.BorderSubtle, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = cmd.command,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AntigravityPalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = cmd.durationLabel,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AntigravityPalette.StatusActiveGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Live stdout/stderr stream from `context.filesDir/runtime/bin/agy`
        Surface(
            color = AntigravityPalette.BackgroundOled,
            shape = RoundedCornerShape(8.dp),
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
                items(uiState.terminalLogs) { line ->
                    Text(
                        text = line,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (line.contains("ERROR") || line.contains("stderr")) {
                            AntigravityPalette.DiffDelRed
                        } else {
                            AntigravityPalette.TextSecondary
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive shell/agy input
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = {
                    Text(
                        text = "$ agy --help",
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AntigravityPalette.TextMuted
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AntigravityPalette.BackgroundOled,
                    unfocusedContainerColor = AntigravityPalette.BackgroundOled,
                    focusedBorderColor = AntigravityPalette.AccentPrimary,
                    unfocusedBorderColor = AntigravityPalette.BorderSubtle,
                    focusedTextColor = AntigravityPalette.TextPrimary,
                    unfocusedTextColor = AntigravityPalette.TextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    onRunTerminalCommand(commandInput)
                    commandInput = ""
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AntigravityPalette.SurfaceElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Run",
                    tint = AntigravityPalette.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun IdeQuickChip(label: String, onClick: () -> Unit) {
    Surface(
        color = AntigravityPalette.SurfaceElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, AntigravityPalette.NewConvoButtonBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            color = AntigravityPalette.TextPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun AntigravitySettingsDialog(
    uiState: AntigravityUiState,
    onSaveApiKey: (String) -> Unit,
    onRunCliCommand: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyText by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey) }
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AntigravityPalette.SurfaceDark,
        titleContentColor = AntigravityPalette.TextPrimary,
        textContentColor = AntigravityPalette.TextSecondary,
        title = {
            Text(
                text = "Antigravity CLI Settings (v${uiState.cliVersion})",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Native Binary: filesDir/runtime/bin/agy (android_arm64 Bionic ELF)",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AntigravityPalette.StatusActiveGreen
                )

                if (uiState.detectedAuthUrl != null) {
                    OutlinedButton(
                        onClick = { uriHandler.openUri(uiState.detectedAuthUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Google Sign-In for agy CLI")
                    }
                }

                Text(
                    text = "GEMINI_API_KEY (injected into agy environment & direct fallback):",
                    fontSize = 12.sp,
                    color = AntigravityPalette.TextSecondary
                )

                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    placeholder = { Text("AIzaSy...", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AntigravityPalette.BackgroundOled,
                        unfocusedContainerColor = AntigravityPalette.BackgroundOled,
                        focusedBorderColor = AntigravityPalette.AccentPrimary,
                        unfocusedBorderColor = AntigravityPalette.BorderSubtle,
                        focusedTextColor = AntigravityPalette.TextPrimary,
                        unfocusedTextColor = AntigravityPalette.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IdeQuickChip("Verify agy --version") {
                        onRunCliCommand("agy --version")
                        onDismiss()
                    }
                    IdeQuickChip("Start Remote-Control") {
                        onRunCliCommand("agy remote-control status")
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveApiKey(keyText)
                    onDismiss()
                }
            ) {
                Text("Save", color = AntigravityPalette.AccentPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AntigravityPalette.TextSecondary)
            }
        }
    )
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
            Text(
                text = "Review Changes • +${diffCard.additions} -${diffCard.deletions}",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                Text(
                    text = diffCard.filePath,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
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
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AntigravityPalette.TextPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = AntigravityPalette.AccentPrimary)
            }
        }
    )
}

/* ============================================================================
 * Custom Canvas Vector Icons matching the exact Antigravity 2.0 Screenshot
 * ============================================================================ */

@Composable
private fun AntigravityArchLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.88f)
            cubicTo(w * 0.28f, h * 0.12f, w * 0.72f, h * 0.12f, w * 0.85f, h * 0.88f)
        }
        drawPath(
            path = path,
            color = Color(0xFFE4E6EB),
            style = Stroke(width = w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun SidebarToggleIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.4.dp.toPx()
        drawRoundRect(
            color = Color(0xFF9CA0A8),
            topLeft = Offset(0f, size.height * 0.1f),
            size = Size(size.width, size.height * 0.8f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = Color(0xFF9CA0A8),
            start = Offset(size.width * 0.35f, size.height * 0.1f),
            end = Offset(size.width * 0.35f, size.height * 0.9f),
            strokeWidth = stroke
        )
    }
}

@Composable
private fun FolderOutlineIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.3.dp.toPx()
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.08f, h * 0.25f)
            lineTo(w * 0.38f, h * 0.25f)
            lineTo(w * 0.48f, h * 0.36f)
            lineTo(w * 0.92f, h * 0.36f)
            lineTo(w * 0.92f, h * 0.82f)
            lineTo(w * 0.08f, h * 0.82f)
            close()
        }
        drawPath(path = path, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))
    }
}

@Composable
private fun ClockOutlineIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.3.dp.toPx()
        drawCircle(color = color, style = Stroke(width = stroke))
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x, size.height * 0.28f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(size.width * 0.72f, center.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ToolboxOutlineIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.3.dp.toPx()
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.1f, h * 0.32f),
            size = Size(w * 0.8f, h * 0.54f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.35f, h * 0.32f),
            end = Offset(w * 0.35f, h * 0.18f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(w * 0.35f, h * 0.18f),
            end = Offset(w * 0.65f, h * 0.18f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(w * 0.65f, h * 0.18f),
            end = Offset(w * 0.65f, h * 0.32f),
            strokeWidth = stroke
        )
    }
}

@Composable
private fun FilterLinesIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.3.dp.toPx()
        val c = Color(0xFF686B73)
        drawLine(c, Offset(0f, size.height * 0.25f), Offset(size.width, size.height * 0.25f), stroke)
        drawLine(c, Offset(size.width * 0.2f, size.height * 0.5f), Offset(size.width * 0.8f, size.height * 0.5f), stroke)
        drawLine(c, Offset(size.width * 0.35f, size.height * 0.75f), Offset(size.width * 0.65f, size.height * 0.75f), stroke)
    }
}

@Composable
private fun ShieldCheckIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.3.dp.toPx()
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.88f, h * 0.25f)
            lineTo(w * 0.88f, h * 0.55f)
            cubicTo(w * 0.88f, h * 0.78f, w * 0.5f, h * 0.92f, w * 0.5f, h * 0.92f)
            cubicTo(w * 0.5f, h * 0.92f, w * 0.12f, h * 0.78f, w * 0.12f, h * 0.55f)
            lineTo(w * 0.12f, h * 0.25f)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))
    }
}

@Composable
private fun MicrophoneOutlineIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.3.dp.toPx()
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.32f, h * 0.12f),
            size = Size(w * 0.36f, h * 0.48f),
            cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
            style = Stroke(width = stroke)
        )
        drawLine(color, Offset(w * 0.5f, h * 0.65f), Offset(w * 0.5f, h * 0.88f), stroke)
        drawLine(color, Offset(w * 0.32f, h * 0.88f), Offset(w * 0.68f, h * 0.88f), stroke)
    }
}

@Composable
private fun LaptopTerminalIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.2.dp.toPx()
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.15f, h * 0.18f),
            size = Size(w * 0.7f, h * 0.52f),
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(color, Offset(w * 0.05f, h * 0.80f), Offset(w * 0.95f, h * 0.80f), stroke)
    }
}

@Preview(
    name = "Antigravity 2.0 Desktop/Tablet Expanded Style",
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
            onSelectSession = { _, _ -> },
            onNewConversation = {},
            onPromptChange = {},
            onSendPrompt = {},
            onSelectModel = {},
            onSelectReviewPolicy = {},
            onSelectCheckoutMode = {},
            onToggleThoughtExpanded = {},
            onOpenDiffReview = {},
            onToggleOpenIdePane = {},
            onSetOpenIdePaneVisible = {},
            onSetSettingsVisible = {},
            onSaveApiKey = {},
            onRunTerminalCommand = {}
        )
    }
}

@Preview(
    name = "Antigravity 2.0 Phone Compact Style",
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
            onSelectSession = { _, _ -> },
            onNewConversation = {},
            onPromptChange = {},
            onSendPrompt = {},
            onSelectModel = {},
            onSelectReviewPolicy = {},
            onSelectCheckoutMode = {},
            onToggleThoughtExpanded = {},
            onOpenDiffReview = {},
            onToggleOpenIdePane = {},
            onSetOpenIdePaneVisible = {},
            onSetSettingsVisible = {},
            onSaveApiKey = {},
            onRunTerminalCommand = {}
        )
    }
}
