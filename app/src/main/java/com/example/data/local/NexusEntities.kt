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
    val externalUrl: String,
    val launchInApp: Boolean,
    val version: String,
    val tagsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pinned_apps")
data class PinnedAppEntity(
    @PrimaryKey val appId: String,
    val pinnedAt: Long = System.currentTimeMillis()
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
    val bio: String
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
