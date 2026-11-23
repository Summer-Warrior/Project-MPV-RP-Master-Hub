package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.NexusDatabase
import com.example.data.local.NexusPreferences
import com.example.data.model.AppEntry
import com.example.data.model.BoardCardInstance
import com.example.data.model.BoardLayoutMode
import com.example.data.model.BoardZone
import com.example.data.model.CardEntry
import com.example.data.model.CardOrientation
import com.example.data.model.CardPackEntry
import com.example.data.model.ChainStackItem
import com.example.data.model.DeckEntry
import com.example.data.model.DiscordResource
import com.example.data.model.RpCharacter
import com.example.data.model.TabletopLogEntry
import com.example.data.model.TabletopState
import com.example.data.model.TurnPhase
import com.example.data.model.WikiArticle
import com.example.data.network.NexusNetworkClient
import com.example.data.repository.NexusRepository
import com.example.data.util.BigIntMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class DiscordBroadcastDraft(
    val username: String = "Nexus Arbiter",
    val avatarUrl: String = "",
    val title: String = "",
    val body: String = "",
    val footer: String = "Sent from Nexus Master Hub",
    val accentHex: Int = 0x00E5FF,
    val fields: List<Pair<String, String>> = emptyList()
)

class NexusViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NexusDatabase.getInstance(application)
    private val preferences = NexusPreferences(application)
    private val networkClient = NexusNetworkClient()
    private val repository = NexusRepository(
        context = application,
        dao = database.nexusDao(),
        preferences = preferences,
        networkClient = networkClient
    )

    val apps: StateFlow<List<AppEntry>> = repository.apps.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val cardPacks: StateFlow<List<CardPackEntry>> = repository.cardPacks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val cards: StateFlow<List<CardEntry>> = repository.cards.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val decks: StateFlow<List<DeckEntry>> = repository.decks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val wikiArticles: StateFlow<List<WikiArticle>> = repository.wikiArticles.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val rpCharacters: StateFlow<List<RpCharacter>> = repository.rpCharacters.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val discordResources: StateFlow<List<DiscordResource>> = repository.discordResources.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val webhookUrl: StateFlow<String> = repository.webhookUrl.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    // Cross-screen deep selection & draft states
    private val _selectedNavIndex = MutableStateFlow(0)
    val selectedNavIndex: StateFlow<Int> = _selectedNavIndex.asStateFlow()

    private val _highlightedCardId = MutableStateFlow<String?>(null)
    val highlightedCardId: StateFlow<String?> = _highlightedCardId.asStateFlow()

    private val _highlightedLoreId = MutableStateFlow<String?>(null)
    val highlightedLoreId: StateFlow<String?> = _highlightedLoreId.asStateFlow()

    private val _broadcastDraft = MutableStateFlow(DiscordBroadcastDraft())
    val broadcastDraft: StateFlow<DiscordBroadcastDraft> = _broadcastDraft.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // ONE SINGLE UNIFIED TABLETOP STATE STORE
    private val _tabletopState = MutableStateFlow(
        TabletopState(
            layoutMode = BoardLayoutMode.YUGIOH_5_ZONE,
            zones = buildZonesForLayout(BoardLayoutMode.YUGIOH_5_ZONE)
        )
    )
    val tabletopState: StateFlow<TabletopState> = _tabletopState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.loadBundledRegistries()
            // Auto-load the first starter deck into the unified tabletop for immediate playability
            val loadedDecks = repository.decks.first { it.isNotEmpty() }
            val loadedCards = repository.cards.first { it.isNotEmpty() }
            loadedDecks.firstOrNull()?.let { starter ->
                loadDeckInternal(starter, loadedCards)
            }
        }
    }

    fun selectTab(index: Int) {
        _selectedNavIndex.value = index.coerceIn(0, 4)
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun showStatus(message: String) {
        _statusMessage.value = message
    }

    fun openCardInVault(cardId: String) {
        _highlightedCardId.value = cardId
        _selectedNavIndex.value = 1
    }

    fun clearHighlightedCard() {
        _highlightedCardId.value = null
    }

    fun openLoreOrCharacter(targetId: String) {
        _highlightedLoreId.value = targetId
        _selectedNavIndex.value = 3
    }

    fun clearHighlightedLore() {
        _highlightedLoreId.value = null
    }

    fun prepareBroadcastAndNavigate(draft: DiscordBroadcastDraft) {
        _broadcastDraft.value = draft
        _selectedNavIndex.value = 4
    }

    fun updateBroadcastDraft(transform: (DiscordBroadcastDraft) -> DiscordBroadcastDraft) {
        _broadcastDraft.update(transform)
    }

    // --- Hub Mutations ---

    fun togglePinApp(app: AppEntry) {
        viewModelScope.launch {
            repository.togglePinApp(app.id, app.isPinned)
        }
    }

    fun saveUserApp(app: AppEntry) {
        viewModelScope.launch {
            repository.saveUserApp(app)
            showStatus("Saved \"${app.name}\" to local Hub registry.")
        }
    }

    fun deleteUserApp(id: String) {
        viewModelScope.launch {
            repository.deleteUserApp(id)
            showStatus("Removed custom app manifest.")
        }
    }

    // --- Card Vault Mutations ---

    fun saveCard(card: CardEntry) {
        viewModelScope.launch {
            repository.saveCard(card)
            showStatus("Saved card \"${card.name}\" (${card.codeString}).")
        }
    }

    fun deleteCard(id: String) {
        viewModelScope.launch {
            repository.deleteCard(id)
            showStatus("Deleted custom card.")
        }
    }

    fun saveCardPack(pack: CardPackEntry) {
        viewModelScope.launch {
            repository.saveCardPack(pack)
            showStatus("Created pack \"${pack.name}\".")
        }
    }

    fun importFederatedPackUrl(url: String, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.importFederatedPackFromUrl(url)
            _isBusy.value = false
            result.fold(
                onSuccess = { imported ->
                    val msg = "Imported \"${imported.pack.name}\" (${imported.cards.size} cards)."
                    showStatus(msg)
                    onDone(true, msg)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Failed to import card pack."
                    showStatus(msg)
                    onDone(false, msg)
                }
            )
        }
    }

    fun importFederatedPackJson(rawJson: String, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = repository.importFederatedPackFromRawJson(rawJson)
            result.fold(
                onSuccess = { imported ->
                    val msg = "Imported \"${imported.pack.name}\" (${imported.cards.size} cards)."
                    showStatus(msg)
                    onDone(true, msg)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Invalid JSON."
                    showStatus(msg)
                    onDone(false, msg)
                }
            )
        }
    }

    fun saveDeck(deck: DeckEntry) {
        viewModelScope.launch {
            repository.saveDeck(deck)
            showStatus("Saved deck \"${deck.name}\" (${deck.cardIds.size} cards).")
        }
    }

    fun deleteDeck(id: String) {
        viewModelScope.launch {
            repository.deleteDeck(id)
            showStatus("Deleted deck.")
        }
    }

    // --- Lore & Characters Mutations ---

    fun saveWikiArticle(article: WikiArticle) {
        viewModelScope.launch {
            repository.saveWikiArticle(article)
            showStatus("Saved wiki entry \"${article.title}\".")
        }
    }

    fun deleteWikiArticle(id: String) {
        viewModelScope.launch {
            repository.deleteWikiArticle(id)
            showStatus("Deleted custom wiki entry.")
        }
    }

    fun saveRpCharacter(character: RpCharacter) {
        viewModelScope.launch {
            repository.saveRpCharacter(character)
            showStatus("Saved RP persona \"${character.name}\".")
        }
    }

    fun deleteRpCharacter(id: String) {
        viewModelScope.launch {
            repository.deleteRpCharacter(id)
            showStatus("Deleted RP persona.")
        }
    }

    // --- Discord RP & Webhook ---

    fun saveDiscordResource(resource: DiscordResource) {
        viewModelScope.launch {
            repository.saveDiscordResource(resource)
            showStatus("Added \"${resource.name}\" to Discord directory.")
        }
    }

    fun deleteDiscordResource(id: String) {
        viewModelScope.launch {
            repository.deleteDiscordResource(id)
            showStatus("Removed custom Discord entry.")
        }
    }

    fun saveWebhookUrl(url: String) {
        viewModelScope.launch {
            repository.saveWebhookUrl(url)
            showStatus(if (url.isBlank()) "Cleared saved webhook URL." else "Saved Discord Webhook locally.")
        }
    }

    fun sendDiscordBroadcast(
        webhookUrlInput: String,
        draft: DiscordBroadcastDraft,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.broadcastDiscordRp(
                webhookUrl = webhookUrlInput,
                username = draft.username,
                avatarUrl = draft.avatarUrl,
                title = draft.title,
                description = draft.body,
                footer = draft.footer,
                colorDecimal = draft.accentHex,
                fields = draft.fields
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { msg ->
                    repository.saveWebhookUrl(webhookUrlInput)
                    showStatus(msg)
                    onComplete(true, msg)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Broadcast error."
                    showStatus(msg)
                    onComplete(false, msg)
                }
            )
        }
    }

    // =========================================================================
    // SINGLE UNIFIED TABLETOP ENGINE ACTIONS (ONE STATE STORE)
    // =========================================================================

    fun switchBoardLayout(mode: BoardLayoutMode) {
        _tabletopState.update { current ->
            val placedCards = current.zones.mapNotNull { it.cardInstance }
            val newZones = buildZonesForLayout(mode).toMutableList()
            placedCards.forEachIndexed { idx, inst ->
                if (idx < newZones.size) {
                    newZones[idx] = newZones[idx].copy(cardInstance = inst)
                }
            }
            val overflowToHand = if (placedCards.size > newZones.size) {
                placedCards.drop(newZones.size)
            } else emptyList()

            current.copy(
                layoutMode = mode,
                zones = newZones,
                hand = current.hand + overflowToHand,
                inspectingZoneId = null,
                eventLog = appendLog(current, "Switched unified board to ${mode.label}.")
            )
        }
    }

    fun loadDeckIntoTabletop(deck: DeckEntry, navigateToBoard: Boolean = false) {
        loadDeckInternal(deck, cards.value)
        if (navigateToBoard) {
            _selectedNavIndex.value = 2
        }
        showStatus("Loaded \"${deck.name}\" into Tabletop.")
    }

    fun loadSingleCardIntoHand(card: CardEntry, navigateToBoard: Boolean = true) {
        _tabletopState.update { current ->
            val inst = BoardCardInstance(
                instanceId = UUID.randomUUID().toString(),
                card = card
            )
            current.copy(
                hand = current.hand + inst,
                selectedHandInstanceId = inst.instanceId,
                eventLog = appendLog(current, "Added \"${card.name}\" directly to Hand.")
            )
        }
        if (navigateToBoard) _selectedNavIndex.value = 2
    }

    private fun loadDeckInternal(deck: DeckEntry, allCards: List<CardEntry>) {
        val cardMap = allCards.associateBy { it.id }
        val resolved = deck.cardIds.mapNotNull { cardMap[it] }.ifEmpty { allCards.take(8) }
        val shuffled = resolved.shuffled()
        val openingHandCount = minOf(4, shuffled.size)
        val openingHand = shuffled.take(openingHandCount).map {
            BoardCardInstance(instanceId = UUID.randomUUID().toString(), card = it)
        }
        val remainingLibrary = shuffled.drop(openingHandCount)

        val defaultLp = when {
            deck.ruleset.contains("Magic", ignoreCase = true) -> "20"
            else -> "8000"
        }

        _tabletopState.update { current ->
            current.copy(
                activeDeckId = deck.id,
                activeDeckName = deck.name,
                lifePoints = defaultLp,
                resourcePool = "3",
                turnNumber = 1,
                currentPhase = TurnPhase.MAIN,
                library = remainingLibrary,
                hand = openingHand,
                graveyard = emptyList(),
                zones = buildZonesForLayout(current.layoutMode),
                selectedHandInstanceId = null,
                inspectingZoneId = null,
                chainStack = emptyList(),
                eventLog = listOf(
                    TabletopLogEntry(
                        id = UUID.randomUUID().toString(),
                        turn = 1,
                        phase = TurnPhase.MAIN.shortCode,
                        message = "Loaded \"${deck.name}\" (${resolved.size} cards), drew $openingHandCount."
                    )
                )
            )
        }
    }

    fun drawCard() {
        _tabletopState.update { current ->
            if (current.library.isEmpty()) {
                return@update current.copy(
                    eventLog = appendLog(current, "Deck is empty — no cards left to draw.")
                )
            }
            val top = current.library.first()
            val inst = BoardCardInstance(UUID.randomUUID().toString(), top)
            current.copy(
                library = current.library.drop(1),
                hand = current.hand + inst,
                eventLog = appendLog(current, "Drew \"${top.name}\" (${current.library.size - 1} left).")
            )
        }
    }

    fun shuffleLibrary() {
        _tabletopState.update { current ->
            current.copy(
                library = current.library.shuffled(),
                eventLog = appendLog(current, "Shuffled deck (${current.library.size} cards).")
            )
        }
    }

    fun selectHandCard(instanceId: String?) {
        _tabletopState.update { current ->
            current.copy(
                selectedHandInstanceId = if (current.selectedHandInstanceId == instanceId) null else instanceId,
                inspectingZoneId = null
            )
        }
    }

    fun onZoneTapped(zoneId: String) {
        _tabletopState.update { current ->
            val zone = current.zones.find { it.zoneId == zoneId } ?: return@update current
            val selectedId = current.selectedHandInstanceId

            if (selectedId != null && zone.cardInstance == null) {
                val handCard = current.hand.find { it.instanceId == selectedId } ?: return@update current
                val updatedZones = current.zones.map {
                    if (it.zoneId == zoneId) it.copy(cardInstance = handCard) else it
                }
                current.copy(
                    zones = updatedZones,
                    hand = current.hand.filterNot { it.instanceId == selectedId },
                    selectedHandInstanceId = null,
                    inspectingZoneId = zoneId,
                    eventLog = appendLog(current, "Deployed \"${handCard.card.name}\" to ${zone.label}.")
                )
            } else if (zone.cardInstance != null) {
                // Tap an occupied zone to inspect/control it (or toggle orientation if already inspecting)
                if (current.inspectingZoneId == zoneId) {
                    val toggled = if (zone.cardInstance.orientation == CardOrientation.ATTACK_UNTAPPED) {
                        CardOrientation.DEFENSE_TAPPED
                    } else {
                        CardOrientation.ATTACK_UNTAPPED
                    }
                    val updatedZones = current.zones.map {
                        if (it.zoneId == zoneId) {
                            it.copy(cardInstance = zone.cardInstance.copy(orientation = toggled))
                        } else it
                    }
                    current.copy(
                        zones = updatedZones,
                        eventLog = appendLog(
                            current,
                            "Rotated \"${zone.cardInstance.card.name}\" in ${zone.label} -> ${toggled.label}."
                        )
                    )
                } else {
                    current.copy(inspectingZoneId = zoneId, selectedHandInstanceId = null)
                }
            } else {
                current.copy(inspectingZoneId = null)
            }
        }
    }

    fun quickDeploySelectedHandCard() {
        _tabletopState.update { current ->
            val selectedId = current.selectedHandInstanceId ?: return@update current
            val openZone = current.zones.firstOrNull { it.cardInstance == null } ?: return@update current.copy(
                eventLog = appendLog(current, "No open zones available on the board.")
            )
            val handCard = current.hand.find { it.instanceId == selectedId } ?: return@update current
            val updatedZones = current.zones.map {
                if (it.zoneId == openZone.zoneId) it.copy(cardInstance = handCard) else it
            }
            current.copy(
                zones = updatedZones,
                hand = current.hand.filterNot { it.instanceId == selectedId },
                selectedHandInstanceId = null,
                inspectingZoneId = openZone.zoneId,
                eventLog = appendLog(current, "Quick-deployed \"${handCard.card.name}\" to ${openZone.label}.")
            )
        }
    }

    fun toggleZoneCardOrientation(zoneId: String) {
        _tabletopState.update { current ->
            val zone = current.zones.find { it.zoneId == zoneId } ?: return@update current
            val inst = zone.cardInstance ?: return@update current
            val next = if (inst.orientation == CardOrientation.ATTACK_UNTAPPED) {
                CardOrientation.DEFENSE_TAPPED
            } else {
                CardOrientation.ATTACK_UNTAPPED
            }
            current.copy(
                zones = current.zones.map {
                    if (it.zoneId == zoneId) it.copy(cardInstance = inst.copy(orientation = next)) else it
                },
                eventLog = appendLog(current, "Set \"${inst.card.name}\" to ${next.label}.")
            )
        }
    }

    fun adjustZoneCardCounters(zoneId: String, delta: Int) {
        _tabletopState.update { current ->
            val zone = current.zones.find { it.zoneId == zoneId } ?: return@update current
            val inst = zone.cardInstance ?: return@update current
            val updatedCount = (inst.counters + delta).coerceAtLeast(0)
            current.copy(
                zones = current.zones.map {
                    if (it.zoneId == zoneId) it.copy(cardInstance = inst.copy(counters = updatedCount)) else it
                },
                eventLog = appendLog(current, "\"${inst.card.name}\" counters -> $updatedCount.")
            )
        }
    }

    fun returnZoneCardToHand(zoneId: String) {
        _tabletopState.update { current ->
            val zone = current.zones.find { it.zoneId == zoneId } ?: return@update current
            val inst = zone.cardInstance ?: return@update current
            current.copy(
                zones = current.zones.map { if (it.zoneId == zoneId) it.copy(cardInstance = null) else it },
                hand = current.hand + inst.copy(orientation = CardOrientation.ATTACK_UNTAPPED, counters = 0),
                inspectingZoneId = null,
                eventLog = appendLog(current, "Returned \"${inst.card.name}\" from ${zone.label} to Hand.")
            )
        }
    }

    fun sendZoneCardToGraveyard(zoneId: String) {
        _tabletopState.update { current ->
            val zone = current.zones.find { it.zoneId == zoneId } ?: return@update current
            val inst = zone.cardInstance ?: return@update current
            current.copy(
                zones = current.zones.map { if (it.zoneId == zoneId) it.copy(cardInstance = null) else it },
                graveyard = listOf(inst.card) + current.graveyard,
                inspectingZoneId = null,
                eventLog = appendLog(current, "Sent \"${inst.card.name}\" from ${zone.label} to Graveyard/Exile.")
            )
        }
    }

    fun adjustLifePoints(delta: String) {
        _tabletopState.update { current ->
            val updated = BigIntMath.add(current.lifePoints, delta)
            current.copy(
                lifePoints = updated,
                eventLog = appendLog(current, "LP adjusted by $delta -> ${BigIntMath.formatWithCommas(updated)}.")
            )
        }
    }

    fun multiplyLifePoints(factor: String) {
        _tabletopState.update { current ->
            val updated = BigIntMath.multiply(current.lifePoints, factor)
            current.copy(
                lifePoints = updated,
                eventLog = appendLog(current, "LP multiplied by x$factor -> ${BigIntMath.formatWithCommas(updated)}.")
            )
        }
    }

    fun setLifePointsExact(exactRaw: String) {
        if (!BigIntMath.isValidBigInt(exactRaw)) {
            showStatus("Enter a valid integer (any number of digits).")
            return
        }
        val normalized = BigIntMath.parseOrZero(exactRaw).toString()
        _tabletopState.update { current ->
            current.copy(
                lifePoints = normalized,
                eventLog = appendLog(current, "LP set to ${BigIntMath.formatWithCommas(normalized)}.")
            )
        }
    }

    fun adjustResourcePool(delta: String) {
        _tabletopState.update { current ->
            val updated = BigIntMath.subtract(current.resourcePool, "-$delta", clampZero = false)
            val added = BigIntMath.add(current.resourcePool, delta)
            val clamped = if (BigIntMath.parseOrZero(added).signum() < 0) "0" else added
            current.copy(
                resourcePool = clamped,
                eventLog = appendLog(current, "Resource pool -> ${BigIntMath.formatWithCommas(clamped)}.")
            )
        }
    }

    fun setResourcePoolExact(exactRaw: String) {
        if (!BigIntMath.isValidBigInt(exactRaw)) return
        val normalized = BigIntMath.parseOrZero(exactRaw).toString()
        _tabletopState.update { current ->
            current.copy(
                resourcePool = normalized,
                eventLog = appendLog(current, "Resource pool set to ${BigIntMath.formatWithCommas(normalized)}.")
            )
        }
    }

    fun selectPhase(phase: TurnPhase) {
        _tabletopState.update { current ->
            current.copy(
                currentPhase = phase,
                eventLog = appendLog(current, "Entered ${phase.label} Phase.")
            )
        }
    }

    fun advancePhase() {
        _tabletopState.update { current ->
            val phases = TurnPhase.entries
            val nextIdx = (phases.indexOf(current.currentPhase) + 1) % phases.size
            val nextTurn = if (nextIdx == 0) current.turnNumber + 1 else current.turnNumber
            val nextPhase = phases[nextIdx]
            current.copy(
                turnNumber = nextTurn,
                currentPhase = nextPhase,
                eventLog = appendLog(
                    current.copy(turnNumber = nextTurn, currentPhase = nextPhase),
                    if (nextIdx == 0) "Turn $nextTurn started — Draw Phase." else "Entered ${nextPhase.label} Phase."
                )
            )
        }
    }

    fun pushChainLink(title: String, detail: String, sourceCardId: String? = null) {
        if (title.isBlank()) return
        _tabletopState.update { current ->
            val linkNum = current.chainStack.size + 1
            val item = ChainStackItem(
                linkId = UUID.randomUUID().toString(),
                linkNumber = linkNum,
                title = title.trim(),
                detail = detail.trim(),
                sourceCardId = sourceCardId
            )
            current.copy(
                chainStack = current.chainStack + item,
                eventLog = appendLog(current, "Chain/Stack Link $linkNum added: \"${item.title}\".")
            )
        }
    }

    fun resolveTopChainLink() {
        _tabletopState.update { current ->
            val top = current.chainStack.lastOrNull() ?: return@update current
            current.copy(
                chainStack = current.chainStack.dropLast(1),
                eventLog = appendLog(current, "Resolved Link ${top.linkNumber}: \"${top.title}\" (LIFO).")
            )
        }
    }

    fun clearChainStack() {
        _tabletopState.update { current ->
            if (current.chainStack.isEmpty()) return@update current
            current.copy(
                chainStack = emptyList(),
                eventLog = appendLog(current, "Cleared Chain/Stack.")
            )
        }
    }

    fun toggleEventLogExpanded() {
        _tabletopState.update { it.copy(isEventLogExpanded = !it.isEventLogExpanded) }
    }

    private fun appendLog(state: TabletopState, message: String): List<TabletopLogEntry> {
        val entry = TabletopLogEntry(
            id = UUID.randomUUID().toString(),
            turn = state.turnNumber,
            phase = state.currentPhase.shortCode,
            message = message
        )
        return (listOf(entry) + state.eventLog).take(60)
    }

    private fun buildZonesForLayout(mode: BoardLayoutMode): List<BoardZone> {
        return when (mode) {
            BoardLayoutMode.YUGIOH_5_ZONE -> listOf(
                BoardZone("ygo-m1", "M1", "FRONT"),
                BoardZone("ygo-m2", "M2", "FRONT"),
                BoardZone("ygo-m3", "M3", "FRONT"),
                BoardZone("ygo-m4", "M4", "FRONT"),
                BoardZone("ygo-m5", "M5", "FRONT"),
                BoardZone("ygo-s1", "S/T 1", "BACK"),
                BoardZone("ygo-s2", "S/T 2", "BACK"),
                BoardZone("ygo-s3", "S/T 3", "BACK"),
                BoardZone("ygo-s4", "S/T 4", "BACK"),
                BoardZone("ygo-s5", "S/T 5", "BACK"),
                BoardZone("ygo-field", "Field", "SPECIAL"),
                BoardZone("ygo-emz", "Extra", "SPECIAL")
            )
            BoardLayoutMode.SPEED_DUEL_3_ZONE -> listOf(
                BoardZone("spd-m1", "M1", "FRONT"),
                BoardZone("spd-m2", "M2", "FRONT"),
                BoardZone("spd-m3", "M3", "FRONT"),
                BoardZone("spd-s1", "S/T 1", "BACK"),
                BoardZone("spd-s2", "S/T 2", "BACK"),
                BoardZone("spd-s3", "S/T 3", "BACK"),
                BoardZone("spd-skill", "Skill / Field", "SPECIAL")
            )
            BoardLayoutMode.MTG_BATTLEFIELD -> listOf(
                BoardZone("mtg-c1", "Creature 1", "FRONT"),
                BoardZone("mtg-c2", "Creature 2", "FRONT"),
                BoardZone("mtg-c3", "Creature 3", "FRONT"),
                BoardZone("mtg-pw", "Planeswalker", "FRONT"),
                BoardZone("mtg-l1", "Land 1", "BACK"),
                BoardZone("mtg-l2", "Land 2", "BACK"),
                BoardZone("mtg-l3", "Land 3", "BACK"),
                BoardZone("mtg-art", "Artifact/Ench", "BACK"),
                BoardZone("mtg-cmd", "Command", "SPECIAL")
            )
            BoardLayoutMode.HYBRID_6_LANE -> listOf(
                BoardZone("hyb-l1", "Lane 1", "FRONT"),
                BoardZone("hyb-l2", "Lane 2", "FRONT"),
                BoardZone("hyb-l3", "Lane 3", "FRONT"),
                BoardZone("hyb-l4", "Lane 4", "BACK"),
                BoardZone("hyb-l5", "Lane 5", "BACK"),
                BoardZone("hyb-l6", "Lane 6", "BACK"),
                BoardZone("hyb-omni", "Omni-Nexus", "SPECIAL")
            )
        }
    }
}
