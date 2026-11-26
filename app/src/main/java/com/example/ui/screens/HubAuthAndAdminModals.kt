package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.NexusConfigPrefs
import com.example.data.model.AccessTier
import com.example.data.router.AppRouteDecision
import com.example.ui.NexusViewModel
import com.example.ui.components.NexusPillBadge
import com.example.ui.theme.MonoFontFamily
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

@Composable
fun DiscordAuthDialog(
    viewModel: NexusViewModel,
    onDismiss: () -> Unit
) {
    val session by viewModel.authSession.collectAsStateWithLifecycle()
    val config by viewModel.configPrefs.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var handle by remember(session.discordHandle) { mutableStateOf(session.discordHandle) }
    var snowflakeId by remember(session.discordUserId) { mutableStateOf(session.discordUserId) }
    var email by remember(session.email) { mutableStateOf(session.email) }
    var discordClientId by remember(config.discordClientId) { mutableStateOf(config.discordClientId) }
    var hasGuildRole by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Security, contentDescription = null, tint = NexusViolet)
                Text("Discord OAuth2 + Email Lock", style = MaterialTheme.typography.titleLarge, color = NexusTextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NexusSurface,
                    border = BorderStroke(1.dp, NexusCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (session.isSignedIn) "Signed in as ${session.discordHandle} (${session.resolvedTier.label})"
                            else "Currently browsing as PUBLIC",
                            style = MaterialTheme.typography.labelLarge,
                            color = NexusCyan
                        )
                        Text(
                            text = "Owner account pre-linked: ${config.ownerEmail}. New Discord users may connect any Drive-enabled email address (not Gmail-only) to verify Owner or Admin roles. Every new login notifies the Owner in Email, Discord, and Notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusTextSecondary
                        )
                    }
                }

                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text("Discord Handle") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_discord_handle")
                )

                OutlinedTextField(
                    value = snowflakeId,
                    onValueChange = { snowflakeId = it },
                    label = { Text("Discord User Snowflake ID (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (any Drive-enabled address)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_input")
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasGuildRole, onCheckedChange = { hasGuildRole = it })
                    Text("Verified RP Guild Member Role", style = MaterialTheme.typography.bodySmall, color = NexusTextSecondary)
                }

                // Quick Owner Link button using AI Studio account email
                OutlinedButton(
                    onClick = {
                        handle = "Summer-Warrior (Owner)"
                        email = config.ownerEmail
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fill_owner_email_button"),
                    border = BorderStroke(1.dp, NexusGold.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusGold)
                ) {
                    Text("Use Pre-Linked Owner Account (${config.ownerEmail})", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedTextField(
                    value = discordClientId,
                    onValueChange = { discordClientId = it },
                    label = { Text("Public Discord OAuth2 Client ID (PKCE, no secret)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        viewModel.saveOAuthAndWorkerConfig(
                            discordClientId = discordClientId,
                            googleDriveClientId = config.googleDriveClientId,
                            gatekeeperWorkerUrl = config.gatekeeperWorkerUrl,
                            masterGoogleDocId = config.masterGoogleDocId
                        )
                        viewModel.startDiscordOAuthPkce(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, NexusViolet),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusViolet)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Launch Discord OAuth2 + PKCE (Custom Tab)")
                }

                if (isBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NexusCyan, strokeWidth = 2.dp)
                        Text("Verifying role & dispatching owner alerts...", style = MaterialTheme.typography.bodySmall, color = NexusCyan)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    viewModel.connectDiscordAccountWithEmail(
                        discordHandle = handle,
                        discordSnowflakeId = snowflakeId,
                        email = email,
                        hasGuildRole = hasGuildRole
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground),
                modifier = Modifier.testTag("auth_connect_confirm")
            ) {
                Text("Connect & Verify")
            }
        },
        dismissButton = {
            Row {
                if (session.isSignedIn) {
                    TextButton(onClick = {
                        viewModel.signOutDiscord()
                        onDismiss()
                    }) { Text("Sign Out", color = NexusDanger) }
                }
                TextButton(onClick = onDismiss) { Text("Close", color = NexusTextSecondary) }
            }
        }
    )
}

/**
 * CASE 2 — IN-APP GITHUB RELEASE DOWNLOAD SHEET
 * Displays version, APK filename, file size, and routes directly to GitHub Releases
 * via zero-permission browser download (NEVER Google Play Store).
 */
