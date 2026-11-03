package com.antigravity.studio.ui

import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
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
import com.antigravity.studio.ui.model.AuthState
import com.antigravity.studio.ui.model.AuthType
import com.antigravity.studio.ui.model.BackgroundCommandItem
import com.antigravity.studio.ui.model.ChatTimelineItem
import com.antigravity.studio.ui.model.CheckoutMode
import com.antigravity.studio.ui.model.ReviewPolicy
import com.antigravity.studio.ui.model.WorkspaceProject
import com.antigravity.studio.ui.theme.AntigravityPalette
import com.antigravity.studio.ui.theme.AntigravityStudioTheme
import kotlinx.coroutines.launch

/**
 * Mobile-First Antigravity Studio Screen.
 *
 * Distinct experiences for Phone and Tablet:
 * - Phone (`Compact` / `Medium`): Sleek mobile app bar, drawer navigation, touch-optimized composer,
 *   dedicated Google/Gemini Sign-In modal, and swipeable Terminal bottom sheet.
 *   (NO clunky desktop window titlebar, NO desktop "Open IDE" button).
 * - Tablet (`Expanded`): Multi-pane workspace console with side-by-side terminal & inspector.
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
    onSetSignInVisible: (Boolean) -> Unit,
    onSignInWithAntigravity: (Context) -> Unit,
    onSaveAuthToken: (String) -> Unit,
    onSignOut: () -> Unit,
    onSetTerminalSheetVisible: (Boolean) -> Unit,
    onRunTerminalCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpanded = windowWidthSizeClass == WindowWidthSizeClass.Expanded
    val context = LocalContext.current

    // Diff Review Dialog
    uiState.activeDiffForReview?.let { diffCard ->
        GitDiffReviewDialog(
            diffCard = diffCard,
            onDismiss = { onOpenDiffReview(null) }
        )
    }

    // Sign-In & Account Authentication Dialog
    if (uiState.isSignInDialogVisible) {
        AntigravitySignInDialog(
            authState = uiState.authState,
            currentAuthToken = uiState.authToken,
            detectedAuthUrl = uiState.detectedAuthUrl,
            onSignInWithAntigravity = { onSignInWithAntigravity(context) },
            onSaveAuthToken = onSaveAuthToken,
            onSignOut = onSignOut,
            onDismiss = { onSetSignInVisible(false) }
        )
    }

    if (isExpanded) {
        // TABLET EXPANDED MULTI-PANE IDE LAYOUT
        var isTabletSidebarCollapsed by remember { mutableStateOf(false) }

        Row(
            modifier = modifier
                .fillMaxSize()
                .background(AntigravityPalette.BackgroundOled)
        ) {
            AnimatedVisibility(visible = !isTabletSidebarCollapsed) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    TabletSidebar(
                        uiState = uiState,
                        onSelectProject = onSelectProject,
                        onSelectSession = onSelectSession,
                        onNewConversation = onNewConversation,
                        onOpenSignIn = { onSetSignInVisible(true) },
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight()
                    )
                    VerticalDivider(thickness = 1.dp, color = AntigravityPalette.BorderSubtle)
                }
            }

            // Center Agent Console
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                TabletTopBar(
                    uiState = uiState,
                    isSidebarCollapsed = isTabletSidebarCollapsed,
                    onToggleSidebar = { isTabletSidebarCollapsed = !isTabletSidebarCollapsed },
                    onSelectProject = onSelectProject,
                    onOpenSignIn = { onSetSignInVisible(true) },
                    onToggleTerminal = { onSetTerminalSheetVisible(!uiState.isTerminalSheetVisible) }
                )
                HorizontalDivider(thickness = 1.dp, color = AntigravityPalette.BorderSubtle)

                AntigravityChatArea(
                    uiState = uiState,
                    onPromptChange = onPromptChange,
                    onSendPrompt = onSendPrompt,
                    onSelectModel = onSelectModel,
                    onSelectReviewPolicy = onSelectReviewPolicy,
                    onSelectCheckoutMode = onSelectCheckoutMode,
                    onToggleThoughtExpanded = onToggleThoughtExpanded,
                    onOpenDiffReview = onOpenDiffReview,
                    modifier = Modifier.weight(1f)
                )
            }

            // Right-hand Terminal & Logs Pane
            AnimatedVisibility(visible = uiState.isTerminalSheetVisible) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    VerticalDivider(thickness = 1.dp, color = AntigravityPalette.BorderSubtle)
                    TerminalAndInspectorPane(
                        uiState = uiState,
                        onRunTerminalCommand = onRunTerminalCommand,
                        onClose = { onSetTerminalSheetVisible(false) },
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                    )
                }
            }
        }
    } else {
        // PHONE COMPACT NATIVE MOBILE LAYOUT
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = AntigravityPalette.SidebarBg,
                    drawerContentColor = AntigravityPalette.TextPrimary,
                    modifier = Modifier.width(280.dp)
                ) {
                    MobileNavigationDrawerContent(
                        uiState = uiState,
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
                        onOpenSignIn = {
                            scope.launch { drawerState.close() }
                            onSetSignInVisible(true)
                        },
                        onOpenTerminal = {
                            scope.launch { drawerState.close() }
                            onSetTerminalSheetVisible(true)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            modifier = modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AntigravityPalette.BackgroundOled)
            ) {
                // Sleek Mobile Top App Bar
                MobileTopAppBar(
                    uiState = uiState,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onSelectProject = onSelectProject,
                    onOpenSignIn = { onSetSignInVisible(true) },
                    onOpenTerminal = { onSetTerminalSheetVisible(true) }
                )

                HorizontalDivider(thickness = 1.dp, color = AntigravityPalette.BorderSubtle)

                // Mobile Chat Canvas & Composer
                AntigravityChatArea(
                    uiState = uiState,
                    onPromptChange = onPromptChange,
                    onSendPrompt = onSendPrompt,
                    onSelectModel = onSelectModel,
                    onSelectReviewPolicy = onSelectReviewPolicy,
                    onSelectCheckoutMode = onSelectCheckoutMode,
                    onToggleThoughtExpanded = onToggleThoughtExpanded,
                    onOpenDiffReview = onOpenDiffReview,
                    modifier = Modifier.weight(1f)
                )
            }

            // Swipeable Terminal BottomSheet on Mobile
            if (uiState.isTerminalSheetVisible) {
                ModalBottomSheet(
                    onDismissRequest = { onSetTerminalSheetVisible(false) },
                    sheetState = sheetState,
                    containerColor = AntigravityPalette.SidebarBg,
                    contentColor = AntigravityPalette.TextPrimary
                ) {
                    TerminalAndInspectorPane(
                        uiState = uiState,
                        onRunTerminalCommand = onRunTerminalCommand,
                        onClose = { onSetTerminalSheetVisible(false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(480.dp)
                    )
                }
            }
        }
    }
}

/**
 * Sleek, native mobile top app bar:
 * - Hamburger menu icon to open drawer
 * - Project selector pill: `📁 JavaKingdom ⌄`
 * - Sign-In avatar pill (Google / Gemini) with status indicator
 */
