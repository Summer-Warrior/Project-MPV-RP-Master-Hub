package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NexusDao {
    // Apps & Pins & Discovered Satellites
    @Query("SELECT * FROM user_apps ORDER BY createdAt DESC")
    fun observeUserApps(): Flow<List<UserAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserApp(entity: UserAppEntity)

    @Query("DELETE FROM user_apps WHERE id = :id")
    suspend fun deleteUserApp(id: String)

    @Query("SELECT * FROM discovered_apps ORDER BY syncedAt DESC")
    fun observeDiscoveredApps(): Flow<List<DiscoveredAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDiscoveredApps(entities: List<DiscoveredAppEntity>)

    @Query("SELECT * FROM pinned_apps")
    fun observePinnedApps(): Flow<List<PinnedAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pinApp(entity: PinnedAppEntity)

    @Query("DELETE FROM pinned_apps WHERE appId = :appId")
    suspend fun unpinApp(appId: String)

    // Admin Allowlist & Login Notifications
    @Query("SELECT * FROM admin_role_bindings")
    fun observeAdminRoleBindings(): Flow<List<AdminRoleBindingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAdminRoleBinding(entity: AdminRoleBindingEntity)

    @Query("DELETE FROM admin_role_bindings WHERE id = :id")
    suspend fun deleteAdminRoleBinding(id: String)

    @Query("SELECT * FROM login_notifications ORDER BY createdAt DESC")
    fun observeLoginNotifications(): Flow<List<LoginNotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoginNotification(entity: LoginNotificationEntity)

    @Query("UPDATE login_notifications SET isRead = 1")
    suspend fun markAllNotificationsRead()

    @Query("DELETE FROM login_notifications WHERE id = :id")
    suspend fun deleteLoginNotification(id: String)

    // Card Packs & Cards & Decks
    @Query("SELECT * FROM local_card_packs ORDER BY createdAt DESC")
    fun observeCardPacks(): Flow<List<CardPackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCardPack(entity: CardPackEntity)

    @Query("SELECT * FROM local_cards ORDER BY createdAt DESC")
    fun observeCards(): Flow<List<CardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(entity: CardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(entities: List<CardEntity>)

    @Query("DELETE FROM local_cards WHERE id = :id")
    suspend fun deleteCard(id: String)

    @Query("SELECT * FROM local_decks ORDER BY createdAt DESC")
    fun observeDecks(): Flow<List<DeckEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(entity: DeckEntity)

    @Query("DELETE FROM local_decks WHERE id = :id")
    suspend fun deleteDeck(id: String)

    // Wiki & Characters
    @Query("SELECT * FROM local_wiki_articles ORDER BY updatedAt DESC")
    fun observeWikiArticles(): Flow<List<WikiArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWikiArticle(entity: WikiArticleEntity)

    @Query("DELETE FROM local_wiki_articles WHERE id = :id")
    suspend fun deleteWikiArticle(id: String)

    @Query("SELECT * FROM local_rp_characters")
    fun observeCharacters(): Flow<List<RpCharacterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(entity: RpCharacterEntity)

    @Query("DELETE FROM local_rp_characters WHERE id = :id")
    suspend fun deleteCharacter(id: String)

    // Discord Resources
    @Query("SELECT * FROM local_discord_resources")
    fun observeDiscordResources(): Flow<List<DiscordResourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscordResource(entity: DiscordResourceEntity)

    @Query("DELETE FROM local_discord_resources WHERE id = :id")
    suspend fun deleteDiscordResource(id: String)
}
