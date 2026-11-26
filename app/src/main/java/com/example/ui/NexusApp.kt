package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.CardVaultScreen
import com.example.ui.screens.DiscordAuthDialog
import com.example.ui.screens.DiscordRpScreen
import com.example.ui.screens.GitHubDownloadSheetDialog
import com.example.ui.screens.HubScreen
import com.example.ui.screens.LoginNotificationsDialog
import com.example.ui.screens.LoreWikiScreen
import com.example.ui.screens.OwnerAdminAuditorDialog
import com.example.ui.screens.TabletopScreen
import com.example.ui.theme.NexusBackground
import com.example.ui.theme.NexusCard
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusCyanContainer
import com.example.ui.theme.NexusSurface
import com.example.ui.theme.NexusTextMuted
import com.example.ui.theme.NexusTextPrimary

private data class NavSection(
    val index: Int,
    val label: String,
    val testTag: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val NEXUS_NAV_SECTIONS = listOf(
    NavSection(0, "Hub", "nav_hub", Icons.Filled.Hub, Icons.Outlined.Hub),
    NavSection(1, "Vault", "nav_vault", Icons.Filled.Style, Icons.Outlined.Style),
    NavSection(2, "Tabletop", "nav_tabletop", Icons.Filled.GridView, Icons.Outlined.GridView),
    NavSection(3, "Lore", "nav_lore", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    NavSection(4, "Discord", "nav_discord", Icons.Filled.Forum, Icons.Outlined.Forum)
)

@Composable
fun NexusMasterHubApp(viewModel: NexusViewModel) {
    val selectedIndex by viewModel.selectedNavIndex.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val showAuthDialog by viewModel.showAuthDialog.collectAsStateWithLifecycle()
    val showNotificationsDialog by viewModel.showNotificationsDialog.collectAsStateWithLifecycle()
    val showOwnerAuditorSheet by viewModel.showOwnerAuditorSheet.collectAsStateWithLifecycle()
    val effectiveTier by viewModel.effectiveTier.collectAsStateWithLifecycle()
    val pendingDownload by viewModel.pendingDownloadSheet.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    BackHandler(enabled = selectedIndex != 0) {
        viewModel.selectTab(0)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusBackground)
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        if (useNavigationRail) {
            Scaffold(
                containerColor = NexusBackground,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = {
                    SnackbarHost(snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = NexusCard,
                            contentColor = NexusTextPrimary,
                            actionColor = NexusCyan
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = NexusSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        NEXUS_NAV_SECTIONS.forEach { section ->
                            val selected = selectedIndex == section.index
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(section.index) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) section.selectedIcon else section.unselectedIcon,
                                        contentDescription = section.label
                                    )
                                },
                                label = { Text(section.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = NexusCyan,
                                    selectedTextColor = NexusCyan,
                                    indicatorColor = NexusCyanContainer,
                                    unselectedIconColor = NexusTextMuted,
                                    unselectedTextColor = NexusTextMuted
                                ),
                                modifier = Modifier.testTag(section.testTag)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 860.dp)
                        ) {
                            NexusSectionHost(selectedIndex = selectedIndex, viewModel = viewModel)
                        }
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = NexusBackground,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = {
                    SnackbarHost(snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = NexusCard,
                            contentColor = NexusTextPrimary,
                            actionColor = NexusCyan
                        )
                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = NexusSurface,
                        tonalElevation = 0.dp
                    ) {
                        NEXUS_NAV_SECTIONS.forEach { section ->
                            val selected = selectedIndex == section.index
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(section.index) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) section.selectedIcon else section.unselectedIcon,
                                        contentDescription = section.label
                                    )
                                },
                                label = { Text(section.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NexusCyan,
                                    selectedTextColor = NexusCyan,
                                    indicatorColor = NexusCyanContainer,
                                    unselectedIconColor = NexusTextMuted,
                                    unselectedTextColor = NexusTextMuted
                                ),
                                modifier = Modifier.testTag(section.testTag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NexusSectionHost(selectedIndex = selectedIndex, viewModel = viewModel)
                }
            }
        }
    }

    // Global Modals (Auth, Case 2 GitHub Download Sheet, Owner Login Alerts, Stealth Admin Auditor)
    if (showAuthDialog) {
        DiscordAuthDialog(viewModel = viewModel, onDismiss = { viewModel.dismissAuthDialog() })
    }

    pendingDownload?.let { decision ->
        GitHubDownloadSheetDialog(
            decision = decision,
            onDownloadFromGitHub = { url -> viewModel.openGitHubReleaseAssetDownload(context, url) },
            onOpenWebFallback = { viewModel.openWebFallbackFromDownloadSheet(context, decision.app) },
            onDismiss = { viewModel.dismissDownloadSheet() }
        )
    }

    if (showNotificationsDialog) {
        LoginNotificationsDialog(viewModel = viewModel, onDismiss = { viewModel.dismissNotificationsDialog() })
    }

    if (showOwnerAuditorSheet && effectiveTier.isAdminOrOwner) {
        OwnerAdminAuditorDialog(viewModel = viewModel, onDismiss = { viewModel.dismissOwnerAuditorSheet() })
    }
}

@Composable
private fun NexusSectionHost(
    selectedIndex: Int,
    viewModel: NexusViewModel
) {
    when (selectedIndex) {
        0 -> HubScreen(viewModel = viewModel)
        1 -> CardVaultScreen(viewModel = viewModel)
        2 -> TabletopScreen(viewModel = viewModel)
        3 -> LoreWikiScreen(viewModel = viewModel)
        else -> DiscordRpScreen(viewModel = viewModel)
    }
}