@Composable
private fun MobileTopAppBar(
    uiState: AntigravityUiState,
    onOpenDrawer: () -> Unit,
    onSelectProject: (String) -> Unit,
    onOpenSignIn: () -> Unit,
    onOpenTerminal: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(AntigravityPalette.SidebarBg)
            .padding(horizontal = 12.dp)
    ) {
        // Left: Hamburger + Arch Logo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onOpenDrawer)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open Drawer",
                tint = AntigravityPalette.TextPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            AntigravityArchLogo(modifier = Modifier.size(18.dp))
        }

        // Center: Project Selector Pill
        ProjectDropdownHeader(
            activeProject = uiState.activeProject,
            allProjects = uiState.projects,
            onSelectProject = onSelectProject
        )

        // Right: Terminal badge + Auth Avatar Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // CLI Status dot
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AntigravityPalette.SurfaceElevated)
                    .clickable(onClick = onOpenTerminal)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.isExecutingTurn) AntigravityPalette.AccentBlueDot
                                else AntigravityPalette.StatusActiveGreen
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Terminal",
                        tint = AntigravityPalette.TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Auth Avatar / Sign-In Button
            AuthTopPill(
                authState = uiState.authState,
                onClick = onOpenSignIn
            )
        }
    }
}

/**
 * Tablet Top Bar (cleaner, no desktop menu bar).
 */
