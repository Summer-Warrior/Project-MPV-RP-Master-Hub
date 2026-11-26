package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.AdminRoleBindingEntity
import com.example.data.local.CardEntity
import com.example.data.local.CardPackEntity
import com.example.data.local.DeckEntity
import com.example.data.local.DiscordResourceEntity
import com.example.data.local.DiscoveredAppEntity
import com.example.data.local.LoginNotificationEntity
import com.example.data.local.NexusConfigPrefs
import com.example.data.local.NexusDao
import com.example.data.local.NexusPreferences
import com.example.data.local.PinnedAppEntity
import com.example.data.local.RpCharacterEntity
import com.example.data.local.UserAppEntity
import com.example.data.local.WikiArticleEntity
import com.example.data.model.AccessTier
import com.example.data.model.AdminRoleBinding
import com.example.data.model.AppEntry
import com.example.data.model.CardEntry
import com.example.data.model.CardPackEntry
import com.example.data.model.DeckEntry
import com.example.data.model.DiscordResource
import com.example.data.model.LoginNotificationEntry
import com.example.data.model.RpCharacter
import com.example.data.model.WikiArticle
import com.example.data.network.GitHubStoreService
import com.example.data.network.ImportedPackResult
import com.example.data.network.NexusNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class NexusRepository(
    private val context: Context,
    private val dao: NexusDao,
    private val preferences: NexusPreferences,
    private val networkClient: NexusNetworkClient,
    private val gitHubStoreService: GitHubStoreService
) {
    companion object {
        private const val TAG = "NexusRepository"
    }

    private val bundledApps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val bundledPacks = MutableStateFlow<List<CardPackEntry>>(emptyList())
    private val bundledCards = MutableStateFlow<List<CardEntry>>(emptyList())
    private val bundledDecks = MutableStateFlow<List<DeckEntry>>(emptyList())
    private val bundledArticles = MutableStateFlow<List<WikiArticle>>(emptyList())
    private val bundledCharacters = MutableStateFlow<List<RpCharacter>>(emptyList())
    private val bundledDiscord = MutableStateFlow<List<DiscordResource>>(emptyList())

    val webhookUrl: Flow<String> = preferences.webhookUrl
    val configPrefs: Flow<NexusConfigPrefs> = preferences.configPrefs

    val apps: Flow<List<AppEntry>> = combine(
        bundledApps,
        dao.observeDiscoveredApps(),
        dao.observeUserApps(),
        dao.observePinnedApps()
    ) { base, discoveredEntities, userEntities, pins ->
        val pinnedIds = pins.map { it.appId }.toSet()
        val userApps = userEntities.map { it.toModel(it.id in pinnedIds) }
        val discoveredApps = discoveredEntities.map { it.toModel(it.id in pinnedIds) }
        val baseApps = base.map { it.copy(isPinned = it.id in pinnedIds) }
        val merged = (userApps + baseApps + discoveredApps).distinctBy { it.id }
        merged.sortedWith(
            compareByDescending<AppEntry> { it.isPinned }
                .thenByDescending { it.launchInApp }
                .thenBy { it.lockedUpstream }
        )
    }

    val adminRoleBindings: Flow<List<AdminRoleBinding>> = dao.observeAdminRoleBindings().map { list ->
        list.map {
            AdminRoleBinding(
                id = it.id,
                identifier = it.identifier,
                identifierType = it.identifierType,
                assignedTier = AccessTier.fromRaw(it.assignedTier),
                label = it.label
            )
        }
    }

    val loginNotifications: Flow<List<LoginNotificationEntry>> = dao.observeLoginNotifications().map { list ->
        list.map {
            LoginNotificationEntry(
                id = it.id,
                discordHandle = it.discordHandle,
                discordUserId = it.discordUserId,
                email = it.email,
                resolvedTier = it.resolvedTier,
                timestampLabel = it.timestampLabel,
                discordAlertStatus = it.discordAlertStatus,
                emailAlertStatus = it.emailAlertStatus,
                isRead = it.isRead
            )
        }
    }

    val cardPacks: Flow<List<CardPackEntry>> = combine(
        bundledPacks,
        dao.observeCardPacks()
    ) { base, local ->
        (local.map { it.toModel() } + base).distinctBy { it.id }
    }

    val cards: Flow<List<CardEntry>> = combine(
        bundledCards,
        dao.observeCards()
    ) { base, local ->
        (local.map { it.toModel() } + base).distinctBy { it.id }
    }

    val decks: Flow<List<DeckEntry>> = combine(
        bundledDecks,
        dao.observeDecks()
    ) { base, local ->
        (local.map { it.toModel() } + base).distinctBy { it.id }
    }

    val wikiArticles: Flow<List<WikiArticle>> = combine(
        bundledArticles,
        dao.observeWikiArticles()
    ) { base, local ->
        (local.map { it.toModel() } + base).distinctBy { it.id }
    }

    val rpCharacters: Flow<List<RpCharacter>> = combine(
        bundledCharacters,
        dao.observeCharacters()
    ) { base, local ->
        (local.map { it.toModel() } + base).distinctBy { it.id }
    }

    val discordResources: Flow<List<DiscordResource>> = combine(
        bundledDiscord,
        dao.observeDiscordResources()
    ) { base, local ->
        (local.map { it.toModel() } + base).distinctBy { it.id }
    }

    suspend fun loadBundledRegistries() = withContext(Dispatchers.IO) {
        bundledApps.value = parseAppsRegistry(readAsset("registry/apps.json"))
        val (packs, cards, decks) = parseCardPacksRegistry(readAsset("registry/card-packs.json"))
        bundledPacks.value = packs
        bundledCards.value = cards
        bundledDecks.value = decks
        val (articles, characters) = parseWikisRegistry(readAsset("registry/wikis.json"))
        bundledArticles.value = articles
        bundledCharacters.value = characters
        bundledDiscord.value = parseBotsRegistry(readAsset("registry/bots.json"))
    }

    suspend fun syncGitHubSatellites(): Result<Int> {
        val result = gitHubStoreService.discoverSatelliteApps()
        result.onSuccess { discovered ->
            val entities = discovered.map { app ->
                DiscoveredAppEntity(
                    id = app.id,
                    name = app.name,
                    subtitle = app.subtitle,
                    description = app.description,
                    type = app.type,
                    platformsJson = JSONArray(app.platforms).toString(),
                    repoOwner = app.repoOwner,
                    repoName = app.repoName,
                    deepLinkScheme = app.deepLinkScheme,
                    androidPackageName = app.androidPackageName,
                    webUrl = app.webUrl,
                    externalUrl = app.externalUrl,
                    accessTier = app.accessTier,
                    hiddenFromUnauthorized = app.hiddenFromUnauthorized,
                    allowedTiersJson = JSONArray(app.allowedTiers).toString(),
                    version = app.version,
                    tagsJson = JSONArray(app.tags).toString()
                )
            }
            if (entities.isNotEmpty()) {
                dao.upsertDiscoveredApps(entities)
            }
        }
        return result.map { it.size }
    }

    private fun readAsset(path: String): String {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read asset $path", e)
            ""
        }
    }

    // --- Mutations ---

    suspend fun togglePinApp(appId: String, currentlyPinned: Boolean) {
        if (currentlyPinned) dao.unpinApp(appId) else dao.pinApp(PinnedAppEntity(appId))
    }

    suspend fun saveUserApp(app: AppEntry) {
        dao.insertUserApp(
            UserAppEntity(
                id = app.id,
                name = app.name,
                subtitle = app.subtitle,
                description = app.description,
                type = app.type,
                platformsJson = JSONArray(app.platforms).toString(),
                repoOwner = app.repoOwner,
                repoName = app.repoName,
                deepLinkScheme = app.deepLinkScheme,
                androidPackageName = app.androidPackageName,
                webUrl = app.webUrl,
                externalUrl = app.externalUrl,
                launchInApp = app.launchInApp,
                accessTier = app.accessTier,
                hiddenFromUnauthorized = app.hiddenFromUnauthorized,
                allowedTiersJson = JSONArray(app.allowedTiers).toString(),
                lockedUpstream = app.lockedUpstream,
                version = app.version,
                tagsJson = JSONArray(app.tags).toString()
            )
        )
    }

    suspend fun deleteUserApp(id: String) = dao.deleteUserApp(id)

    suspend fun saveAdminRoleBinding(binding: AdminRoleBinding) {
        dao.upsertAdminRoleBinding(
            AdminRoleBindingEntity(
                id = binding.id,
                identifier = binding.identifier.trim(),
                identifierType = binding.identifierType,
                assignedTier = binding.assignedTier.name,
                label = binding.label.trim()
            )
        )
    }

    suspend fun deleteAdminRoleBinding(id: String) = dao.deleteAdminRoleBinding(id)

    suspend fun recordLoginNotification(entry: LoginNotificationEntry) {
        dao.insertLoginNotification(
            LoginNotificationEntity(
                id = entry.id,
                discordHandle = entry.discordHandle,
                discordUserId = entry.discordUserId,
                email = entry.email,
                resolvedTier = entry.resolvedTier,
                timestampLabel = entry.timestampLabel,
                discordAlertStatus = entry.discordAlertStatus,
                emailAlertStatus = entry.emailAlertStatus,
                isRead = entry.isRead
            )
        )
    }

    suspend fun markAllNotificationsRead() = dao.markAllNotificationsRead()

    suspend fun deleteLoginNotification(id: String) = dao.deleteLoginNotification(id)

    suspend fun saveOAuthAndWorkerConfig(
        discordClientId: String,
        googleDriveClientId: String,
        gatekeeperWorkerUrl: String,
        masterGoogleDocId: String
    ) = preferences.saveOAuthAndWorkerConfig(
        discordClientId = discordClientId,
        googleDriveClientId = googleDriveClientId,
        gatekeeperWorkerUrl = gatekeeperWorkerUrl,
        masterGoogleDocId = masterGoogleDocId
    )

    suspend fun saveActiveSessionJson(json: String) = preferences.saveActiveSessionJson(json)

    suspend fun saveCard(card: CardEntry) = dao.insertCard(card.toEntity())

    suspend fun deleteCard(id: String) = dao.deleteCard(id)

    suspend fun saveCardPack(pack: CardPackEntry) {
        dao.insertCardPack(
            CardPackEntity(
                id = pack.id,
                name = pack.name,
                ruleset = pack.ruleset,
                version = pack.version,
                description = pack.description,
                sourceUrl = pack.sourceUrl
            )
        )
    }

    suspend fun importFederatedPackFromUrl(url: String): Result<ImportedPackResult> {
        val result = networkClient.fetchFederatedCardPack(url)
        result.onSuccess { imported ->
            saveCardPack(imported.pack)
            dao.insertCards(imported.cards.map { it.toEntity() })
        }
        return result
    }

    suspend fun importFederatedPackFromRawJson(jsonText: String): Result<ImportedPackResult> {
        val result = networkClient.parseFederatedPackPayload(jsonText, "local://manual-paste")
        result.onSuccess { imported ->
            saveCardPack(imported.pack)
            dao.insertCards(imported.cards.map { it.toEntity() })
        }
        return result
    }

    suspend fun saveDeck(deck: DeckEntry) {
        dao.insertDeck(
            DeckEntity(
                id = deck.id,
                name = deck.name,
                ruleset = deck.ruleset,
                description = deck.description,
                cardIdsJson = JSONArray(deck.cardIds).toString()
            )
        )
    }

    suspend fun deleteDeck(id: String) = dao.deleteDeck(id)

    suspend fun saveWikiArticle(article: WikiArticle) {
        dao.insertWikiArticle(
            WikiArticleEntity(
                id = article.id,
                title = article.title,
                category = article.category,
                summary = article.summary,
                markdownContent = article.markdownContent,
                linkedCardIdsJson = JSONArray(article.linkedCardIds).toString(),
                linkedCharacterIdsJson = JSONArray(article.linkedCharacterIds).toString(),
                updatedAt = article.updatedAt
            )
        )
    }

    suspend fun deleteWikiArticle(id: String) = dao.deleteWikiArticle(id)

    suspend fun saveRpCharacter(character: RpCharacter) {
        dao.insertCharacter(
            RpCharacterEntity(
                id = character.id,
                name = character.name,
                title = character.title,
                avatarUrl = character.avatarUrl,
                faction = character.faction,
                catchphrase = character.catchphrase,
                summonChant = character.summonChant,
                linkedDeckId = character.linkedDeckId,
                currentLp = character.currentLp,
                bio = character.bio,
                memorialFiltersJson = JSONArray(character.memorialFilters).toString()
            )
        )
    }

    suspend fun deleteRpCharacter(id: String) = dao.deleteCharacter(id)

    suspend fun saveDiscordResource(resource: DiscordResource) {
        dao.insertDiscordResource(
            DiscordResourceEntity(
                id = resource.id,
                name = resource.name,
                category = resource.category,
                description = resource.description,
                inviteUrl = resource.inviteUrl,
                tagsJson = JSONArray(resource.tags).toString()
            )
        )
    }

    suspend fun deleteDiscordResource(id: String) = dao.deleteDiscordResource(id)

    suspend fun saveWebhookUrl(url: String) = preferences.saveWebhookUrl(url)

    suspend fun broadcastDiscordRp(
        webhookUrl: String,
        username: String,
        avatarUrl: String,
        title: String,
        description: String,
        footer: String,
        colorDecimal: Int,
        fields: List<Pair<String, String>>
    ): Result<String> {
        return networkClient.sendDiscordWebhookEmbed(
            rawWebhookUrl = webhookUrl,
            username = username,
            avatarUrl = avatarUrl,
            title = title,
            description = description,
            footerText = footer,
            colorDecimal = colorDecimal,
            fields = fields
        )
    }

    // --- JSON Parsers ---

    private fun parseAppsRegistry(json: String): List<AppEntry> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                AppEntry(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    subtitle = obj.optString("subtitle"),
                    description = obj.optString("description"),
                    type = obj.optString("type", "TCG_TOOL"),
                    platforms = obj.optJSONArray("platforms").toStringList(),
                    repoOwner = obj.optString("repoOwner"),
                    repoName = obj.optString("repoName"),
                    deepLinkScheme = obj.optString("deepLinkScheme"),
                    androidPackageName = obj.optString("androidPackageName"),
                    webUrl = obj.optString("webUrl"),
                    externalUrl = obj.optString("externalUrl"),
                    launchInApp = obj.optBoolean("launchInApp", false),
                    accessTier = obj.optString("accessTier", "PUBLIC"),
                    hiddenFromUnauthorized = obj.optBoolean("hiddenFromUnauthorized", false),
                    allowedTiers = obj.optJSONArray("allowedTiers").toStringList(),
                    lockedUpstream = obj.optBoolean("lockedUpstream", false),
                    version = obj.optString("version", "1.0"),
                    tags = obj.optJSONArray("tags").toStringList(),
                    isPinned = false,
                    isUserCreated = false
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing apps.json", e)
            emptyList()
        }
    }

    private fun parseCardPacksRegistry(json: String): Triple<List<CardPackEntry>, List<CardEntry>, List<DeckEntry>> {
        if (json.isBlank()) return Triple(emptyList(), emptyList(), emptyList())
        return try {
            val root = JSONObject(json)
            val packsArr = root.optJSONArray("packs") ?: JSONArray()
            val packs = mutableListOf<CardPackEntry>()
            val cards = mutableListOf<CardEntry>()
            for (i in 0 until packsArr.length()) {
                val pObj = packsArr.getJSONObject(i)
                val packId = pObj.getString("id")
                packs.add(
                    CardPackEntry(
                        id = packId,
                        name = pObj.getString("name"),
                        ruleset = pObj.optString("ruleset", "Hybrid Crossover"),
                        version = pObj.optString("version", "1.0.0"),
                        description = pObj.optString("description"),
                        sourceUrl = pObj.optString("sourceUrl"),
                        isBuiltIn = true
                    )
                )
                val cardsArr = pObj.optJSONArray("cards") ?: JSONArray()
                for (j in 0 until cardsArr.length()) {
                    val cObj = cardsArr.getJSONObject(j)
                    cards.add(
                        CardEntry(
                            id = cObj.getString("id"),
                            codeString = cObj.getString("codeString"),
                            name = cObj.getString("name"),
                            ruleset = cObj.optString("ruleset", pObj.optString("ruleset")),
                            packId = cObj.optString("packId", packId),
                            imageUrl = cObj.optString("imageUrl"),
                            loreLink = cObj.optString("loreLink"),
                            abilityText = cObj.optString("abilityText"),
                            attributes = cObj.optJSONObject("attributes").toStringMap(),
                            isUserCreated = false
                        )
                    )
                }
            }
            val decksArr = root.optJSONArray("starterDecks") ?: JSONArray()
            val decks = List(decksArr.length()) { i ->
                val dObj = decksArr.getJSONObject(i)
                DeckEntry(
                    id = dObj.getString("id"),
                    name = dObj.getString("name"),
                    ruleset = dObj.optString("ruleset"),
                    description = dObj.optString("description"),
                    cardIds = dObj.optJSONArray("cardIds").toStringList(),
                    isUserCreated = false
                )
            }
            Triple(packs, cards, decks)
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing card-packs.json", e)
            Triple(emptyList(), emptyList(), emptyList())
        }
    }

    private fun parseWikisRegistry(json: String): Pair<List<WikiArticle>, List<RpCharacter>> {
        if (json.isBlank()) return emptyList<WikiArticle>() to emptyList()
        return try {
            val root = JSONObject(json)
            val artArr = root.optJSONArray("articles") ?: JSONArray()
            val articles = List(artArr.length()) { i ->
                val obj = artArr.getJSONObject(i)
                WikiArticle(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    category = obj.optString("category", "World Lore"),
                    summary = obj.optString("summary"),
                    markdownContent = obj.optString("markdownContent"),
                    linkedCardIds = obj.optJSONArray("linkedCardIds").toStringList(),
                    linkedCharacterIds = obj.optJSONArray("linkedCharacterIds").toStringList(),
                    updatedAt = obj.optString("updatedAt"),
                    isUserCreated = false
                )
            }
            val charArr = root.optJSONArray("characters") ?: JSONArray()
            val characters = List(charArr.length()) { i ->
                val obj = charArr.getJSONObject(i)
                RpCharacter(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    title = obj.optString("title"),
                    avatarUrl = obj.optString("avatarUrl"),
                    faction = obj.optString("faction"),
                    catchphrase = obj.optString("catchphrase"),
                    summonChant = obj.optString("summonChant"),
                    linkedDeckId = obj.optString("linkedDeckId"),
                    currentLp = obj.optString("currentLp", "8000"),
                    bio = obj.optString("bio"),
                    memorialFilters = obj.optJSONArray("memorialFilters").toStringList(),
                    isUserCreated = false
                )
            }
            articles to characters
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing wikis.json", e)
            emptyList<WikiArticle>() to emptyList()
        }
    }

    private fun parseBotsRegistry(json: String): List<DiscordResource> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                DiscordResource(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    category = obj.optString("category", "RP Server"),
                    description = obj.optString("description"),
                    inviteUrl = obj.optString("inviteUrl"),
                    tags = obj.optJSONArray("tags").toStringList(),
                    isUserCreated = false
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing bots.json", e)
            emptyList()
        }
    }

    // --- Entity Mappers ---

    private fun UserAppEntity.toModel(isPinned: Boolean) = AppEntry(
        id = id,
        name = name,
        subtitle = subtitle,
        description = description,
        type = type,
        platforms = parseJsonArrayString(platformsJson),
        repoOwner = repoOwner,
        repoName = repoName,
        deepLinkScheme = deepLinkScheme,
        androidPackageName = androidPackageName,
        webUrl = webUrl,
        externalUrl = externalUrl,
        launchInApp = launchInApp,
        accessTier = accessTier,
        hiddenFromUnauthorized = hiddenFromUnauthorized,
        allowedTiers = parseJsonArrayString(allowedTiersJson),
        lockedUpstream = lockedUpstream,
        version = version,
        tags = parseJsonArrayString(tagsJson),
        isPinned = isPinned,
        isUserCreated = true
    )

    private fun DiscoveredAppEntity.toModel(isPinned: Boolean) = AppEntry(
        id = id,
        name = name,
        subtitle = subtitle,
        description = description,
        type = type,
        platforms = parseJsonArrayString(platformsJson),
        repoOwner = repoOwner,
        repoName = repoName,
        deepLinkScheme = deepLinkScheme,
        androidPackageName = androidPackageName,
        webUrl = webUrl,
        externalUrl = externalUrl,
        launchInApp = false,
        accessTier = accessTier,
        hiddenFromUnauthorized = hiddenFromUnauthorized,
        allowedTiers = parseJsonArrayString(allowedTiersJson),
        lockedUpstream = false,
        version = version,
        tags = parseJsonArrayString(tagsJson),
        isPinned = isPinned,
        isUserCreated = false
    )

    private fun CardPackEntity.toModel() = CardPackEntry(
        id = id,
        name = name,
        ruleset = ruleset,
        version = version,
        description = description,
        sourceUrl = sourceUrl,
        isBuiltIn = false
    )

    private fun CardEntity.toModel() = CardEntry(
        id = id,
        codeString = codeString,
        name = name,
        ruleset = ruleset,
        packId = packId,
        imageUrl = imageUrl,
        loreLink = loreLink,
        abilityText = abilityText,
        attributes = parseJsonMapString(attributesJson),
        isUserCreated = true
    )

    private fun CardEntry.toEntity(): CardEntity {
        val attrs = JSONObject()
        attributes.forEach { (k, v) -> attrs.put(k, v) }
        return CardEntity(
            id = id,
            codeString = codeString,
            name = name,
            ruleset = ruleset,
            packId = packId,
            imageUrl = imageUrl,
            loreLink = loreLink,
            abilityText = abilityText,
            attributesJson = attrs.toString()
        )
    }

    private fun DeckEntity.toModel() = DeckEntry(
        id = id,
        name = name,
        ruleset = ruleset,
        description = description,
        cardIds = parseJsonArrayString(cardIdsJson),
        isUserCreated = true
    )

    private fun WikiArticleEntity.toModel() = WikiArticle(
        id = id,
        title = title,
        category = category,
        summary = summary,
        markdownContent = markdownContent,
        linkedCardIds = parseJsonArrayString(linkedCardIdsJson),
        linkedCharacterIds = parseJsonArrayString(linkedCharacterIdsJson),
        updatedAt = updatedAt,
        isUserCreated = true
    )

    private fun RpCharacterEntity.toModel() = RpCharacter(
        id = id,
        name = name,
        title = title,
        avatarUrl = avatarUrl,
        faction = faction,
        catchphrase = catchphrase,
        summonChant = summonChant,
        linkedDeckId = linkedDeckId,
        currentLp = currentLp,
        bio = bio,
        memorialFilters = parseJsonArrayString(memorialFiltersJson),
        isUserCreated = true
    )

    private fun DiscordResourceEntity.toModel() = DiscordResource(
        id = id,
        name = name,
        category = category,
        description = description,
        inviteUrl = inviteUrl,
        tags = parseJsonArrayString(tagsJson),
        isUserCreated = true
    )

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { optString(it) }.filter { it.isNotBlank() }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val map = linkedMapOf<String, String>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = optString(key)
        }
        return map
    }

    private fun parseJsonArrayString(raw: String): List<String> = try {
        JSONArray(raw).toStringList()
    } catch (e: Exception) {
        Log.d(TAG, "Fallback empty list for malformed JSON array: ${e.message}")
        emptyList()
    }

    private fun parseJsonMapString(raw: String): Map<String, String> = try {
        JSONObject(raw).toStringMap()
    } catch (e: Exception) {
        Log.d(TAG, "Fallback empty map for malformed JSON map: ${e.message}")
        emptyMap()
    }
}
