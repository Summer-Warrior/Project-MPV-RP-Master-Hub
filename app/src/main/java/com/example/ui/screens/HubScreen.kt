package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AccessTier
import com.example.data.model.AppEntry
import com.example.data.util.JsonExportUtil
import com.example.ui.NexusViewModel
import com.example.ui.components.JsonExportActionRow
import com.example.ui.components.NexusEmptyStateCard
import com.example.ui.components.NexusPillBadge
import com.example.ui.theme.NexusBackground
import com.example.ui.theme.NexusBorder
import com.example.ui.theme.NexusCard
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusGold
import com.example.ui.theme.NexusSuccess
import com.example.ui.theme.NexusSurface
import com.example.ui.theme.NexusTextMuted
import com.example.ui.theme.NexusTextPrimary
import com.example.ui.theme.NexusTextSecondary
import com.example.ui.theme.NexusViolet
import java.util.UUID

private val PLATFORM_FILTERS = listOf("All", "Android", "Web", "iOS", "Windows", "macOS", "Linux", "Discord")
private val TYPE_FILTERS = listOf(
    "All", "TCG_TOOL", "TCG_CLIENT", "LORE_WIKI", "ASSET_VAULT", "ENGINE_SANDBOX", "RUNTIME", "STORE", "ANIMATION", "Official Client", "Web Game"
)