@Composable
private fun TabletTopBar(
    uiState: AntigravityUiState,
    isSidebarCollapsed: Boolean,
    onToggleSidebar: () -> Unit,
    onSelectProject: (String) -> Unit,
    onOpenSignIn: () -> Unit,
    onToggleTerminal: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(AntigravityPalette.SidebarBg)
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSidebarCollapsed) {
                IconButton(onClick = onToggleSidebar, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Sidebar",
                        tint = AntigravityPalette.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            AntigravityArchLogo(modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            ProjectDropdownHeader(
                activeProject = uiState.activeProject,
                allProjects = uiState.projects,
                onSelectProject = onSelectProject
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = AntigravityPalette.SurfaceElevated,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, AntigravityPalette.BorderSubtle),
                modifier = Modifier.clickable(onClick = onToggleTerminal)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.isExecutingTurn) AntigravityPalette.AccentBlueDot
                                else AntigravityPalette.StatusActiveGreen
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "agy v${uiState.cliVersion}",
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AntigravityPalette.TextPrimary
                    )
                }
            }

            AuthTopPill(
                authState = uiState.authState,
                onClick = onOpenSignIn
            )
        }
    }
}

/**
 * Top Auth Avatar Pill:
 * - If NOT signed in: "Sign In" button with Google G icon.
 * - If SIGNED IN: Avatar with User initial + active dot.
 */
@Composable
private fun AuthTopPill(
    authState: AuthState,
    onClick: () -> Unit
) {
    if (authState.isSignedIn) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(AntigravityPalette.SurfaceElevated)
                .border(1.dp, AntigravityPalette.NewConvoButtonBorder, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AntigravityPalette.AccentPrimary)
            ) {
                Text(
                    text = authState.displayName.take(1).ifEmpty { "G" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(AntigravityPalette.StatusActiveGreen)
            )
        }
    } else {
        Surface(
            color = AntigravityPalette.SurfaceElevated,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, AntigravityPalette.NewConvoButtonBorder),
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                AntigravityArchLogo(modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Sign in",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AntigravityPalette.TextPrimary
                )
            }
        }
    }
}

/**
 * Main Chat Area (Empty Canvas greeting OR active conversation stream + bottom composer).
 */
@Composable
private fun AntigravityChatArea(
    uiState: AntigravityUiState,
    onPromptChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onSelectModel: (AntigravityProcessManager.AgyModelOption) -> Unit,
    onSelectReviewPolicy: (ReviewPolicy) -> Unit,
    onSelectCheckoutMode: (CheckoutMode) -> Unit,
    onToggleThoughtExpanded: (String) -> Unit,
    onOpenDiffReview: (ChatTimelineItem.GitDiffCard) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.chatTimeline.isEmpty()) {
        // Empty New Conversation view tailored for mobile
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            AntigravityArchLogo(modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Antigravity Agent",
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = AntigravityPalette.TextPrimary
            )
            Text(
                text = "What would you like to build today?",
                fontSize = 13.5.sp,
                color = AntigravityPalette.TextMuted
            )
            Spacer(modifier = Modifier.height(24.dp))

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
    } else {
        // Active Conversation stream
        Column(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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

            // Bottom-docked Composer Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
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

/**
 * Mobile Navigation Drawer Content.
 */
@Composable
private fun MobileNavigationDrawerContent(
    uiState: AntigravityUiState,
    onSelectProject: (String) -> Unit,
    onSelectSession: (String, String) -> Unit,
    onNewConversation: () -> Unit,
    onOpenSignIn: () -> Unit,
    onOpenTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(AntigravityPalette.SidebarBg)
            .padding(14.dp)
    ) {
        // Top Account Card in Drawer
        Surface(
            color = AntigravityPalette.SurfaceDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AntigravityPalette.BorderCard),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSignIn)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp)
            ) {
                if (uiState.authState.isSignedIn) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AntigravityPalette.AccentPrimary)
                    ) {
                        Text(
                            text = uiState.authState.displayName.take(1).ifEmpty { "G" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.authState.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AntigravityPalette.TextPrimary
                        )
                        Text(
                            text = if (uiState.authState.authType == AuthType.ANTIGRAVITY_ACCOUNT) "Antigravity Connected" else "AGY Token Active",
                            fontSize = 11.5.sp,
                            color = AntigravityPalette.StatusActiveGreen
                        )
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AntigravityPalette.SurfaceElevated)
                    ) {
                        AntigravityArchLogo(modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sign in to Antigravity",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AntigravityPalette.TextPrimary
                        )
                        Text(
                            text = "Antigravity CLI (agy) account",
                            fontSize = 11.sp,
                            color = AntigravityPalette.TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // "+ New Conversation" Button
        Surface(
            color = AntigravityPalette.NewConvoButtonBg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AntigravityPalette.NewConvoButtonBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
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
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Conversation",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AntigravityPalette.TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "PROJECTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = AntigravityPalette.TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        // Projects & Sessions List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
        Spacer(modifier = Modifier.height(8.dp))

        // Terminal & Engine Logs Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpenTerminal)
                .padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = "Terminal",
                tint = AntigravityPalette.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Terminal & Engine Logs",
                fontSize = 13.sp,
                color = AntigravityPalette.TextSecondary
            )
        }
    }
}

