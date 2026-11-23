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
        PinnedAppEntity::class,
        CardPackEntity::class,
        CardEntity::class,
        DeckEntity::class,
        WikiArticleEntity::class,
        RpCharacterEntity::class,
        DiscordResourceEntity::class
    ],
    version = 1,
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
                ).build().also { INSTANCE = it }
            }
        }
    }
}

private val Context.nexusDataStore by preferencesDataStore(name = "nexus_local_prefs")

/**
 * Local-only DataStore preferences for optional user-provided Discord Webhook URL.
 * Never asks for or stores Discord Bot Tokens.
 */
class NexusPreferences(private val context: Context) {
    private val keyWebhookUrl = stringPreferencesKey("discord_webhook_url")

    val webhookUrl: Flow<String> = context.nexusDataStore.data.map { prefs ->
        prefs[keyWebhookUrl].orEmpty()
    }

    suspend fun saveWebhookUrl(url: String) {
        context.nexusDataStore.edit { prefs ->
            prefs[keyWebhookUrl] = url.trim()
        }
    }
}
