package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Database(
    entities = [
        UserAppEntity::class,
        DiscoveredAppEntity::class,
        PinnedAppEntity::class,
        AdminRoleBindingEntity::class,
        LoginNotificationEntity::class,
        CardPackEntity::class,
        CardEntity::class,
        DeckEntity::class,
        WikiArticleEntity::class,
        RpCharacterEntity::class,
        DiscordResourceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun nexusDao(): NexusDao

    companion object {
        @Volatile
        private var INSTANCE: NexusDatabase? = null

        fun getInstance(context: Context): NexusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexusDatabase::class.java,
                    "nexus_master_hub.db"
                ).fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

private val Context.nexusDataStore by preferencesDataStore(name = "nexus_local_prefs")

data class NexusConfigPrefs(
    val webhookUrl: String = "",
    val ownerEmail: String = DEFAULT_OWNER_EMAIL,
    val discordClientId: String = "",
    val googleDriveClientId: String = "",
    val gatekeeperWorkerUrl: String = "",
    val masterGoogleDocId: String = "",
    val activeSessionJson: String = ""
) {
    companion object {
        const val DEFAULT_OWNER_EMAIL = "DimitriWhisenhunt@gmail.com"
    }
}

/**
 * Local-only DataStore preferences for optional Webhook URL, public OAuth2 Client IDs,
 * Owner email binding, Gatekeeper Worker URL, and local session persistence.
 * NEVER stores Discord Bot Tokens, OAuth client secrets, or hardcoded Gemini API keys.
 */
class NexusPreferences(private val context: Context) {
    private val keyWebhookUrl = stringPreferencesKey("discord_webhook_url")
    private val keyOwnerEmail = stringPreferencesKey("owner_email")
    private val keyDiscordClientId = stringPreferencesKey("discord_oauth_client_id")
    private val keyGoogleDriveClientId = stringPreferencesKey("google_drive_client_id")
    private val keyGatekeeperWorkerUrl = stringPreferencesKey("gatekeeper_worker_url")
    private val keyMasterGoogleDocId = stringPreferencesKey("master_google_doc_id")
    private val keyActiveSessionJson = stringPreferencesKey("active_session_json")

    val webhookUrl: Flow<String> = context.nexusDataStore.data.map { prefs ->
        prefs[keyWebhookUrl].orEmpty()
    }

    val configPrefs: Flow<NexusConfigPrefs> = context.nexusDataStore.data.map { prefs ->
        NexusConfigPrefs(
            webhookUrl = prefs[keyWebhookUrl].orEmpty(),
            ownerEmail = prefs[keyOwnerEmail].orEmpty().ifBlank { NexusConfigPrefs.DEFAULT_OWNER_EMAIL },
            discordClientId = prefs[keyDiscordClientId].orEmpty(),
            googleDriveClientId = prefs[keyGoogleDriveClientId].orEmpty(),
            gatekeeperWorkerUrl = prefs[keyGatekeeperWorkerUrl].orEmpty(),
            masterGoogleDocId = prefs[keyMasterGoogleDocId].orEmpty(),
            activeSessionJson = prefs[keyActiveSessionJson].orEmpty()
        )
    }

    suspend fun saveWebhookUrl(url: String) {
        context.nexusDataStore.edit { it[keyWebhookUrl] = url.trim() }
    }

    suspend fun saveOAuthAndWorkerConfig(
        discordClientId: String,
        googleDriveClientId: String,
        gatekeeperWorkerUrl: String,
        masterGoogleDocId: String
    ) {
        context.nexusDataStore.edit { prefs ->
            prefs[keyDiscordClientId] = discordClientId.trim()
            prefs[keyGoogleDriveClientId] = googleDriveClientId.trim()
            prefs[keyGatekeeperWorkerUrl] = gatekeeperWorkerUrl.trim()
            prefs[keyMasterGoogleDocId] = masterGoogleDocId.trim()
        }
    }

    suspend fun saveActiveSessionJson(json: String) {
        context.nexusDataStore.edit { it[keyActiveSessionJson] = json }
    }
}