/**
 * Tablet Sidebar (persistent side pane).
 */
@Composable
private fun TabletSidebar(
    uiState: AntigravityUiState,
    onSelectProject: (String) -> Unit,
    onSelectSession: (String, String) -> Unit,
    onNewConversation: () -> Unit,
    onOpenSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(AntigravityPalette.SidebarBg)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            AntigravityArchLogo(modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Antigravity",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AntigravityPalette.TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            color = AntigravityPalette.NewConvoButtonBg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AntigravityPalette.NewConvoButtonBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clickable(onClick = onNewConversation)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = AntigravityPalette.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Conversation", fontSize = 13.sp, color = AntigravityPalette.TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text("PROJECTS", fontSize = 11.sp, color = AntigravityPalette.TextMuted)
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpenSignIn)
                .padding(8.dp)
        ) {
            Icon(Icons.Default.Settings, null, tint = AntigravityPalette.TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Settings & Account", fontSize = 13.sp, color = AntigravityPalette.TextSecondary)
        }
    }
}

/**
 * Mobile-Tailored Antigravity Prompt Card.
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

    Surface(
        color = AntigravityPalette.SurfaceDark,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AntigravityPalette.BorderCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Text Input Field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
            ) {
                if (uiState.promptInput.isEmpty()) {
                    Text(
                        text = "Ask anything, @ to mention, / for actions",
                        fontSize = 13.5.sp,
                        color = AntigravityPalette.TextMuted
                    )
                }

                BasicTextField(
                    value = uiState.promptInput,
                    onValueChange = onPromptChange,
                    textStyle = TextStyle(color = AntigravityPalette.TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(AntigravityPalette.AccentPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Model dropdown & review pills
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AntigravityPalette.SurfaceElevated)
                                .clickable { modelMenuExpanded = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = uiState.selectedModel.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = AntigravityPalette.TextPrimary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(Icons.Default.KeyboardArrowDown, null, tint = AntigravityPalette.TextMuted, modifier = Modifier.size(14.dp))
                        }

                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false },
                            modifier = Modifier.background(AntigravityPalette.SurfaceElevated)
                        ) {
                            uiState.availableModels.forEach { modelOption ->
                                DropdownMenuItem(
                                    text = {
                                        Text(modelOption.displayName, fontSize = 13.sp, color = AntigravityPalette.TextPrimary)
                                    },
                                    onClick = {
                                        onSelectModel(modelOption)
                                        modelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AntigravityPalette.SurfaceElevated)
                                .clickable { reviewMenuExpanded = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            ShieldCheckIcon(color = AntigravityPalette.TextSecondary, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(uiState.reviewPolicy.label, fontSize = 11.5.sp, color = AntigravityPalette.TextSecondary)
                        }

                        DropdownMenu(
                            expanded = reviewMenuExpanded,
                            onDismissRequest = { reviewMenuExpanded = false },
                            modifier = Modifier.background(AntigravityPalette.SurfaceElevated)
                        ) {
                            ReviewPolicy.entries.forEach { policy ->
                                DropdownMenuItem(
                                    text = { Text(policy.label, fontSize = 12.5.sp, color = AntigravityPalette.TextPrimary) },
                                    onClick = {
                                        onSelectReviewPolicy(policy)
                                        reviewMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Send Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.promptInput.isNotBlank()) AntigravityPalette.AccentPrimary
                            else AntigravityPalette.SendButtonCircleBg
                        )
                        .clickable(onClick = onSendPrompt)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Send",
                        tint = if (uiState.promptInput.isNotBlank()) Color.White else AntigravityPalette.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Antigravity Account & CLI Authentication Dialog.
 */