@Composable
fun GitHubDownloadSheetDialog(
    decision: AppRouteDecision.ShowGitHubDownloadSheet,
    onDownloadFromGitHub: (String) -> Unit,
    onOpenWebFallback: () -> Unit,
    onDismiss: () -> Unit
) {
    val release = decision.releaseAsset
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusCard,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NexusPillBadge(text = "GITHUB RELEASES · DIRECT APK", accentColor = NexusCyan)
                Text(decision.app.name, style = MaterialTheme.typography.titleLarge, color = NexusTextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "This satellite app is not installed on your device yet. Download the latest verified Android build directly from GitHub Releases (never routed to Google Play Store).",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusTextSecondary
                )
                Surface(
                    color = NexusSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NexusBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Release: ${release.tagName}", style = MaterialTheme.typography.labelLarge, color = NexusGold)
                        Text(
                            text = release.assetFileName,
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusCyan
                        )
                        Text(
                            text = "File Size: ${release.formattedSize()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusTextMuted
                        )
                    }
                }
                Button(
                    onClick = { onDownloadFromGitHub(release.downloadUrl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_github_apk_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download from GitHub Releases")
                }
                OutlinedButton(
                    onClick = onOpenWebFallback,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, NexusViolet),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusViolet)
                ) {
                    Text("Launch Web Version Instead")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = NexusTextSecondary) }
        }
    )
}

