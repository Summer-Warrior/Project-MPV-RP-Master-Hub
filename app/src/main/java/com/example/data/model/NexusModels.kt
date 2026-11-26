package com.example.data.model

enum class AccessTier(val label: String, val isAdminOrOwner: Boolean) {
    PUBLIC("Public", false),
    DISCORD_SIGNED_IN("Discord Signed-In", false),
    GUILD_ROLE("Guild Role", false),
    CANON_ARCHITECT_ADMIN("Canon Architect Admin", true),
    LORE_REVIEWER_ADMIN("Lore Reviewer Admin", true),
    REPO_TECH_ADMIN("Repo Tech Admin", true),
    OWNER("Owner", true);

    companion object {
        fun fromRaw(raw: String): AccessTier {
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: PUBLIC
        }
    }
}

enum class StandardTurnFlowFlag(val label: String) {
    END_TURN("End Turn"),
    EXTRA_TURN("Extra Turn")
}

enum class TabletopRuleEngineMode(val label: String, val description: String) {
    FLAGSHIP_ULTIMATE_WIP(
        "Flagship Ultimate TCG (WIP)",
        "Core Project MPV ruleset. Standard match cards allow only End Turn / Extra Turn (zero time-travel flags)."
    ),
    AI_HYBRID_COMBO_MIXER(
        "AI / Hybrid Combo Mixer",
        "Separate cross-franchise sandbox blending YGO, MTG, and custom federated packs."
    )
}

enum class ChatChannelType(val label: String, val shortTag: String) {
    IC_CHAT("IC Chat", "IC"),
    OOC_NORMAL("OOC Normal", "OOC"),
    OOC_RULING_CALL("OOC Ruling-Call", "RULING")
}

data class RpChatMessage(
    val id: String,
    val channel: ChatChannelType,
    val senderName: String,
    val senderTier: String,
    val body: String,
    val timestampLabel: String
)

/**
 * 20-Color Pie starts with 0 default colors selected per Notebook TCG guardrails.
 */
val NEXUS_20_COLOR_PIE: List<Pair<String, Long>> = listOf(
    "White" to 0xFFF5F5F5,
    "Blue" to 0xFF29B6F6,
    "Black" to 0xFF546E7A,
    "Red" to 0xFFFF5252,
    "Green" to 0xFF66BB6A,
    "Cyan" to 0xFF00E5FF,
    "Gold" to 0xFFFFB300,
    "Violet" to 0xFFB388FF,
    "Crimson" to 0xFFD50000,
    "Amber" to 0xFFFFCA28,
    "Emerald" to 0xFF00E676,
    "Cobalt" to 0xFF2979FF,
    "Indigo" to 0xFF651FFF,
    "Magenta" to 0xFFFF4081,
    "Teal" to 0xFF1DE9B6,
    "Silver" to 0xFFB0BEC5,
    "Obsidian" to 0xFF37474F,
    "Solar" to 0xFFFF9100,
    "Astral" to 0xFF80D8FF,
    "Void" to 0xFF7C4DFF
)

/**
 * Filters 19-21 live ONLY on Avatar / Memorial Forge Dossiers (never on standard match cards).
 */
val AVATAR_MEMORIAL_FILTERS_19_21: List<String> = listOf(
    "Filter 19: Avatar Chrono-Echo (Memorial Forge Only)",
    "Filter 20: Memorial Timeline Anchor (Dossier Only)",
    "Filter 21: Legacy Rift Continuity Seal (Dossier Only)"
)

data class AppEntry(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val type: String, // TCG_TOOL, TCG_CLIENT, LORE_WIKI, ASSET_VAULT, ENGINE_SANDBOX, ADMIN_AUDITOR, Official Client, Web Game, Tool
    val platforms: List<String>,
    val repoOwner: String = "",
    val repoName: String = "",
    val deepLinkScheme: String = "",
    val androidPackageName: String = "",
    val webUrl: String = "",
    val externalUrl: String = "",
    val launchInApp: Boolean = false,
    val accessTier: String = "PUBLIC",
    val hiddenFromUnauthorized: Boolean = false,
    val allowedTiers: List<String> = emptyList(),
    val lockedUpstream: Boolean = false,
    val version: String = "1.0",
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isUserCreated: Boolean = false
)

data class GitHubReleaseAsset(
    val tagName: String,
    val releaseName: String,
    val assetFileName: String,
    val assetSizeBytes: Long,
    val downloadUrl: String,
    val releasePageUrl: String,
    val publishedAt: String
) {
    fun formattedSize(): String {
        val mb = assetSizeBytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format("%.2f MB", mb) else "${(assetSizeBytes / 1024).coerceAtLeast(1)} KB"
    }
}

data class UserAuthSession(
    val isSignedIn: Boolean = false,
    val discordUserId: String = "",
    val discordHandle: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val resolvedTier: AccessTier = AccessTier.PUBLIC,
    val googleDriveConnected: Boolean = false,
    val googleDriveEmail: String = "",
    val googleAccessToken: String = "",
    val sessionNonce: String = "",
    val issuedAtEpochMs: Long = 0L
)

data class AdminRoleBinding(
    val id: String,
    val identifier: String, // Discord Snowflake ID OR Email Address (any Drive-enabled domain)
    val identifierType: String, // "EMAIL" or "DISCORD_ID"
    val assignedTier: AccessTier,
    val label: String = ""
)

