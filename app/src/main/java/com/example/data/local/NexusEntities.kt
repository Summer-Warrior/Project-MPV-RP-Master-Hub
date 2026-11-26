package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_apps")
data class UserAppEntity(
    @PrimaryKey val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val type: String,
    val platformsJson: String,
    val repoOwner: String = "",
    val repoName: String = "",
    val deepLinkScheme: String = "",
    val androidPackageName: String = "",
    val webUrl: String = "",
    val externalUrl: String,
    val launchInApp: Boolean,
    val accessTier: String = "PUBLIC",
    val hiddenFromUnauthorized: Boolean = false,
    val allowedTiersJson: String = "[]",
    val lockedUpstream: Boolean = false,
    val version: String,
    val tagsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "discovered_apps")
data class DiscoveredAppEntity(
    @PrimaryKey val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val type: String,
    val platformsJson: String,
    val repoOwner: String,
    val repoName: String,
    val deepLinkScheme: String,
    val androidPackageName: String,
    val webUrl: String,
    val externalUrl: String,
    val accessTier: String,
    val hiddenFromUnauthorized: Boolean,
    val allowedTiersJson: String,
    val version: String,
    val tagsJson: String,
    val syncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pinned_apps")
data class PinnedAppEntity(
    @PrimaryKey val appId: String,
    val pinnedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "admin_role_bindings")
data class AdminRoleBindingEntity(
    @PrimaryKey val id: String,
    val identifier: String,
    val identifierType: String,
    val assignedTier: String,
    val label: String
)

@Entity(tableName = "login_notifications")
data class LoginNotificationEntity(
    @PrimaryKey val id: String,
    val discordHandle: String,
    val discordUserId: String,
    val email: String,
    val resolvedTier: String,
    val timestampLabel: String,
    val discordAlertStatus: String,
    val emailAlertStatus: String,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_card_packs")
data class CardPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ruleset: String,
    val version: String,
    val description: String,
    val sourceUrl: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_cards")
data class CardEntity(
    @PrimaryKey val id: String,
    val codeString: String,
    val name: String,
    val ruleset: String,
    val packId: String,
    val imageUrl: String,
    val loreLink: String,
    val abilityText: String,
    val attributesJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ruleset: String,
    val description: String,
    val cardIdsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_wiki_articles")
data class WikiArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val markdownContent: String,
    val linkedCardIdsJson: String,
    val linkedCharacterIdsJson: String,
    val updatedAt: String
)

@Entity(tableName = "local_rp_characters")
data class RpCharacterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val title: String,
    val avatarUrl: String,
    val faction: String,
    val catchphrase: String,
    val summonChant: String,
    val linkedDeckId: String,
    val currentLp: String,
    val bio: String,
    val memorialFiltersJson: String = "[]"
)

@Entity(tableName = "local_discord_resources")
data class DiscordResourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val description: String,
    val inviteUrl: String,
    val tagsJson: String
)