@Composable
fun HubScreen(
    viewModel: NexusViewModel,
    modifier: Modifier = Modifier
) {
    // STRICT STEALTH HIDING: visibleApps already strips hiddenFromUnauthorized apps before reaching UI
    val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val articles by viewModel.wikiArticles.collectAsStateWithLifecycle()
    val effectiveTier by viewModel.effectiveTier.collectAsStateWithLifecycle()
    val session by viewModel.authSession.collectAsStateWithLifecycle()
    val previewOverride by viewModel.previewOverrideTier.collectAsStateWithLifecycle()
    val notifications by viewModel.loginNotifications.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedPlatform by remember { mutableStateOf("All") }
    var selectedType by remember { mutableStateOf("All") }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var exportingApp by remember { mutableStateOf<AppEntry?>(null) }

    val unreadAlerts = remember(notifications) { notifications.count { !it.isRead } }

    val filteredApps = remember(apps, selectedPlatform, selectedType) {
        apps.filter { app ->
            val platformMatch = selectedPlatform == "All" ||
                app.platforms.any { it.equals(selectedPlatform, ignoreCase = true) }
            val typeMatch = selectedType == "All" ||
                app.type.equals(selectedType, ignoreCase = true)
            platformMatch && typeMatch
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NexusBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Account, Sync, Notifications & Stealth Admin Bar
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NexusSurface,
                border = BorderStroke(1.dp, NexusBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.openAuthDialog() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, if (session.isSignedIn) NexusCyan else NexusViolet),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (session.isSignedIn) NexusCyan else NexusViolet
                            ),
                            modifier = Modifier.testTag("hub_auth_button")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (session.isSignedIn) effectiveTier.name else "Sign In",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (previewOverride != null) {
                            NexusPillBadge(text = "PREVIEW", accentColor = NexusGold)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.syncSatelliteRepos() },
                            modifier = Modifier.testTag("hub_sync_github_button")
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = "Sync Summer-Warrior GitHub repos", tint = NexusCyan)
                        }

                        IconButton(
                            onClick = { viewModel.openNotificationsDialog() },
                            modifier = Modifier.testTag("hub_notifications_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadAlerts > 0) {
                                        Badge(containerColor = NexusGold, contentColor = NexusBackground) {
                                            Text(unreadAlerts.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = "Login notifications", tint = NexusGold)
                            }
                        }

                        // STRICT STEALTH: Owner/Admin Auditor Console icon is 100% hidden from unauthorized users
                        if (effectiveTier.isAdminOrOwner) {
                            IconButton(
                                onClick = { viewModel.openOwnerAuditorSheet() },
                                modifier = Modifier.testTag("hub_stealth_admin_button")
                            ) {
                                Icon(
                                    Icons.Default.AdminPanelSettings,
                                    contentDescription = "Owner & Admin Source Auditor",
                                    tint = NexusViolet
                                )
                            }
                        }
                    }
                }
            }
        }

        // Hero Command Banner
        item {
            HubHeroBanner(
                appCount = apps.size,
                cardCount = cards.size,
                loreCount = articles.size,
                onLaunchTabletop = { viewModel.selectTab(2) },
                onRegisterApp = { showAddAppDialog = true },
                onExportAllRegistry = {
                    val fullJson = JsonExportUtil.appsListToJson(apps)
                    JsonExportUtil.copyToClipboard(context, "apps.json", fullJson)
                    viewModel.showStatus("Copied visible apps.json registry (${apps.size} entries).")
                }
            )
        }

        // Platform Filter Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PLATFORM DIRECTORY", style = MaterialTheme.typography.labelSmall, color = NexusCyan)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PLATFORM_FILTERS.forEach { platform ->
                        val selected = selectedPlatform == platform
                        FilterChip(
                            selected = selected,
                            onClick = { selectedPlatform = platform },
                            label = { Text(platform, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("filter_platform_${platform.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NexusCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NexusCyan,
                                containerColor = NexusSurface,
                                labelColor = NexusTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = NexusBorder,
                                selectedBorderColor = NexusCyan
                            )
                        )
                    }
                }
            }
        }

        // Type Filter Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SATELLITE & CLIENT TYPE", style = MaterialTheme.typography.labelSmall, color = NexusGold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TYPE_FILTERS.forEach { type ->
                        val selected = selectedType == type
                        FilterChip(
                            selected = selected,
                            onClick = { selectedType = type },
                            label = { Text(type, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("filter_type_${type.lowercase().replace(" ", "_")}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NexusGold.copy(alpha = 0.2f),
                                selectedLabelColor = NexusGold,
                                containerColor = NexusSurface,
                                labelColor = NexusTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = NexusBorder,
                                selectedBorderColor = NexusGold
                            )
                        )
                    }
                }
            }
        }

        if (filteredApps.isEmpty()) {
            item {
                NexusEmptyStateCard(
                    title = "No apps match this filter",
                    subtitle = "Select 'All' or sync Summer-Warrior satellite repos."
                )
            }
        } else {
            items(filteredApps, key = { it.id }) { app ->
                val installed = remember(app.id, app.deepLinkScheme, app.androidPackageName) {
                    viewModel.isInstalledOnDevice(context, app)
                }
                val authorized = viewModel.isAuthorizedToLaunch(app)
                HubAppCard(
                    app = app,
                    isInstalled = installed,
                    isAuthorized = authorized,
                    onTogglePin = { viewModel.togglePinApp(app) },
                    onRouteAction = { viewModel.onAppCardTapped(context, app) },
                    onShowJson = { exportingApp = app },
                    onDelete = if (app.isUserCreated) {
                        { viewModel.deleteUserApp(app.id) }
                    } else null
                )
            }
        }
    }

    if (showAddAppDialog) {
        RegisterAppManifestDialog(
            onDismiss = { showAddAppDialog = false },
            onSave = { entry ->
                viewModel.saveUserApp(entry)
                showAddAppDialog = false
                exportingApp = entry
            }
        )
    }

    exportingApp?.let { target ->
        val json = remember(target) { JsonExportUtil.appToJson(target) }
        AlertDialog(
            onDismissRequest = { exportingApp = null },
            containerColor = NexusCard,
            title = { Text("App Manifest JSON", style = MaterialTheme.typography.titleLarge, color = NexusTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste into nexus-app.json or registry/apps.json in GitHub:",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusTextSecondary
                    )
                    Surface(
                        color = NexusBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NexusBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        Text(
                            text = json,
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusCyan,
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp)
                        )
                    }
                    JsonExportActionRow(
                        jsonPayload = json,
                        exportTitle = target.id,
                        onFeedback = { viewModel.showStatus(it) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { exportingApp = null }) { Text("Done", color = NexusCyan) }
            }
        )
    }
}

