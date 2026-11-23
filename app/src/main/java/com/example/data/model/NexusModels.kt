package com.example.data.model

data class AppEntry(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val type: String, // Official Client, Custom Clone, Web Game, Tool
    val platforms: List<String>, // Web, Android, iOS, Windows, macOS, Linux, Discord
    val externalUrl: String = "",
    val launchInApp: Boolean = false,
    val version: String = "1.0",
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isUserCreated: Boolean = false
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
    val category: String, // World Lore, Faction, Timeline, Ruleset
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
    val currentLp: String = "8000", // Arbitrary-length BigInt string
    val bio: String = "",
    val isUserCreated: Boolean = false
)

data class DiscordResource(
    val id: String,
    val name: String,
    val category: String, // RP Server, Bot Invite, RP Tool
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
    val rowGroup: String, // "FRONT", "BACK", "SPECIAL"
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
    val layoutMode: BoardLayoutMode = BoardLayoutMode.YUGIOH_5_ZONE,
    val activeDeckId: String = "",
    val activeDeckName: String = "No Deck Loaded",
    val lifePoints: String = "8000", // Arbitrary-length BigInteger string
    val resourcePool: String = "3",  // Mana / Energy / Omni-Resource BigInteger string
    val turnNumber: Int = 1,
    val currentPhase: TurnPhase = TurnPhase.MAIN,
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
