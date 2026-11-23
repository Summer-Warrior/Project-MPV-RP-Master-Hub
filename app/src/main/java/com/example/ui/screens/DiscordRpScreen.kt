package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.DiscordResource
import com.example.data.util.BigIntMath
import com.example.data.util.JsonExportUtil
import com.example.ui.NexusViewModel
import com.example.ui.components.JsonExportActionRow
import com.example.ui.components.NexusPillBadge
import com.example.ui.components.NexusSectionHeader
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

@Composable
fun DiscordRpScreen(
    viewModel: NexusViewModel,
    modifier: Modifier = Modifier
) {
    val resources by viewModel.discordResources.collectAsStateWithLifecycle()
    val characters by viewModel.rpCharacters.collectAsStateWithLifecycle()
    val savedWebhook by viewModel.webhookUrl.collectAsStateWithLifecycle()
    val draft by viewModel.broadcastDraft.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var webhookInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddResourceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(savedWebhook) {
        if (webhookInput.isBlank() && savedWebhook.isNotBlank()) {
            webhookInput = savedWebhook
        }
    }

    val filteredResources = remember(resources, selectedCategory) {
        if (selectedCategory == "All") resources
        else resources.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NexusBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            NexusSectionHeader(
                title = "Discord RP & Bots",
                subtitle = "RP server directory, OAuth2 bot invites & local Webhook broadcaster",
                trailing = {
                    Button(
                        onClick = { showAddResourceDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground),
                        modifier = Modifier.testTag("add_discord_resource_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }

        // Security Note Banner (Webhooks vs Bot Tokens)
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = NexusGold.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, NexusGold.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Security, contentDescription = "Security notice", tint = NexusGold, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.webhook_security_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusTextPrimary
                    )
                }
            }
        }

        // Discord Webhook RP Broadcaster Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = NexusCard,
                border = BorderStroke(1.dp, NexusViolet.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WEBHOOK RP BROADCASTER", style = MaterialTheme.typography.labelSmall, color = NexusViolet)
                        NexusPillBadge(text = "LOCAL STORAGE ONLY", accentColor = NexusCyan)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = webhookInput,
                            onValueChange = { webhookInput = it },
                            label = { Text("Discord Webhook URL (https://discord.com/api/webhooks/...)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("webhook_url_input")
                        )
                        IconButton(
                            onClick = { viewModel.saveWebhookUrl(webhookInput) },
                            modifier = Modifier.testTag("save_webhook_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save webhook URL locally", tint = NexusCyan)
                        }
                    }

                    // Quick Persona Fill Chips
                    if (characters.isNotEmpty()) {
                        Text("QUICK PERSONA FILL", style = MaterialTheme.typography.labelSmall, color = NexusTextMuted)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            characters.forEach { persona ->
                                OutlinedButton(
                                    onClick = {
                                        viewModel.updateBroadcastDraft {
                                            it.copy(
                                                username = persona.name,
                                                avatarUrl = persona.avatarUrl,
                                                title = "${persona.name} — Summon Chant",
                                                body = "> \"${persona.summonChant}\"\n\n*${persona.catchphrase}*",
                                                footer = "${persona.faction} · Nexus Master Hub",
                                                fields = listOf("LP" to BigIntMath.formatWithCommas(persona.currentLp))
                                            )
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, NexusViolet.copy(alpha = 0.5f))
                                ) {
                                    Text(persona.name, style = MaterialTheme.typography.labelSmall, color = NexusViolet)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = draft.username,
                        onValueChange = { v -> viewModel.updateBroadcastDraft { it.copy(username = v) } },
                        label = { Text("Character / Sender Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { v -> viewModel.updateBroadcastDraft { it.copy(title = v) } },
                        label = { Text("Embed Title (Dialogue, Summon, or Duel Result)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("webhook_title_input")
                    )

                    OutlinedTextField(
                        value = draft.body,
                        onValueChange = { v -> viewModel.updateBroadcastDraft { it.copy(body = v) } },
                        label = { Text("RP Message / Summon Chant / Deck Summary") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("webhook_body_input")
                    )

                    // Live Rich Embed Preview
                    if (draft.title.isNotBlank() || draft.body.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NexusBackground,
                            border = BorderStroke(1.dp, NexusBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(86.dp)
                                        .background(NexusCyan)
                                )
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = draft.username.ifBlank { "Nexus Arbiter" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexusGold
                                    )
                                    Text(
                                        text = draft.title.ifBlank { "Untitled Embed" },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = NexusTextPrimary
                                    )
                                    Text(
                                        text = draft.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NexusTextSecondary,
                                        maxLines = 4
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        enabled = !isBusy && draft.title.isNotBlank() && draft.body.isNotBlank(),
                        onClick = {
                            viewModel.sendDiscordBroadcast(webhookInput, draft) { _, _ -> }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_webhook_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NexusViolet, contentColor = NexusBackground)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NexusBackground, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sending Embed...")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Broadcast Rich Embed to Discord")
                        }
                    }
                }
            }
        }

        // Category Filter for bots.json Directory
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("REGISTERED SERVERS, BOTS & RP TOOLS (bots.json)", style = MaterialTheme.typography.labelSmall, color = NexusCyan)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "RP Server", "Bot Invite", "RP Tool").forEach { cat ->
                        val selected = selectedCategory == cat
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelMedium) },
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

        items(filteredResources, key = { it.id }) { resource ->
            DiscordResourceCard(
                resource = resource,
                onOpenLink = {
                    openSafeExternalUrl(context, resource.inviteUrl) { err -> viewModel.showStatus(err) }
                },
                onDelete = if (resource.isUserCreated) {
                    { viewModel.deleteDiscordResource(resource.id) }
                } else null,
                onFeedback = { viewModel.showStatus(it) }
            )
        }
    }

    if (showAddResourceDialog) {
        AddDiscordResourceDialog(
            onDismiss = { showAddResourceDialog = false },
            onSave = { entry ->
                viewModel.saveDiscordResource(entry)
                showAddResourceDialog = false
            }
        )
    }
}

@Composable
private fun DiscordResourceCard(
    resource: DiscordResource,
    onOpenLink: () -> Unit,
    onDelete: (() -> Unit)?,
    onFeedback: (String) -> Unit
) {
    val json = remember(resource) { JsonExportUtil.discordResourceToJson(resource) }
    val accent = when (resource.category) {
        "RP Server" -> NexusViolet
        "Bot Invite" -> NexusCyan
        else -> NexusGold
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NexusCard,
        border = BorderStroke(1.dp, NexusBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discord_resource_${resource.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NexusPillBadge(text = resource.category, accentColor = accent)
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete entry", tint = NexusDanger)
                    }
                }
            }
            Text(resource.name, style = MaterialTheme.typography.titleLarge, color = NexusTextPrimary)
            Text(resource.description, style = MaterialTheme.typography.bodySmall, color = NexusTextSecondary)

            Button(
                onClick = onOpenLink,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NexusSurface, contentColor = accent),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (resource.category) {
                        "Bot Invite" -> "Open Bot OAuth2 Invite"
                        "RP Server" -> "Join RP Server"
                        else -> "Open RP Tool"
                    }
                )
            }

            JsonExportActionRow(
                jsonPayload = json,
                exportTitle = resource.id,
                onFeedback = onFeedback
            )
        }
    }
}

@Composable
private fun AddDiscordResourceDialog(
    onDismiss: () -> Unit,
    onSave: (DiscordResource) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("RP Server") }
    var description by remember { mutableStateOf("") }
    var inviteUrl by remember { mutableStateOf("https://discord.com/") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusCard,
        title = { Text("Add Discord Server / Bot / Tool", color = NexusTextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("RP Server", "Bot Invite", "RP Tool").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = inviteUrl,
                    onValueChange = { inviteUrl = it; errorText = null },
                    label = { Text("HTTPS Invite or Tool URL") },
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
                errorText?.let { Text(it, color = NexusDanger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isBlank() || !inviteUrl.trim().startsWith("https://")) {
                        errorText = "Provide a name and valid https:// URL."
                        return@Button
                    }
                    val slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    onSave(
                        DiscordResource(
                            id = "disc-${slug.ifBlank { "entry" }}-${UUID.randomUUID().toString().take(4)}",
                            name = name.trim(),
                            category = category,
                            description = description.trim().ifBlank { "Community Discord RP resource." },
                            inviteUrl = inviteUrl.trim(),
                            tags = listOf(category),
                            isUserCreated = true
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground)
            ) { Text("Save Entry") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = NexusTextSecondary) }
        }
    )
}
