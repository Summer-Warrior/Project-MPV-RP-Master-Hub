package com.example.data.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.example.data.local.NexusConfigPrefs
import com.example.data.model.AccessTier
import com.example.data.model.AdminRoleBinding
import com.example.data.model.AppEntry
import com.example.data.model.UserAuthSession
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Discord OAuth2 + PKCE Account-Lock & Google Drive (drive.file) Auth Manager.
 *
 * Security boundaries:
 * - NEVER stores a Discord Bot Token or OAuth client_secret in client code.
 * - Uses PKCE (S256) via Chrome Custom Tabs.
 * - Pre-links the AI Studio account email (DimitriWhisenhunt@gmail.com) as OWNER,
 *   while allowing any Drive-enabled email address (not Gmail-only) for new Discord logins
 *   and Admin role verification.
 * - Enforces STRICT stealth hiding of apps with hiddenFromUnauthorized == true.
 */
class DiscordLockAuthManager {

    companion object {
        private const val TAG = "DiscordLockAuthManager"
        const val REDIRECT_URI_DISCORD = "nexus-master-hub://oauth/discord"
        const val REDIRECT_URI_GOOGLE = "nexus-master-hub://oauth/google"
        const val GOOGLE_DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file openid email"
        const val DEFAULT_OWNER_EMAIL = NexusConfigPrefs.DEFAULT_OWNER_EMAIL
    }

    @Volatile
    private var pendingDiscordCodeVerifier: String = ""

    @Volatile
    private var pendingDiscordState: String = ""

    @Volatile
    private var pendingGoogleCodeVerifier: String = ""

    @Volatile
    private var pendingGoogleState: String = ""

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun generateCodeChallengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun launchDiscordOAuthPkce(context: Context, clientId: String): Result<String> {
        val trimmed = clientId.trim()
        if (trimmed.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Configure your public Discord OAuth2 Client ID first, or connect your Discord + Email profile below.")
            )
        }
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallengeS256(verifier)
        val state = UUID.randomUUID().toString()
        pendingDiscordCodeVerifier = verifier
        pendingDiscordState = state

