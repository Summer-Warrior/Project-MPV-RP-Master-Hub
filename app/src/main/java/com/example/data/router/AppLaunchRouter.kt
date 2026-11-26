package com.example.data.router

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.example.data.model.AppEntry
import com.example.data.model.GitHubReleaseAsset
import com.example.data.model.UserAuthSession
import com.example.data.network.GitHubStoreService

sealed class AppRouteDecision {
    data class LaunchInstalled(
        val app: AppEntry,
        val intent: Intent,
        val sessionHandoffIncluded: Boolean
    ) : AppRouteDecision()

    data class ShowGitHubDownloadSheet(
        val app: AppEntry,
        val releaseAsset: GitHubReleaseAsset
    ) : AppRouteDecision()

    data class OpenWebInApp(
        val app: AppEntry,
        val targetUrl: String,
        val sessionHandoffIncluded: Boolean
    ) : AppRouteDecision()

    data class RouteError(val message: String) : AppRouteDecision()
}

/**
 * Exact 3-Case App Detection, Launch & GitHub Release Download Router.
 *
 * - CASE 1: Installed on Android (via PackageManager launch intent or deep-link scheme resolution)
 *           -> launch directly with zero extra prompts. For MPV-Notebook-Source-Auditor, attaches
 *           verified Discord + Email session handoff state (supporting any Drive-enabled email
 *           and direct Notebook access).
 * - CASE 2: Missing on device, and a GitHub Release binary (.apk) exists in /releases/latest
 *           -> show In-App GitHub Release Download Sheet (NEVER routes to Google Play Store).
 * - CASE 3: No native Android binary (or locked upstream / web-only app)
 *           -> skip download sheet and open webUrl / externalUrl via Chrome Custom Tabs.
 */