data class LoginNotificationEntry(
    val id: String,
    val discordHandle: String,
    val discordUserId: String,
    val email: String,
    val resolvedTier: String,
    val timestampLabel: String,
    val discordAlertStatus: String,
    val emailAlertStatus: String,
    val isRead: Boolean = false
)

data class AuditDiffItem(
    val id: String,
    val category: String, // "OUTDATED_FILTER", "DUPLICATE_ENTRY", "EMPTY_SCRAPE", "BROKEN_LINK"
    val sourceLabel: String,
    val summary: String,
    val currentText: String,
    val proposedFix: String
)

data class SourceAuditReport(
    val auditedAt: String,
    val docId: String,
    val reposChecked: Int,
    val engineLabel: String,
    val originalDocumentText: String,
    val proposedDocumentText: String,
    val findings: List<AuditDiffItem>
)

data class CardPackEntry(
    val id: String,
    val name: String,
    val ruleset: String,
    val version: String,
    val description: String,
    val sourceUrl: String,
    val isBuiltIn: Boolean = true
)

data class CardEntry(
    val id: String,
    val codeString: String, // Arbitrary-length string ID: 8-digit, 21+ digit numeric BigInt, or UUID
    val name: String,
    val ruleset: String,
    val packId: String,
    val imageUrl: String = "",
    val loreLink: String = "",
    val abilityText: String = "",
    val attributes: Map<String, String> = emptyMap(),
    val isUserCreated: Boolean = false
)

data class DeckEntry(
    val id: String,
    val name: String,
    val ruleset: String,
    val description: String,
    val cardIds: List<String>,
    val isUserCreated: Boolean = false
)

data class WikiArticle(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val markdownContent: String,
    val linkedCardIds: List<String> = emptyList(),
    val linkedCharacterIds: List<String> = emptyList(),
    val updatedAt: String = "",
    val isUserCreated: Boolean = false
)

data class RpCharacter(
    val id: String,
    val name: String,
    val title: String,
    val avatarUrl: String = "",
    val faction: String = "",
    val catchphrase: String = "",
    val summonChant: String = "",
    val linkedDeckId: String = "",
    val currentLp: String = "8000",
    val bio: String = "",
    val memorialFilters: List<String> = emptyList(), // Filters 19-21 live ONLY on Avatar/Memorial Forge Dossiers
    val isUserCreated: Boolean = false
)

data class DiscordResource(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val inviteUrl: String,
    val tags: List<String> = emptyList(),
    val isUserCreated: Boolean = false
)

// --- Unified Single-Store Tabletop Models ---

enum class BoardLayoutMode(val label: String, val shortLabel: String) {
    YUGIOH_5_ZONE("Yu-Gi-Oh! 5-Zone", "YGO 5Z"),
    SPEED_DUEL_3_ZONE("Speed Duel 3-Zone", "Speed 3Z"),
    MTG_BATTLEFIELD("MTG Battlefield & Stack", "MTG Stack"),
    HYBRID_6_LANE("Hybrid Crossover 6-Lane", "Hybrid 6L")
}

enum class TurnPhase(val label: String, val shortCode: String) {
    DRAW("Draw", "DP"),
    STANDBY("Standby", "SP"),
    MAIN("Main", "MP"),
    COMBAT("Combat", "BP"),
    END("End", "EP")
}

enum class CardOrientation(val label: String) {
    ATTACK_UNTAPPED("ATK / Untapped"),
    DEFENSE_TAPPED("DEF / Tapped")
}

data class BoardCardInstance(
    val instanceId: String,
    val card: CardEntry,
    val orientation: CardOrientation = CardOrientation.ATTACK_UNTAPPED,
    val counters: Int = 0
)

data class BoardZone(
    val zoneId: String,
    val label: String,
    val rowGroup: String,
    val cardInstance: BoardCardInstance? = null
)

data class ChainStackItem(
    val linkId: String,
    val linkNumber: Int,
    val title: String,
    val detail: String,
    val sourceCardId: String? = null
)

data class TabletopLogEntry(
    val id: String,
    val turn: Int,
    val phase: String,
    val message: String
)

data class TabletopState(
    val engineMode: TabletopRuleEngineMode = TabletopRuleEngineMode.FLAGSHIP_ULTIMATE_WIP,
    val layoutMode: BoardLayoutMode = BoardLayoutMode.YUGIOH_5_ZONE,
    val activeDeckId: String = "",
    val activeDeckName: String = "No Deck Loaded",
    val lifePoints: String = "8000",
    val resourcePool: String = "3",
    val turnNumber: Int = 1,
    val currentPhase: TurnPhase = TurnPhase.MAIN,
    val activeTurnFlowFlag: StandardTurnFlowFlag = StandardTurnFlowFlag.END_TURN,
    val selectedPieColors: Set<String> = emptySet(), // 20-Color Pie starts with 0 default colors selected!
    val library: List<CardEntry> = emptyList(),
    val hand: List<BoardCardInstance> = emptyList(),
    val graveyard: List<CardEntry> = emptyList(),
    val zones: List<BoardZone> = emptyList(),
    val selectedHandInstanceId: String? = null,
    val inspectingZoneId: String? = null,
    val chainStack: List<ChainStackItem> = emptyList(),
    val eventLog: List<TabletopLogEntry> = emptyList(),
    val isEventLogExpanded: Boolean = false
)