@Composable
private fun HubHeroBanner(
    appCount: Int,
    cardCount: Int,
    loreCount: Int,
    onLaunchTabletop: () -> Unit,
    onRegisterApp: () -> Unit,
    onExportAllRegistry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, NexusCyan.copy(alpha = 0.35f)),
        color = NexusCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hub_banner_1795407674042),
                contentDescription = "Project MPV RP Master Hub Command Bridge",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                NexusBackground.copy(alpha = 0.35f),
                                NexusBackground.copy(alpha = 0.88f),
                                NexusCard
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusPillBadge(text = "SATELLITE ECOSYSTEM · GITHUB RELEASES", accentColor = NexusCyan)
                    OutlinedButton(
                        onClick = onExportAllRegistry,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, NexusGold.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusGold),
                        modifier = Modifier.testTag("export_all_apps_json")
                    ) {
                        Icon(Icons.Default.Code, contentDescription = "Copy apps.json", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("apps.json", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Text(
                    text = "Project MPV Master Hub",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NexusTextPrimary
                )
                Text(
                    text = "3-case satellite router (Launch Installed -> GitHub Release APK -> Web Portal). Zero Play Store links for public downloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusTextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatMiniTile("Satellites", appCount.toString(), NexusCyan, Modifier.weight(1f))
                    StatMiniTile("Vault Cards", cardCount.toString(), NexusGold, Modifier.weight(1f))
                    StatMiniTile("Lore Pages", loreCount.toString(), NexusViolet, Modifier.weight(1f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onLaunchTabletop,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hero_launch_tabletop"),
                        colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play Tabletop", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onRegisterApp,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("register_app_button"),
                        border = BorderStroke(1.dp, NexusViolet),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusViolet)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add App", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = NexusSurface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = NexusTextMuted)
        }
    }
}

@Composable
private fun HubAppCard(
    app: AppEntry,
    isInstalled: Boolean,
    isAuthorized: Boolean,
    onTogglePin: () -> Unit,
    onRouteAction: () -> Unit,
    onShowJson: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val typeAccent = when {
        app.hiddenFromUnauthorized -> NexusViolet
        app.lockedUpstream -> NexusGold
        app.accessTier == "OWNER_ONLY" -> NexusDanger
        app.launchInApp -> NexusCyan
        else -> NexusCyan
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = NexusCard,
        border = BorderStroke(
            width = if (app.isPinned || app.hiddenFromUnauthorized) 1.5.dp else 1.dp,
            color = when {
                app.hiddenFromUnauthorized -> NexusViolet.copy(alpha = 0.75f)
                app.isPinned -> NexusGold.copy(alpha = 0.65f)
                else -> NexusBorder
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_card_${app.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NexusPillBadge(text = app.type, accentColor = typeAccent)
                        if (isInstalled) {
                            NexusPillBadge(text = "INSTALLED", accentColor = NexusSuccess)
                        }
                        if (app.accessTier == "OWNER_ONLY") {
                            NexusPillBadge(text = "OWNER_ONLY", accentColor = NexusDanger)
                        }
                        if (app.hiddenFromUnauthorized) {
                            NexusPillBadge(text = "STEALTH ADMIN", accentColor = NexusViolet)
                        }
                    }
                    Text(text = app.name, style = MaterialTheme.typography.titleLarge, color = NexusTextPrimary)
                    if (app.lockedUpstream) {
                        NexusPillBadge(
                            text = "LOCKED TO UPSTREAM GITHUB SOURCE (Base Rules Immutable)",
                            accentColor = NexusGold
                        )
                    } else if (app.subtitle.isNotBlank()) {
                        Text(text = app.subtitle, style = MaterialTheme.typography.labelMedium, color = typeAccent)
                    }
                }
                Row {
                    IconButton(onClick = onTogglePin, modifier = Modifier.testTag("pin_app_${app.id}")) {
                        Icon(
                            imageVector = if (app.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (app.isPinned) "Unpin app" else "Pin app",
                            tint = if (app.isPinned) NexusGold else NexusTextMuted
                        )
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete custom app", tint = NexusDanger)
                        }
                    }
                }
            }

            Text(text = app.description, style = MaterialTheme.typography.bodyMedium, color = NexusTextSecondary)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                app.platforms.forEach { platform ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NexusSurface,
                        border = BorderStroke(1.dp, NexusBorder)
                    ) {
                        Text(
                            text = platform,
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusTextPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val ctaLabel = when {
                    app.launchInApp -> "Launch Built-In"
                    !isAuthorized -> "Owner / Tier Sign-In Required"
                    app.lockedUpstream -> "Open Upstream GitHub"
                    isInstalled -> "Open Installed App"
                    else -> "Launch / GitHub Release"
                }
                Button(
                    onClick = onRouteAction,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("route_app_${app.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            !isAuthorized -> NexusSurface
                            app.launchInApp || isInstalled -> NexusCyan
                            else -> NexusSurface
                        },
                        contentColor = when {
                            !isAuthorized -> NexusGold
                            app.launchInApp || isInstalled -> NexusBackground
                            else -> NexusCyan
                        }
                    ),
                    border = if (!app.launchInApp && !isInstalled) BorderStroke(1.dp, if (isAuthorized) NexusCyan.copy(alpha = 0.6f) else NexusGold.copy(alpha = 0.6f)) else null
                ) {
                    Icon(
                        imageVector = when {
                            !isAuthorized -> Icons.Default.Lock
                            app.launchInApp || isInstalled -> Icons.Default.PlayArrow
                            else -> Icons.AutoMirrored.Filled.OpenInNew
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(ctaLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onShowJson,
                    border = BorderStroke(1.dp, NexusBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusTextSecondary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("json_app_${app.id}")
                ) {
                    Icon(Icons.Default.Code, contentDescription = "View JSON", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("JSON", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun RegisterAppManifestDialog(
    onDismiss: () -> Unit,
    onSave: (AppEntry) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("TCG_TOOL") }
    var deepLinkScheme by remember { mutableStateOf("nexus-custom-app://open") }
    var externalUrl by remember { mutableStateOf("https://github.com/Summer-Warrior/") }
    val selectedPlatforms = remember { mutableStateListOf("Android", "Web") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusCard,
        title = { Text("Register Satellite App Manifest", color = NexusTextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("App / Satellite Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Subtitle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deepLinkScheme,
                    onValueChange = { deepLinkScheme = it },
                    label = { Text("Deep-Link Scheme (e.g. nexus-app://open)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = externalUrl,
                    onValueChange = { externalUrl = it; errorText = null },
                    label = { Text("HTTPS GitHub Repo / Web URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("TCG_TOOL", "TCG_CLIENT", "LORE_WIKI", "ASSET_VAULT", "ENGINE_SANDBOX").forEach { t ->
                        FilterChip(selected = selectedType == t, onClick = { selectedType = t }, label = { Text(t, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PLATFORM_FILTERS.drop(1).forEach { p ->
                        val active = p in selectedPlatforms
                        FilterChip(
                            selected = active,
                            onClick = { if (active) selectedPlatforms.remove(p) else selectedPlatforms.add(p) },
                            label = { Text(p, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                errorText?.let { Text(it, color = NexusDanger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val trimmedUrl = externalUrl.trim()
                    if (trimmedName.isBlank() || (trimmedUrl.isNotBlank() && !trimmedUrl.startsWith("https://"))) {
                        errorText = "Provide a name and valid https:// URL."
                        return@Button
                    }
                    val slug = trimmedName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    onSave(
                        AppEntry(
                            id = "${slug.ifBlank { "app" }}-${UUID.randomUUID().toString().take(4)}",
                            name = trimmedName,
                            subtitle = subtitle.trim().ifBlank { selectedType },
                            description = description.trim().ifBlank { "Community registered satellite manifest." },
                            type = selectedType,
                            platforms = selectedPlatforms.toList().ifEmpty { listOf("Web") },
                            deepLinkScheme = deepLinkScheme.trim(),
                            webUrl = trimmedUrl,
                            externalUrl = trimmedUrl,
                            launchInApp = false,
                            version = "Custom",
                            tags = listOf(selectedType),
                            isUserCreated = true
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground)
            ) { Text("Save & Export JSON") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = NexusTextSecondary) }
        }
    )
}