@Composable
fun AntigravitySignInDialog(
    authState: AuthState,
    currentAuthToken: String,
    detectedAuthUrl: String?,
    onSignInWithAntigravity: () -> Unit,
    onSaveAuthToken: (String) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    var tokenInput by remember(currentAuthToken) { mutableStateOf(currentAuthToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AntigravityPalette.SurfaceDark,
        titleContentColor = AntigravityPalette.TextPrimary,
        textContentColor = AntigravityPalette.TextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AntigravityArchLogo(modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (authState.isSignedIn) "Antigravity Account" else "Sign In to Antigravity",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (authState.isSignedIn) {
                    // Signed In Card
                    Surface(
                        color = AntigravityPalette.SurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AntigravityPalette.StatusActiveGreen.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(AntigravityPalette.AccentPrimary)
                                ) {
                                    Text(
                                        text = authState.displayName.take(1).ifEmpty { "A" },
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = authState.displayName.ifEmpty { "Antigravity User" },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AntigravityPalette.TextPrimary
                                    )
                                    Text(
                                        text = authState.userEmail.ifEmpty { "authenticated via agy" },
                                        fontSize = 12.sp,
                                        color = AntigravityPalette.TextMuted
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Authentication active: Antigravity CLI (agy v1.4.12) executes natively with full access.",
                                fontSize = 11.5.sp,
                                color = AntigravityPalette.TextSecondary
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AntigravityPalette.StatusErrorRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                } else {
                    // Option 1: Sign in with Antigravity
                    Button(
                        onClick = onSignInWithAntigravity,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AntigravityPalette.AccentPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        AntigravityArchLogo(modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sign in with Antigravity",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = AntigravityPalette.BorderSubtle)
                        Text(
                            text = " OR AUTH TOKEN ",
                            fontSize = 11.sp,
                            color = AntigravityPalette.TextMuted,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = AntigravityPalette.BorderSubtle)
                    }

                    // Option 2: Enter Antigravity Auth Token
                    Column {
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            placeholder = { Text("Paste Antigravity Auth Token / agy key", fontSize = 12.sp) },
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

                        if (!authState.authErrorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = authState.authErrorMessage,
                                fontSize = 11.sp,
                                color = AntigravityPalette.StatusErrorRed
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { onSaveAuthToken(tokenInput) },
                            enabled = tokenInput.isNotBlank() && !authState.isVerifying,
                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityPalette.SurfaceElevated),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Antigravity Auth Token", color = AntigravityPalette.TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AntigravityPalette.TextSecondary)
            }
        }
    )
}

/**
 * Terminal and Engine Logs Pane (Sheet on phone, side pane on tablet).
 */
@Composable
fun TerminalAndInspectorPane(
    uiState: AntigravityUiState,
    onRunTerminalCommand: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var cmdInput by remember { mutableStateOf("") }

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
                text = "ANTIGRAVITY CLI TERMINAL",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = AntigravityPalette.TextMuted
            )
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, null, tint = AntigravityPalette.TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick CLI Chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Chip("agy --version") { onRunTerminalCommand("agy --version") }
            Chip("agy models") { onRunTerminalCommand("agy models") }
            Chip("agy status") { onRunTerminalCommand("agy remote-control status") }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Terminal Log Console
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
                        color = if (line.contains("ERROR")) AntigravityPalette.DiffDelRed else AntigravityPalette.TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = cmdInput,
                onValueChange = { cmdInput = it },
                placeholder = { Text("$ agy --help", fontSize = 11.5.sp, fontFamily = FontFamily.Monospace) },
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
                    onRunTerminalCommand(cmdInput)
                    cmdInput = ""
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AntigravityPalette.SurfaceElevated)
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = AntigravityPalette.TextPrimary)
            }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
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
            Icon(Icons.Default.Folder, null, tint = AntigravityPalette.TextSecondary, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(activeProject.name, fontSize = 13.5.sp, color = AntigravityPalette.TextSecondary)
            Spacer(modifier = Modifier.width(3.dp))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = AntigravityPalette.TextMuted, modifier = Modifier.size(14.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AntigravityPalette.SurfaceElevated)
        ) {
            allProjects.forEach { proj ->
                DropdownMenuItem(
                    text = { Text(proj.name, fontSize = 13.sp, color = AntigravityPalette.TextPrimary) },
                    onClick = {
                        onSelectProject(proj.id)
                        expanded = false
                    }
                )
            }
        }
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onSelectProject)
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            Icon(Icons.Default.Folder, null, tint = if (project.isActive) AntigravityPalette.TextPrimary else AntigravityPalette.TextMuted, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(project.name, fontSize = 13.sp, color = if (project.isActive) AntigravityPalette.TextPrimary else AntigravityPalette.TextSecondary)
        }

        project.sessions.forEach { session ->
            val isSelected = session.id == selectedSessionId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) AntigravityPalette.SurfaceElevated else Color.Transparent)
                    .clickable { onSelectSession(session.id) }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(session.title, fontSize = 13.sp, color = AntigravityPalette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (session.hasActiveDot) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(AntigravityPalette.AccentBlueDot))
                }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(item: ChatTimelineItem.UserPromptItem) {
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = AntigravityPalette.SurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AntigravityPalette.BorderCard),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(item.prompt, fontSize = 13.5.sp, color = AntigravityPalette.TextPrimary, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun ThoughtTimelineRow(item: ChatTimelineItem.AgentThoughtCard, onToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle).padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Worked for ${item.workedSeconds}s", fontSize = 12.5.sp, color = AntigravityPalette.TextMuted)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(if (item.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = AntigravityPalette.TextMuted, modifier = Modifier.size(14.dp))
        }
        AnimatedVisibility(visible = item.isExpanded) {
            Surface(
                color = AntigravityPalette.SurfaceDark,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AntigravityPalette.BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    item.steps.forEach { step ->
                        Text("• $step", fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = AntigravityPalette.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffTimelineCard(item: ChatTimelineItem.GitDiffCard, onReview: () -> Unit) {
    Surface(
        color = AntigravityPalette.SurfaceDark,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AntigravityPalette.BorderCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(item.filePath.substringAfterLast('/'), fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, color = AntigravityPalette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.width(6.dp))
                Text("+${item.additions}", fontSize = 11.sp, color = AntigravityPalette.DiffAddGreen)
                Spacer(modifier = Modifier.width(4.dp))
                Text("-${item.deletions}", fontSize = 11.sp, color = AntigravityPalette.DiffDelRed)
            }
            Surface(
                color = AntigravityPalette.SurfaceElevated,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, AntigravityPalette.NewConvoButtonBorder),
                modifier = Modifier.clickable(onClick = onReview)
            ) {
                Text("Review", fontSize = 11.5.sp, color = AntigravityPalette.TextPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun AssistantMessageView(item: ChatTimelineItem.AgentResponseItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(item.markdownText, fontSize = 14.sp, lineHeight = 20.sp, color = AntigravityPalette.TextPrimary)
        if (!item.tokenUsageLabel.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.tokenUsageLabel, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AntigravityPalette.TextMuted)
        }
    }
}

@Composable
private fun GitDiffReviewDialog(diffCard: ChatTimelineItem.GitDiffCard, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AntigravityPalette.SurfaceDark,
        title = { Text("Changes • +${diffCard.additions} -${diffCard.deletions}") },
        text = {
            Surface(color = AntigravityPalette.BackgroundOled, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(diffCard.patchPreview, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AntigravityPalette.TextPrimary, modifier = Modifier.padding(10.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = AntigravityPalette.AccentPrimary) } }
    )
}

/* ================= Custom Icons ================= */

@Composable
private fun AntigravityArchLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.88f)
            cubicTo(w * 0.28f, h * 0.12f, w * 0.72f, h * 0.12f, w * 0.85f, h * 0.88f)
        }
        drawPath(path, color = Color(0xFFE4E6EB), style = Stroke(width = w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}


@Composable
private fun ShieldCheckIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
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
        drawPath(path, color = color, style = Stroke(width = 1.3.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Preview(name = "Mobile Phone Preview", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun AntigravityMobilePreview() {
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
            onSetSignInVisible = {},
            onSignInWithAntigravity = {},
            onSaveAuthToken = {},
            onSignOut = {},
            onSetTerminalSheetVisible = {},
            onRunTerminalCommand = {}
        )
    }
}