        val authUri = Uri.Builder()
            .scheme("https")
            .authority("discord.com")
            .appendPath("oauth2")
            .appendPath("authorize")
            .appendQueryParameter("client_id", trimmed)
            .appendQueryParameter("redirect_uri", REDIRECT_URI_DISCORD)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "identify email guilds")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        return try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, authUri)
            Result.success("Opened Discord OAuth2 + PKCE sign-in.")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open Custom Tab for Discord OAuth2", e)
            Result.failure(IllegalStateException("Could not open browser for Discord OAuth2: ${e.message}"))
        }
    }

    /**
     * Launches Google OAuth2 + PKCE with the narrow drive.file scope so Owner/Admins can connect
     * ANY Drive-enabled email address (not Gmail-only) without requiring public OAuth verification.
     */
    fun launchGoogleDriveFileOAuth(
        context: Context,
        googleClientId: String,
        loginHintEmail: String = ""
    ): Result<String> {
        val trimmed = googleClientId.trim()
        if (trimmed.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Enter your Google OAuth2 Client ID (Testing mode, drive.file scope) in Auditor Settings.")
            )
        }
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallengeS256(verifier)
        val state = UUID.randomUUID().toString()
        pendingGoogleCodeVerifier = verifier
        pendingGoogleState = state

        val builder = Uri.Builder()
            .scheme("https")
            .authority("accounts.google.com")
            .appendPath("o")
            .appendPath("oauth2")
            .appendPath("v2")
            .appendPath("auth")
            .appendQueryParameter("client_id", trimmed)
            .appendQueryParameter("redirect_uri", REDIRECT_URI_GOOGLE)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", GOOGLE_DRIVE_FILE_SCOPE)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "online")
            .appendQueryParameter("prompt", "select_account")

        if (loginHintEmail.isNotBlank()) {
            builder.appendQueryParameter("login_hint", loginHintEmail.trim())
        }

        return try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, builder.build())
            Result.success("Opened Google Drive (drive.file) sign-in.")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open Custom Tab for Google Drive OAuth2", e)
            Result.failure(IllegalStateException("Could not open browser for Google Drive OAuth2: ${e.message}"))
        }
    }

    fun consumeDiscordCallback(uri: Uri): Result<Pair<String, String>> {
        val state = uri.getQueryParameter("state").orEmpty()
        val code = uri.getQueryParameter("code").orEmpty()
        if (pendingDiscordState.isNotBlank() && state != pendingDiscordState) {
            return Result.failure(SecurityException("Discord OAuth state mismatch."))
        }
        if (code.isBlank()) {
            val err = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error") ?: "Missing OAuth code"
            return Result.failure(IllegalStateException("Discord OAuth cancelled: $err"))
        }
        val verifier = pendingDiscordCodeVerifier
        pendingDiscordCodeVerifier = ""
        pendingDiscordState = ""
        return Result.success(code to verifier)
    }

    fun consumeGoogleCallback(uri: Uri): Result<Pair<String, String>> {
        val state = uri.getQueryParameter("state").orEmpty()
        val code = uri.getQueryParameter("code").orEmpty()
        if (pendingGoogleState.isNotBlank() && state != pendingGoogleState) {
            return Result.failure(SecurityException("Google Drive OAuth state mismatch."))
        }
        if (code.isBlank()) {
            val err = uri.getQueryParameter("error") ?: "Missing OAuth code"
            return Result.failure(IllegalStateException("Google Drive OAuth cancelled: $err"))
        }
        val verifier = pendingGoogleCodeVerifier
        pendingGoogleCodeVerifier = ""
        pendingGoogleState = ""
        return Result.success(code to verifier)
    }

    /**
     * Resolves a user's AccessTier by checking:
     * 1. Pre-linked Owner email (DimitriWhisenhunt@gmail.com) -> OWNER
     * 2. Allowlisted Admin bindings by Email (any Drive-enabled domain) or Discord Snowflake ID
     * 3. Signed-in Discord user -> DISCORD_SIGNED_IN (or GUILD_ROLE if guildVerified)
     */
    fun resolveTierForIdentity(
        discordUserId: String,
        email: String,
        ownerEmail: String,
        bindings: List<AdminRoleBinding>,
        hasGuildRole: Boolean = false
    ): AccessTier {
        val normalizedEmail = email.trim().lowercase()
        val normalizedOwner = ownerEmail.trim().lowercase().ifBlank { DEFAULT_OWNER_EMAIL.lowercase() }
        val normalizedId = discordUserId.trim()

        if (normalizedEmail.isNotBlank() && normalizedEmail == normalizedOwner) {
            return AccessTier.OWNER
        }

        val emailMatch = bindings.firstOrNull {
            it.identifierType == "EMAIL" && it.identifier.trim().lowercase() == normalizedEmail && normalizedEmail.isNotBlank()
        }
        if (emailMatch != null) return emailMatch.assignedTier

        val idMatch = bindings.firstOrNull {
            it.identifierType == "DISCORD_ID" && it.identifier.trim() == normalizedId && normalizedId.isNotBlank()
        }
        if (idMatch != null) return idMatch.assignedTier

        return if (hasGuildRole) AccessTier.GUILD_ROLE else AccessTier.DISCORD_SIGNED_IN
    }

    fun isValidEmail(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.contains("@") && trimmed.substringAfter("@").contains(".") && !trimmed.contains(" ")
    }

    /**
     * STRICT STEALTH HIDING RULE:
     * Apps marked hiddenFromUnauthorized == true (specifically MPV-Notebook-Source-Auditor)
     * are 100% stripped from lists, search, filters, and drawers for anyone who is NOT
     * OWNER or an explicitly allowlisted Admin Tier. Zero "Locked" placeholder is ever shown.
     */
    fun isAppVisibleToEffectiveTier(app: AppEntry, effectiveTier: AccessTier): Boolean {
        if (!app.hiddenFromUnauthorized) return true
        if (effectiveTier == AccessTier.OWNER) return true
        return app.allowedTiers.any { it.equals(effectiveTier.name, ignoreCase = true) }
    }

    fun filterVisibleApps(apps: List<AppEntry>, effectiveTier: AccessTier): List<AppEntry> {
        return apps.filter { isAppVisibleToEffectiveTier(it, effectiveTier) }
    }

    fun isAuthorizedToLaunch(app: AppEntry, effectiveTier: AccessTier): Boolean {
        if (app.hiddenFromUnauthorized && !isAppVisibleToEffectiveTier(app, effectiveTier)) {
            return false
        }
        return when (app.accessTier.uppercase()) {
            "PUBLIC" -> true
            "DISCORD_SIGNED_IN" -> effectiveTier != AccessTier.PUBLIC
            "GUILD_ROLE" -> effectiveTier != AccessTier.PUBLIC && effectiveTier != AccessTier.DISCORD_SIGNED_IN
            "OWNER_ONLY" -> effectiveTier == AccessTier.OWNER
            "ADMIN_TIER" -> effectiveTier == AccessTier.OWNER ||
                app.allowedTiers.any { it.equals(effectiveTier.name, ignoreCase = true) }
            else -> true
        }
    }

    fun effectiveTier(session: UserAuthSession, previewOverride: AccessTier?): AccessTier {
        if (session.resolvedTier == AccessTier.OWNER && previewOverride != null) {
            return previewOverride
        }
        return if (session.isSignedIn) session.resolvedTier else AccessTier.PUBLIC
    }

    fun serializeSession(session: UserAuthSession): String = JSONObject().apply {
        put("isSignedIn", session.isSignedIn)
        put("discordUserId", session.discordUserId)
        put("discordHandle", session.discordHandle)
        put("email", session.email)
        put("avatarUrl", session.avatarUrl)
        put("resolvedTier", session.resolvedTier.name)
        put("googleDriveConnected", session.googleDriveConnected)
        put("googleDriveEmail", session.googleDriveEmail)
        put("sessionNonce", session.sessionNonce)
        put("issuedAtEpochMs", session.issuedAtEpochMs)
    }.toString()

    fun deserializeSession(raw: String): UserAuthSession {
        if (raw.isBlank()) return UserAuthSession()
        return try {
            val obj = JSONObject(raw)
            UserAuthSession(
                isSignedIn = obj.optBoolean("isSignedIn", false),
                discordUserId = obj.optString("discordUserId"),
                discordHandle = obj.optString("discordHandle"),
                email = obj.optString("email"),
                avatarUrl = obj.optString("avatarUrl"),
                resolvedTier = AccessTier.fromRaw(obj.optString("resolvedTier", "PUBLIC")),
                googleDriveConnected = obj.optBoolean("googleDriveConnected", false),
                googleDriveEmail = obj.optString("googleDriveEmail"),
                sessionNonce = obj.optString("sessionNonce"),
                issuedAtEpochMs = obj.optLong("issuedAtEpochMs", 0L)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore saved session", e)
            UserAuthSession()
        }
    }
}
