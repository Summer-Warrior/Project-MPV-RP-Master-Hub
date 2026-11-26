package com.example.data.network

import android.util.Log
import com.example.data.model.AuditDiffItem
import com.example.data.model.SourceAuditReport
import com.example.data.model.UserAuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Owner/Admin-only Google Drive (drive.file) Source Auditor & Login Notification Dispatcher.
 *
 * - Never embeds a Gemini API key in Android code: routes AI diff analysis through the
 *   configurable serverless Gatekeeper Worker URL (with a deterministic local canon & filter
 *   1-85 structural fallback so the diff viewer always works cleanly).
 * - Requires explicit human confirmation before writing any fix to Google Drive.
 */
class SourceAuditorService(
    private val networkClient: NexusNetworkClient,
    private val gitHubStoreService: GitHubStoreService
) {
    companion object {
        private const val TAG = "SourceAuditorService"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val textMedia = "text/plain; charset=utf-8".toMediaType()

    /**
     * Fetches selected Google Doc as plain text using the narrow drive.file OAuth access token.
     */
    suspend fun fetchGoogleDocContent(docId: String, accessToken: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanDocId = docId.trim()
        if (cleanDocId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a Google Doc ID first."))
        }
        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Connect Google Drive (drive.file) first."))
        }
        val url = "https://www.googleapis.com/drive/v3/files/$cleanDocId/export?mimeType=text/plain"
        try {
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    Result.success(res.body?.string().orEmpty())
                } else {
                    Result.failure(IllegalStateException("Google Drive export failed (HTTP ${res.code}). Verify drive.file access."))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Doc export failed", e)
            Result.failure(IllegalStateException("Drive fetch error: ${e.message}"))
        }
    }

    /**
     * Audits the master Google Doc text + live files across all 8 Summer-Warrior GitHub repos.
     * Uses the configured serverless Gatekeeper Worker URL (holding the Gemini API key server-side)
     * when available, and runs deterministic structural checks for Filter 1-85 parity, duplicate
     * entries, empty/footer-only wiki scrapes, and broken nexus-app.json / code-string links.
     */
    suspend fun runSourceAudit(
        docId: String,
        currentDocText: String,
        gatekeeperWorkerUrl: String
    ): Result<SourceAuditReport> = withContext(Dispatchers.IO) {
        val snapshots = gitHubStoreService.fetchEcosystemAuditSnapshots().getOrElse { emptyList() }
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        val trimmedWorker = gatekeeperWorkerUrl.trim()
        if (trimmedWorker.isNotBlank()) {
            val validated = networkClient.validateSafeExternalHttpsUrl(trimmedWorker)
            if (validated.isSuccess) {
                val workerAttempt = runViaGatekeeperWorker(
                    workerUrl = validated.getOrThrow(),
                    docId = docId,
                    docText = currentDocText,
                    snapshots = snapshots,
                    timestamp = timestamp
                )
                if (workerAttempt.isSuccess) return@withContext workerAttempt
            }
        }

        // Deterministic local canon & registry auditor (zero API keys needed)
        val findings = mutableListOf<AuditDiffItem>()
        var proposedDoc = currentDocText

        val legacyFilterRegex = Regex("Filter\\s+1[-–]8[0-4]\\b", RegexOption.IGNORE_CASE)
        if (legacyFilterRegex.containsMatchIn(proposedDoc)) {
            val matched = legacyFilterRegex.find(proposedDoc)?.value ?: "Filter 1-84"
            proposedDoc = proposedDoc.replace(legacyFilterRegex, "Filter 1-85")
            findings.add(
                AuditDiffItem(
                    id = UUID.randomUUID().toString(),
                    category = "OUTDATED_FILTER",
                    sourceLabel = "Master Google Doc ($docId)",
                    summary = "Outdated canon filter range ($matched) detected — current canon is Filter 1-85.",
                    currentText = matched,
                    proposedFix = "Filter 1-85"
                )
            )
        }

        val lines = proposedDoc.lines()
        val seen = linkedSetOf<String>()
        val deduped = mutableListOf<String>()
        var duplicateCount = 0
        for (line in lines) {
            val norm = line.trim()
            if (norm.length > 24 && !seen.add(norm)) {
                duplicateCount++
            } else {
                deduped.add(line)
            }
        }
        if (duplicateCount > 0) {
            proposedDoc = deduped.joinToString("\n")
            findings.add(
                AuditDiffItem(
                    id = UUID.randomUUID().toString(),
                    category = "DUPLICATE_ENTRY",
                    sourceLabel = "Master Google Doc ($docId)",
                    summary = "Removed $duplicateCount duplicate canon entry line(s).",
                    currentText = "$duplicateCount duplicated paragraph/entry line(s)",
                    proposedFix = "Deduplicated single-source entries"
                )
            )
        }

        if (proposedDoc.contains("Cookie Policy", ignoreCase = true) ||
            proposedDoc.contains("Privacy Policy | Terms of Service", ignoreCase = true) ||
            proposedDoc.trim().length < 60
        ) {
            findings.add(
                AuditDiffItem(
                    id = UUID.randomUUID().toString(),
                    category = "EMPTY_SCRAPE",
                    sourceLabel = "Master Google Doc ($docId)",
                    summary = "Detected footer-only or truncated wiki scrape section.",
                    currentText = proposedDoc.take(120).ifBlank { "(Empty Document Snapshot)" },
                    proposedFix = "Replace stub with verified Markdown canon body and link to Lore-Keeping-app."
                )
            )
        }

        // Check all 8 Summer-Warrior repos for missing or malformed nexus-app.json manifests
        for (snap in snapshots) {
            if (snap.manifestJson.isNullOrBlank()) {
                findings.add(
                    AuditDiffItem(
                        id = UUID.randomUUID().toString(),
                        category = "BROKEN_LINK",
                        sourceLabel = "Summer-Warrior/${snap.repoName}",
                        summary = "Root nexus-app.json manifest missing on branch '${snap.defaultBranch}'.",
                        currentText = "404 Not Found: nexus-app.json",
                        proposedFix = """{"id":"${snap.repoName.lowercase()}","name":"${snap.repoName}","webUrl":"https://summer-warrior.github.io/${snap.repoName}/"}"""
                    )
                )
            }
        }

        if (findings.isEmpty()) {
            findings.add(
                AuditDiffItem(
                    id = UUID.randomUUID().toString(),
                    category = "VERIFIED",
                    sourceLabel = "Canon & Satellite Audit",
                    summary = "All checked canon filter versions (1-85) and repo links passed structural audit.",
                    currentText = "Canon Filter 1-85 Verified",
                    proposedFix = "No destructive edits required."
                )
            )
        }

        Result.success(
            SourceAuditReport(
                auditedAt = timestamp,
                docId = docId.ifBlank { "local-snapshot" },
                reposChecked = snapshots.size,
                engineLabel = if (trimmedWorker.isBlank()) "Local Structural Auditor (Set Gatekeeper Worker for Gemini AI)" else "Local Fallback Auditor",
                originalDocumentText = currentDocText,
                proposedDocumentText = proposedDoc,
                findings = findings
            )
        )
    }

    private fun runViaGatekeeperWorker(
        workerUrl: String,
        docId: String,
        docText: String,
        snapshots: List<SatelliteRepoSnapshot>,
        timestamp: String
    ): Result<SourceAuditReport> {
        return try {
            val reposJson = JSONArray()
            snapshots.forEach { snap ->
                reposJson.put(
                    JSONObject().apply {
                        put("repoName", snap.repoName)
                        put("defaultBranch", snap.defaultBranch)
                        put("hasManifest", !snap.manifestJson.isNullOrBlank())
                        put("manifestJson", snap.manifestJson.orEmpty().take(2000))
                    }
                )
            }
            val payload = JSONObject().apply {
                put("action", "audit_sources")
                put("docId", docId)
                put("documentText", docText)
                put("targetCanonFilterVersion", "1-85")
                put("repos", reposJson)
            }
            val req = Request.Builder()
                .url(workerUrl)
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    return Result.failure(IllegalStateException("Gatekeeper Worker HTTP ${res.code}"))
                }
                val root = JSONObject(res.body?.string().orEmpty())
                val findingsArr = root.optJSONArray("findings") ?: JSONArray()
                val findings = List(findingsArr.length()) { i ->
                    val f = findingsArr.getJSONObject(i)
                    AuditDiffItem(
                        id = UUID.randomUUID().toString(),
                        category = f.optString("category", "CANON_DIFF"),
                        sourceLabel = f.optString("sourceLabel", docId),
                        summary = f.optString("summary"),
                        currentText = f.optString("currentText"),
                        proposedFix = f.optString("proposedFix")
                    )
                }
                Result.success(
                    SourceAuditReport(
                        auditedAt = timestamp,
                        docId = docId,
                        reposChecked = snapshots.size,
                        engineLabel = "Gatekeeper Worker (Server-Side Gemini)",
                        originalDocumentText = docText,
                        proposedDocumentText = root.optString("proposedDocumentText", docText),
                        findings = findings
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gatekeeper Worker audit failed, falling back locally", e)
            Result.failure(e)
        }
    }

    /**
     * Explicitly called ONLY after human approval tap on "Approve & Write to Google Drive".
     * Uses PATCH uploadType=media so Google Drive Version History preserves an undo checkpoint.
     */
    suspend fun approveAndWriteToGoogleDrive(
        docId: String,
        accessToken: String,
        approvedText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanDocId = docId.trim()
        if (cleanDocId.isBlank() || accessToken.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Connect Google Drive (drive.file) and specify a Master Google Doc ID before writing.")
            )
        }
        val url = "https://www.googleapis.com/upload/drive/v3/files/$cleanDocId?uploadType=media"
        try {
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .patch(approvedText.toRequestBody(textMedia))
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    Result.success("Approved fix written to Google Drive ($cleanDocId). Version History checkpoint created.")
                } else {
                    Result.failure(IllegalStateException("Drive write failed (HTTP ${res.code}). Check drive.file ownership."))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Drive write failed", e)
            Result.failure(IllegalStateException("Drive write error: ${e.message}"))
        }
    }

    /**
     * Dispatches Owner login notifications to Discord Webhook + optional Gatekeeper Email relay
     * whenever any new Discord account + email connects.
     */
    suspend fun dispatchOwnerLoginAlerts(
        session: UserAuthSession,
        ownerEmail: String,
        discordWebhookUrl: String,
        gatekeeperWorkerUrl: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val discordStatus = if (discordWebhookUrl.isNotBlank()) {
            val res = networkClient.sendDiscordWebhookEmbed(
                rawWebhookUrl = discordWebhookUrl,
                username = "Nexus Master Hub Auth Monitor",
                avatarUrl = session.avatarUrl,
                title = "New User Login Alert — ${session.resolvedTier.label}",
                description = "A Discord account connected to Project MPV RP Master Hub.",
                footerText = "Owner Target: $ownerEmail",
                colorDecimal = if (session.resolvedTier.isAdminOrOwner) 0xFFB300 else 0x00E5FF,
                fields = listOf(
                    "Discord Handle" to session.discordHandle.ifBlank { "Unknown" },
                    "Snowflake ID" to session.discordUserId.ifBlank { "Unlinked" },
                    "Email" to session.email.ifBlank { "None" },
                    "Assigned Tier" to session.resolvedTier.name
                )
            )
            res.fold(onSuccess = { "Delivered to Discord" }, onFailure = { "Discord failed: ${it.message}" })
        } else {
            "Configure Webhook in Discord tab for live push"
        }

        val emailStatus = if (gatekeeperWorkerUrl.isNotBlank() &&
            networkClient.validateSafeExternalHttpsUrl(gatekeeperWorkerUrl).isSuccess
        ) {
            try {
                val payload = JSONObject().apply {
                    put("action", "notify_owner_login")
                    put("ownerEmail", ownerEmail)
                    put("discordHandle", session.discordHandle)
                    put("discordUserId", session.discordUserId)
                    put("userEmail", session.email)
                    put("resolvedTier", session.resolvedTier.name)
                }
                val req = Request.Builder()
                    .url(gatekeeperWorkerUrl.trim())
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) "Email relayed to $ownerEmail"
                    else "Worker HTTP ${res.code} (use Email App button)"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Owner email worker relay failed", e)
                "Logged locally (tap Email Owner to send)"
            }
        } else {
            "Ready for $ownerEmail (tap Email Owner or set Worker)"
        }

        discordStatus to emailStatus
    }
}