@Composable
fun LoginNotificationsDialog(
    viewModel: NexusViewModel,
    onDismiss: () -> Unit
) {
    val notifications by viewModel.loginNotifications.collectAsStateWithLifecycle()
    val config by viewModel.configPrefs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = NexusGold)
                Text("Login Alerts (${notifications.size})", style = MaterialTheme.typography.titleLarge, color = NexusTextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "New Discord + Email logins alert Owner (${config.ownerEmail}) here, via Discord Webhook, and via Email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusTextSecondary
                )
                if (notifications.isEmpty()) {
                    Text(
                        text = "No login events recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusTextMuted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    notifications.forEach { entry ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NexusSurface,
                            border = BorderStroke(1.dp, NexusBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NexusPillBadge(text = entry.resolvedTier, accentColor = NexusCyan)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(entry.timestampLabel, style = MaterialTheme.typography.labelSmall, color = NexusTextMuted)
                                        IconButton(
                                            onClick = { viewModel.deleteLoginNotification(entry.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Dismiss alert", tint = NexusDanger, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                Text("${entry.discordHandle} (${entry.email})", style = MaterialTheme.typography.labelLarge, color = NexusTextPrimary)
                                Text("Discord ID: ${entry.discordUserId}", style = MaterialTheme.typography.labelSmall, color = NexusTextMuted)
                                Text("Discord Alert: ${entry.discordAlertStatus}", style = MaterialTheme.typography.labelSmall, color = NexusViolet)
                                Text("Email Alert: ${entry.emailAlertStatus}", style = MaterialTheme.typography.labelSmall, color = NexusGold)

                                OutlinedButton(
                                    onClick = {
                                        val subject = Uri.encode("Nexus Master Hub Login Alert: ${entry.discordHandle} (${entry.resolvedTier})")
                                        val body = Uri.encode(
                                            "New login detected on Project MPV RP Master Hub:\n" +
                                                "Discord Handle: ${entry.discordHandle}\n" +
                                                "Discord ID: ${entry.discordUserId}\n" +
                                                "Email: ${entry.email}\n" +
                                                "Role Tier: ${entry.resolvedTier}\n" +
                                                "Time: ${entry.timestampLabel}"
                                        )
                                        val mailIntent = Intent(
                                            Intent.ACTION_SENDTO,
                                            Uri.parse("mailto:${config.ownerEmail}?subject=$subject&body=$body")
                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        try {
                                            context.startActivity(mailIntent)
                                        } catch (e: Exception) {
                                            viewModel.showStatus("No email app installed: ${e.message}")
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, NexusGold.copy(alpha = 0.6f))
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = NexusGold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Email Owner (${config.ownerEmail})", style = MaterialTheme.typography.labelSmall, color = NexusGold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = NexusCyan) }
        }
    )
}

/**
 * OWNER / AUTHORIZED ADMIN PANEL — 100% INVISIBLE TO UNAUTHORIZED USERS.
 * Includes:
 * 1. Google Drive (drive.file) Source Auditor & Side-by-Side Diff Viewer with explicit approval write
 * 2. One-tap Launch Dedicated MPV-Notebook-Source-Auditor with verified Discord+Email session handoff
 * 3. Admin Role Allowlist (Email + Discord Snowflake ID) & Local-Only Developer Preview Mode
 */
@Composable
fun OwnerAdminAuditorDialog(
    viewModel: NexusViewModel,
    onDismiss: () -> Unit
) {
    val session by viewModel.authSession.collectAsStateWithLifecycle()
    val config by viewModel.configPrefs.collectAsStateWithLifecycle()
    val bindings by viewModel.adminRoleBindings.collectAsStateWithLifecycle()
    val previewOverride by viewModel.previewOverrideTier.collectAsStateWithLifecycle()
    val auditDocText by viewModel.auditDocText.collectAsStateWithLifecycle()
    val auditReport by viewModel.auditReport.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var tabIndex by remember { mutableIntStateOf(0) }
    var docIdInput by remember(config.masterGoogleDocId) { mutableStateOf(config.masterGoogleDocId) }
    var workerUrlInput by remember(config.gatekeeperWorkerUrl) { mutableStateOf(config.gatekeeperWorkerUrl) }
    var googleClientIdInput by remember(config.googleDriveClientId) { mutableStateOf(config.googleDriveClientId) }

    var newBindingValue by remember { mutableStateOf("") }
    var newBindingType by remember { mutableStateOf("EMAIL") }
    var newBindingTier by remember { mutableStateOf(AccessTier.CANON_ARCHITECT_ADMIN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = NexusGold)
                Column {
                    Text("Owner / Admin Source Auditor", style = MaterialTheme.typography.titleLarge, color = NexusTextPrimary)
                    Text("Stealth Authorized Console (${session.resolvedTier.label})", style = MaterialTheme.typography.labelSmall, color = NexusGold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TabRow(selectedTabIndex = tabIndex, containerColor = NexusSurface, contentColor = NexusGold) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Drive Auditor", style = MaterialTheme.typography.labelMedium) })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Roles & Preview", style = MaterialTheme.typography.labelMedium) })
                }

                if (tabIndex == 0) {
                    // One-tap dedicated Source Auditor App launcher with verified Discord+Email handoff
                    Button(
                        onClick = { viewModel.launchDedicatedSourceAuditor(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("launch_dedicated_auditor_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NexusViolet, contentColor = NexusBackground)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Launch Dedicated Notebook Source Auditor App")
                    }

                    Text(
                        text = "Automatically passes verified Discord session + Drive-enabled email (${session.email}) for seamless sign-in & direct Notebook access.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NexusTextMuted
                    )

                    // Google Drive narrow drive.file OAuth2 connection
                    OutlinedTextField(
                        value = googleClientIdInput,
                        onValueChange = { googleClientIdInput = it },
                        label = { Text("Google OAuth Client ID (drive.file Testing Mode)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = docIdInput,
                        onValueChange = { docIdInput = it },
                        label = { Text("Master Google Doc ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = workerUrlInput,
                        onValueChange = { workerUrlInput = it },
                        label = { Text("Serverless Gatekeeper Worker URL (holds Gemini key)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.saveOAuthAndWorkerConfig(
                                    discordClientId = config.discordClientId,
                                    googleDriveClientId = googleClientIdInput,
                                    gatekeeperWorkerUrl = workerUrlInput,
                                    masterGoogleDocId = docIdInput
                                )
                                viewModel.startGoogleDriveSignIn(context)
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, NexusCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusCyan)
                        ) {
                            Text("Google Drive Sign-In", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { viewModel.fetchMasterDocFromDrive(docIdInput) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pull Doc", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    OutlinedTextField(
                        value = auditDocText,
                        onValueChange = { viewModel.updateAuditDocText(it) },
                        label = { Text("Master Google Doc Content (Editable Snapshot)") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        enabled = !isBusy,
                        onClick = {
                            viewModel.saveOAuthAndWorkerConfig(
                                discordClientId = config.discordClientId,
                                googleDriveClientId = googleClientIdInput,
                                gatekeeperWorkerUrl = workerUrlInput,
                                masterGoogleDocId = docIdInput
                            )
                            viewModel.runSourceAudit(docIdInput)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_source_audit_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NexusBackground, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auditing 8 Repos + Doc...")
                        } else {
                            Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Audit Doc + 8 GitHub Repos")
                        }
                    }

                    // Side-by-Side Current vs. Proposed Fix Diff Viewer
                    auditReport?.let { report ->
                        Surface(
                            color = NexusBackground,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NexusGold.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "DIFF REPORT · ${report.reposChecked} REPOS · ${report.engineLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NexusGold
                                )
                                report.findings.forEach { finding ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(NexusSurface, RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("${finding.category} · ${finding.sourceLabel}", style = MaterialTheme.typography.labelSmall, color = NexusCyan)
                                        Text(finding.summary, style = MaterialTheme.typography.bodySmall, color = NexusTextPrimary)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                            Surface(
                                                color = NexusDanger.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(6.dp)) {
                                                    Text("CURRENT", style = MaterialTheme.typography.labelSmall, color = NexusDanger)
                                                    Text(finding.currentText, fontFamily = MonoFontFamily, style = MaterialTheme.typography.labelSmall, color = NexusTextPrimary)
                                                }
                                            }
                                            Surface(
                                                color = NexusSuccess.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(6.dp)) {
                                                    Text("PROPOSED FIX", style = MaterialTheme.typography.labelSmall, color = NexusSuccess)
                                                    Text(finding.proposedFix, fontFamily = MonoFontFamily, style = MaterialTheme.typography.labelSmall, color = NexusTextPrimary)
                                                }
                                            }
                                        }
                                    }
                                }

                                Button(
                                    enabled = !isBusy,
                                    onClick = { viewModel.approveAndWriteAuditToDrive(docIdInput) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("approve_drive_write_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = NexusSuccess, contentColor = NexusBackground)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Approve & Write to Google Drive", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Tab 2: Admin Role Allowlist & Local-Only Developer Preview Mode
                    Surface(
                        color = NexusSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NexusGold.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("PRE-LINKED OWNER", style = MaterialTheme.typography.labelSmall, color = NexusGold)
                            Text(config.ownerEmail, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodySmall, color = NexusTextPrimary)
                        }
                    }

                    if (session.resolvedTier == AccessTier.OWNER) {
                        Text("DEVELOPER PREVIEW MODE (LOCAL ONLY)", style = MaterialTheme.typography.labelSmall, color = NexusCyan)
                        Text(
                            text = "Safely test stealth hiding of MPV-Notebook-Source-Auditor across role tiers:",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusTextSecondary
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf<AccessTier?>(
                                null,
                                AccessTier.PUBLIC,
                                AccessTier.DISCORD_SIGNED_IN,
                                AccessTier.CANON_ARCHITECT_ADMIN,
                                AccessTier.LORE_REVIEWER_ADMIN,
                                AccessTier.REPO_TECH_ADMIN
                            ).forEach { tierOpt ->
                                FilterChip(
                                    selected = previewOverride == tierOpt,
                                    onClick = { viewModel.setDeveloperPreviewTier(tierOpt) },
                                    label = { Text(tierOpt?.name ?: "LIVE OWNER", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("preview_tier_${tierOpt?.name?.lowercase() ?: "live"}")
                                )
                            }
                        }
                    }

                    Text("ADD ADMIN EMAIL OR DISCORD SNOWFLAKE", style = MaterialTheme.typography.labelSmall, color = NexusViolet)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = newBindingType == "EMAIL", onClick = { newBindingType = "EMAIL" }, label = { Text("Email (Any Domain)") })
                        FilterChip(selected = newBindingType == "DISCORD_ID", onClick = { newBindingType = "DISCORD_ID" }, label = { Text("Discord ID") })
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            AccessTier.CANON_ARCHITECT_ADMIN,
                            AccessTier.LORE_REVIEWER_ADMIN,
                            AccessTier.REPO_TECH_ADMIN
                        ).forEach { tier ->
                            FilterChip(
                                selected = newBindingTier == tier,
                                onClick = { newBindingTier = tier },
                                label = { Text(tier.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newBindingValue,
                            onValueChange = { newBindingValue = it },
                            label = { Text(if (newBindingType == "EMAIL") "Admin Email Address" else "Discord Snowflake ID") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (newBindingValue.isNotBlank()) {
                                    viewModel.addAdminRoleBinding(newBindingValue, newBindingType, newBindingTier, newBindingValue)
                                    newBindingValue = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Admin Binding", tint = NexusCyan)
                        }
                    }

                    bindings.forEach { binding ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NexusSurface, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(binding.identifier, style = MaterialTheme.typography.bodySmall, color = NexusTextPrimary)
                                Text("${binding.identifierType} · ${binding.assignedTier.name}", style = MaterialTheme.typography.labelSmall, color = NexusViolet)
                            }
                            IconButton(onClick = { viewModel.deleteAdminRoleBinding(binding.id) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove admin", tint = NexusDanger, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = NexusCyan) }
        }
    )
}