class AppLaunchRouter(
    private val gitHubStoreService: GitHubStoreService
) {
    companion object {
        private const val TAG = "AppLaunchRouter"
        const val AUDITOR_APP_ID = "mpv-notebook-source-auditor"
    }

    fun isInstalledOnDevice(context: Context, app: AppEntry): Boolean {
        val pm = context.packageManager
        if (app.androidPackageName.isNotBlank()) {
            val pkgIntent = pm.getLaunchIntentForPackage(app.androidPackageName)
            if (pkgIntent != null) return true
        }
        if (app.deepLinkScheme.isNotBlank()) {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(app.deepLinkScheme))
            val resolved = pm.resolveActivity(viewIntent, 0)
            if (resolved != null) return true
        }
        return false
    }

    suspend fun resolveRoute(
        context: Context,
        app: AppEntry,
        session: UserAuthSession
    ): AppRouteDecision {
        // Locked upstream GitHub clients (ProjectIgnis/EDOPro, Card-Forge/forge) open strictly as external GitHub links
        if (app.lockedUpstream) {
            val url = app.externalUrl.ifBlank { app.webUrl }
            return if (url.startsWith("https://")) {
                AppRouteDecision.OpenWebInApp(app, url, sessionHandoffIncluded = false)
            } else {
                AppRouteDecision.RouteError("Invalid upstream GitHub URL for ${app.name}.")
            }
        }

        val isAuditorHandoff = app.id == AUDITOR_APP_ID &&
            session.isSignedIn &&
            session.resolvedTier.isAdminOrOwner

        // CASE 1 — APP DETECTED ON DEVICE
        val installedIntent = buildInstalledLaunchIntent(context, app, session, isAuditorHandoff)
        if (installedIntent != null) {
            return AppRouteDecision.LaunchInstalled(
                app = app,
                intent = installedIntent,
                sessionHandoffIncluded = isAuditorHandoff
            )
        }

        // CASE 2 — APP MISSING ON DEVICE: Check GitHub /releases/latest for Android .apk asset
        if (app.repoOwner.isNotBlank() && app.repoName.isNotBlank()) {
            val releaseResult = gitHubStoreService.fetchLatestReleaseAsset(
                repoOwner = app.repoOwner,
                repoName = app.repoName,
                preferredExtensions = listOf(".apk")
            )
            val apkAsset = releaseResult.getOrNull()
            if (apkAsset != null) {
                return AppRouteDecision.ShowGitHubDownloadSheet(app, apkAsset)
            }
        }

        // CASE 3 — NO NATIVE BINARY AVAILABLE: Open webUrl (docs/index.html) directly in Custom Tabs
        val baseWebUrl = app.webUrl.ifBlank { app.externalUrl }
        if (!baseWebUrl.startsWith("https://")) {
            return AppRouteDecision.RouteError("No installed app, GitHub APK, or HTTPS web portal found for ${app.name}.")
        }
        val finalWebUrl = if (isAuditorHandoff) {
            appendAuditorSessionHandoff(baseWebUrl, session)
        } else {
            baseWebUrl
        }
        return AppRouteDecision.OpenWebInApp(
            app = app,
            targetUrl = finalWebUrl,
            sessionHandoffIncluded = isAuditorHandoff
        )
    }

    fun executeInstalledLaunch(context: Context, intent: Intent): Result<Unit> {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch installed satellite app", e)
            Result.failure(IllegalStateException("Could not open installed app: ${e.message}"))
        }
    }

    fun openUrlInCustomTab(context: Context, url: String): Result<Unit> {
        if (!url.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("Blocked non-HTTPS URL."))
        }
        return try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, Uri.parse(url))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "CustomTabs unavailable, falling back to browser ACTION_VIEW", e)
            try {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                Result.success(Unit)
            } catch (ex: Exception) {
                Log.w(TAG, "Browser ACTION_VIEW also failed", ex)
                Result.failure(IllegalStateException("No browser available to open URL."))
            }
        }
    }

    private fun buildInstalledLaunchIntent(
        context: Context,
        app: AppEntry,
        session: UserAuthSession,
        includeAuditorHandoff: Boolean
    ): Intent? {
        val pm = context.packageManager

        // Prefer deep-link scheme when handing off verified Discord + Email session state to Auditor
        if (app.deepLinkScheme.isNotBlank()) {
            val targetUri = if (includeAuditorHandoff) {
                Uri.parse(appendAuditorSessionHandoff(app.deepLinkScheme, session))
            } else {
                Uri.parse(app.deepLinkScheme)
            }
            val deepIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                putExtra("return_scheme", "nexus-master-hub://open")
                if (includeAuditorHandoff) {
                    attachAuditorExtras(this, session)
                }
            }
            if (pm.resolveActivity(deepIntent, 0) != null) {
                return deepIntent
            }
        }

        if (app.androidPackageName.isNotBlank()) {
            val launchIntent = pm.getLaunchIntentForPackage(app.androidPackageName)
            if (launchIntent != null) {
                launchIntent.putExtra("return_scheme", "nexus-master-hub://open")
                if (includeAuditorHandoff) {
                    attachAuditorExtras(launchIntent, session)
                }
                return launchIntent
            }
        }
        return null
    }

    private fun attachAuditorExtras(intent: Intent, session: UserAuthSession) {
        intent.putExtra("handoff_source", "nexus-master-hub")
        intent.putExtra("discord_id", session.discordUserId)
        intent.putExtra("discord_handle", session.discordHandle)
        intent.putExtra("role_tier", session.resolvedTier.name)
        intent.putExtra("verified_email", session.email)
        intent.putExtra("drive_any_email", true)
        intent.putExtra("notebook_access", "direct")
        intent.putExtra("session_nonce", session.sessionNonce)
        intent.putExtra("issued_at", session.issuedAtEpochMs)
    }

    /**
     * Appends verified Discord session state for authorized Owner/Admin launches of
     * MPV-Notebook-Source-Auditor so it automatically continues sign-in with any
     * Drive-enabled email address (not Gmail-only) and direct Notebook access.
     */
    fun appendAuditorSessionHandoff(baseUri: String, session: UserAuthSession): String {
        return Uri.parse(baseUri).buildUpon()
            .appendQueryParameter("handoff_source", "nexus-master-hub")
            .appendQueryParameter("return_uri", "nexus-master-hub://open")
            .appendQueryParameter("discord_id", session.discordUserId)
            .appendQueryParameter("discord_handle", session.discordHandle)
            .appendQueryParameter("role_tier", session.resolvedTier.name)
            .appendQueryParameter("verified_email", session.email)
            .appendQueryParameter("drive_any_email", "true")
            .appendQueryParameter("notebook_access", "direct")
            .appendQueryParameter("session_nonce", session.sessionNonce)
            .appendQueryParameter("issued_at", session.issuedAtEpochMs.toString())
            .build()
            .toString()
    }
}
