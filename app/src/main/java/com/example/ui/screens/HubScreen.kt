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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
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
import com.example.data.model.AppEntry
import com.example.data.util.JsonExportUtil
import com.example.ui.NexusViewModel
import com.example.ui.components.JsonExportActionRow
import com.example.ui.components.NexusEmptyStateCard
import com.example.ui.components.NexusPillBadge
import com.example.ui.components.openSafeExternalUrl
import com.example.ui.theme.NexusBackground
import com.example.ui.theme.NexusBorder
import com.example.ui.theme.NexusCard
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusGold
import com.example.ui.theme.NexusSurface
import com.example.ui.theme.NexusTextMuted
import com.example.ui.theme.NexusTextPrimary
import com.example.ui.theme.NexusTextSecondary
import com.example.ui.theme.NexusViolet
import java.util.UUID

private val PLATFORM_FILTERS = listOf("All", "Web", "Android", "iOS", "Windows", "macOS", "Linux", "Discord")
private val TYPE_FILTERS = listOf("All", "Official Client", "Custom Clone", "Web Game", "Tool")

@Composable
fun HubScreen(
    viewModel: NexusViewModel,
    modifier: Modifier = Modifier
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val articles by viewModel.wikiArticles.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedPlatform by remember { mutableStateOf("All") }
    var selectedType by remember { mutableStateOf("All") }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var exportingApp by remember { mutableStateOf<AppEntry?>(null) }

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
                    viewModel.showStatus("Copied full apps.json registry (${apps.size} entries).")
                }
            )
        }

        // Platform Filter Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PLATFORM DIRECTORY",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusCyan
                )
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
                Text(
                    text = "CLIENT & APP TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusGold
                )
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
                    title = "No clients match this filter",
                    subtitle = "Try selecting 'All' platforms or register a custom app manifest."
                )
            }
        } else {
            items(filteredApps, key = { it.id }) { app ->
                HubAppCard(
                    app = app,
                    onTogglePin = { viewModel.togglePinApp(app) },
                    onLaunchInApp = {
                        if (app.id.contains("vault", ignoreCase = true)) {
                            viewModel.selectTab(1)
                        } else {
                            viewModel.selectTab(2)
                        }
                    },
                    onOpenExternal = {
                        openSafeExternalUrl(context, app.externalUrl) { err ->
                            viewModel.showStatus(err)
                        }
                    },
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
            title = {
                Text(
                    text = "App Manifest JSON",
                    style = MaterialTheme.typography.titleLarge,
                    color = NexusTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste into registry/apps.json in your GitHub repository:",
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
                TextButton(onClick = { exportingApp = null }) {
                    Text("Done", color = NexusCyan)
                }
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
                contentDescription = "Nexus Master Hub Command Bridge",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(205.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(205.dp)
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
                    NexusPillBadge(text = "DATA-DRIVEN REGISTRY", accentColor = NexusCyan)
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
                    text = "Nexus Master Hub",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NexusTextPrimary
                )
                Text(
                    text = "Honest upstream links for official locked clients plus an instant built-in Multi-TCG Tabletop & Infinite Card Vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusTextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatMiniTile("Clients", appCount.toString(), NexusCyan, Modifier.weight(1f))
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexusCyan,
                            contentColor = NexusBackground
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Tabletop", maxLines = 1)
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
    onTogglePin: () -> Unit,
    onLaunchInApp: () -> Unit,
    onOpenExternal: () -> Unit,
    onShowJson: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val typeAccent = when (app.type) {
        "Official Client" -> NexusGold
        "Web Game" -> NexusCyan
        "Custom Clone" -> NexusViolet
        else -> NexusCyan
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = NexusCard,
        border = BorderStroke(
            width = if (app.isPinned) 1.5.dp else 1.dp,
            color = if (app.isPinned) NexusGold.copy(alpha = 0.65f) else NexusBorder
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
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NexusPillBadge(text = app.type, accentColor = typeAccent)
                        if (app.version.isNotBlank()) {
                            Text(
                                text = app.version,
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusTextMuted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = NexusTextPrimary
                    )
                    if (app.subtitle.isNotBlank()) {
                        Text(
                            text = app.subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = typeAccent
                        )
                    }
                }
                Row {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.testTag("pin_app_${app.id}")
                    ) {
                        Icon(
                            imageVector = if (app.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (app.isPinned) "Unpin app" else "Pin app",
                            tint = if (app.isPinned) NexusGold else NexusTextMuted
                        )
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete custom app",
                                tint = NexusDanger
                            )
                        }
                    }
                }
            }

            Text(
                text = app.description,
                style = MaterialTheme.typography.bodyMedium,
                color = NexusTextSecondary
            )

            // Platform Badges Row
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

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (app.launchInApp) {
                    Button(
                        onClick = onLaunchInApp,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("launch_inapp_${app.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexusCyan,
                            contentColor = NexusBackground
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Launch Now", maxLines = 1)
                    }
                } else if (app.externalUrl.isNotBlank()) {
                    Button(
                        onClick = onOpenExternal,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_external_${app.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexusSurface,
                            contentColor = NexusCyan
                        ),
                        border = BorderStroke(1.dp, NexusCyan.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open GitHub / Releases", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
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
    var selectedType by remember { mutableStateOf("Custom Clone") }
    var externalUrl by remember { mutableStateOf("https://github.com/") }
    val selectedPlatforms = remember { mutableStateListOf("Android", "Web") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusCard,
        title = { Text("Register App Manifest", color = NexusTextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("App / Client Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_app_name")
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Subtitle (e.g. Custom TCG Client)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = externalUrl,
                    onValueChange = { externalUrl = it; errorText = null },
                    label = { Text("HTTPS Repo / Download URL") },
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

                Text("App Type", style = MaterialTheme.typography.labelSmall, color = NexusGold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Official Client", "Custom Clone", "Web Game", "Tool").forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = { selectedType = t },
                            label = { Text(t, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Platforms", style = MaterialTheme.typography.labelSmall, color = NexusCyan)
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

                errorText?.let {
                    Text(it, color = NexusDanger, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val trimmedUrl = externalUrl.trim()
                    if (trimmedName.isBlank()) {
                        errorText = "App name is required."
                        return@Button
                    }
                    if (trimmedUrl.isNotBlank() && !trimmedUrl.startsWith("https://")) {
                        errorText = "URL must start with https://"
                        return@Button
                    }
                    val slug = trimmedName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    onSave(
                        AppEntry(
                            id = "${slug.ifBlank { "app" }}-${UUID.randomUUID().toString().take(4)}",
                            name = trimmedName,
                            subtitle = subtitle.trim().ifBlank { selectedType },
                            description = description.trim().ifBlank { "Community registered manifest." },
                            type = selectedType,
                            platforms = selectedPlatforms.toList().ifEmpty { listOf("Web") },
                            externalUrl = trimmedUrl,
                            launchInApp = false,
                            version = "Custom",
                            tags = listOf(selectedType),
                            isUserCreated = true
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground),
                modifier = Modifier.testTag("save_app_manifest_button")
            ) {
                Text("Save & Export JSON")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NexusTextSecondary)
            }
        }
    )
}
