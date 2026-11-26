package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.DiscordLockAuthManager
import com.example.data.model.AccessTier
import com.example.data.model.AdminRoleBinding
import com.example.data.model.AppEntry
import com.example.data.model.NEXUS_20_COLOR_PIE
import com.example.data.model.StandardTurnFlowFlag
import com.example.data.model.TabletopState
import com.example.data.model.UserAuthSession
import com.example.data.network.GitHubStoreService
import com.example.data.network.NexusNetworkClient
import com.example.data.router.AppLaunchRouter
import com.example.data.util.BigIntMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Nexus Master Hub", appName)
    }

    @Test
    fun `bigInt math handles 21+ digit numbers without overflow`() {
        val max21 = "999999999999999999999"
        assertEquals("1000000000000000000000", BigIntMath.add(max21, "1"))
        assertEquals("999,999,999,999,999,999,999", BigIntMath.formatWithCommas(max21))
    }

    @Test
    fun `network client validates discord webhooks and rejects unsafe urls`() {
        val client = NexusNetworkClient()
        assertTrue(client.validateDiscordWebhookUrl("https://discord.com/api/webhooks/123/abc").isSuccess)
        assertTrue(client.validateDiscordWebhookUrl("https://evil.example.com/api/webhooks/123/abc").isFailure)
        assertTrue(client.validateSafeExternalHttpsUrl("http://localhost:8080/pack.json").isFailure)
    }

    @Test
    fun `stealth admin auditor is completely stripped for unauthorized tiers and visible to owner and admins`() {
        val authManager = DiscordLockAuthManager()
        val publicApp = AppEntry(
            id = "mpv-card-maker",
            name = "MPV TCG Card Maker",
            subtitle = "TCG Tool",
            description = "Public tool",
            type = "TCG_TOOL",
            platforms = listOf("Android", "Web"),
            externalUrl = "https://github.com/Summer-Warrior/MPV-TCG-Card-Making-Software-Updated"
        )
        val stealthAuditor = AppEntry(
            id = "mpv-notebook-source-auditor",
            name = "MPV Notebook Source Auditor",
            subtitle = "Admin Auditor",
            description = "Stealth admin auditor",
            type = "ADMIN_AUDITOR",
            platforms = listOf("Android", "Web"),
            deepLinkScheme = "nexus-source-auditor://open",
            externalUrl = "https://github.com/Summer-Warrior/MPV-Notebook-Source-Auditor",
            accessTier = "ADMIN_TIER",
            hiddenFromUnauthorized = true,
            allowedTiers = listOf(
                "OWNER",
                "CANON_ARCHITECT_ADMIN",
                "LORE_REVIEWER_ADMIN",
                "REPO_TECH_ADMIN"
            )
        )
        val apps = listOf(publicApp, stealthAuditor)

        val publicVisible = authManager.filterVisibleApps(apps, AccessTier.PUBLIC)
        assertEquals(listOf("mpv-card-maker"), publicVisible.map { it.id })

        val signedInVisible = authManager.filterVisibleApps(apps, AccessTier.DISCORD_SIGNED_IN)
        assertFalse(signedInVisible.any { it.id == "mpv-notebook-source-auditor" })

        val guildVisible = authManager.filterVisibleApps(apps, AccessTier.GUILD_ROLE)
        assertFalse(guildVisible.any { it.id == "mpv-notebook-source-auditor" })

        for (authorizedTier in listOf(
            AccessTier.OWNER,
            AccessTier.CANON_ARCHITECT_ADMIN,
            AccessTier.LORE_REVIEWER_ADMIN,
            AccessTier.REPO_TECH_ADMIN
        )) {
            val visible = authManager.filterVisibleApps(apps, authorizedTier)
            assertTrue(
                "Expected stealth auditor to be visible for $authorizedTier",
                visible.any { it.id == "mpv-notebook-source-auditor" }
            )
        }
    }

    @Test
    fun `owner email and non-gmail admin email resolve to expected tiers`() {
        val authManager = DiscordLockAuthManager()
        val allowlist = listOf(
            AdminRoleBinding(
                id = "admin-1",
                identifier = "reviewer@nexusguild.org",
                identifierType = "EMAIL",
                assignedTier = AccessTier.LORE_REVIEWER_ADMIN,
                label = "Lore Reviewer"
            )
        )

        val ownerTier = authManager.resolveTierForIdentity(
            discordUserId = "1001",
            email = "dimitriwhisenhunt@gmail.com",
            ownerEmail = "DimitriWhisenhunt@gmail.com",
            bindings = allowlist
        )
        assertEquals(AccessTier.OWNER, ownerTier)

        val nonGmailAdminTier = authManager.resolveTierForIdentity(
            discordUserId = "2002",
            email = "reviewer@nexusguild.org",
            ownerEmail = "DimitriWhisenhunt@gmail.com",
            bindings = allowlist
        )
        assertEquals(AccessTier.LORE_REVIEWER_ADMIN, nonGmailAdminTier)

        val standardUserTier = authManager.resolveTierForIdentity(
            discordUserId = "3003",
            email = "duelist@proton.me",
            ownerEmail = "DimitriWhisenhunt@gmail.com",
            bindings = allowlist
        )
        assertEquals(AccessTier.DISCORD_SIGNED_IN, standardUserTier)
    }

    @Test
    fun `auditor session handoff includes verified discord session and any-email drive notebook flags`() {
        val router = AppLaunchRouter(GitHubStoreService())
        val session = UserAuthSession(
            isSignedIn = true,
            discordUserId = "987654321",
            discordHandle = "CanonArchitect",
            email = "architect@customdomain.org",
            resolvedTier = AccessTier.CANON_ARCHITECT_ADMIN,
            sessionNonce = "state-xyz-123",
            issuedAtEpochMs = 1763850000000L
        )
        val handoffUri = router.appendAuditorSessionHandoff(
            baseUri = "nexus-source-auditor://open",
            session = session
        )
        assertTrue(handoffUri.contains("role_tier=CANON_ARCHITECT_ADMIN"))
        assertTrue(handoffUri.contains("verified_email=architect%40customdomain.org"))
        assertTrue(handoffUri.contains("drive_any_email=true"))
        assertTrue(handoffUri.contains("notebook_access=direct"))
        assertTrue(handoffUri.contains("discord_id=987654321"))
        assertTrue(handoffUri.contains("session_nonce=state-xyz-123"))
    }

    @Test
    fun `tabletop 20-color pie starts with zero defaults and only standard turn-flow flags exist`() {
        val state = TabletopState()
        assertEquals(0, state.selectedPieColors.size)
        assertEquals(20, NEXUS_20_COLOR_PIE.size)
        assertEquals(
            listOf("End Turn", "Extra Turn"),
            StandardTurnFlowFlag.entries.map { it.label }
        )
    }

    @Test
    fun `deep link tab query routes cleanly to hub cards tabletop wiki and rp tabs`() {
        assertEquals(0, com.example.ui.NexusViewModel.resolveDeepLinkTabIndex("apps"))
        assertEquals(1, com.example.ui.NexusViewModel.resolveDeepLinkTabIndex("cards"))
        assertEquals(2, com.example.ui.NexusViewModel.resolveDeepLinkTabIndex("tabletop"))
        assertEquals(3, com.example.ui.NexusViewModel.resolveDeepLinkTabIndex("wiki"))
        assertEquals(4, com.example.ui.NexusViewModel.resolveDeepLinkTabIndex("rp"))
        assertEquals(null, com.example.ui.NexusViewModel.resolveDeepLinkTabIndex(null))
        assertEquals(null, com.example.ui.NexusViewModel.resolveDeepLinkTabIndex("unknown"))
    }

    @Test
    fun `apps registry includes all real MPV ecosystem apps with expected repos and schemes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawJson = context.assets.open("registry/apps.json").bufferedReader().use { it.readText() }
        val arr = org.json.JSONArray(rawJson)
        val byName = (0 until arr.length()).associate { idx ->
            val obj = arr.getJSONObject(idx)
            obj.getString("name") to obj
        }

        assertEquals("Test-engine", byName["PokéFusion Studio"]?.getString("repoName"))
        assertEquals("MPV-ROM-Maker", byName["MPV Omni-Cartridge Runtime"]?.getString("repoName"))
        assertEquals("nexus-omni-cartridge://open", byName["MPV Omni-Cartridge Runtime"]?.getString("deepLinkScheme"))
        assertEquals("MPV-Non-RP-store", byName["MPV Omni-Store"]?.getString("repoName"))
        assertEquals("nexus-omni-store://open", byName["MPV Omni-Store"]?.getString("deepLinkScheme"))
        assertEquals("Asset-Holding-Software", byName["PixelForge Asset Vault"]?.getString("repoName"))
        assertEquals("Lore-Keeping-app", byName["LoreWeaver Admin Wiki"]?.getString("repoName"))
        assertEquals("MPV-Animation-Making-Software", byName["ChronoStage AI"]?.getString("repoName"))
    }
}
