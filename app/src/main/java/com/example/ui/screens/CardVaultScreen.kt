package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CardEntry
import com.example.data.model.CardPackEntry
import com.example.data.model.DeckEntry
import com.example.data.util.BigIntMath
import com.example.data.util.JsonExportUtil
import com.example.ui.NexusViewModel
import com.example.ui.components.JsonExportActionRow
import com.example.ui.components.NexusArtworkImage
import com.example.ui.components.NexusEmptyStateCard
import com.example.ui.components.NexusPillBadge
import com.example.ui.components.NexusSectionHeader
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NexusBackground
import com.example.ui.theme.NexusBorder
import com.example.ui.theme.NexusCard
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusGold
import com.example.ui.theme.NexusSurface
import com.example.ui.theme.NexusTextMuted
import com.example.ui.theme.NexusTextPrimary
import com.example.ui.theme.NexusTextSecondary
import com.example.ui.theme.NexusViolet
import kotlin.math.ceil

private const val PAGE_SIZE = 8

@Composable
fun CardVaultScreen(
    viewModel: NexusViewModel,
    modifier: Modifier = Modifier
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val packs by viewModel.cardPacks.collectAsStateWithLifecycle()
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val highlightedCardId by viewModel.highlightedCardId.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var subTab by remember { mutableIntStateOf(0) } // 0 = Cards, 1 = Packs, 2 = Decks
    var searchQuery by remember { mutableStateOf("") }
    var selectedRuleset by remember { mutableStateOf("All") }
    var currentPage by remember { mutableIntStateOf(0) }

    var inspectingCard by remember { mutableStateOf<CardEntry?>(null) }
    var showCreateCardDialog by remember { mutableStateOf(false) }
    var showImportPackDialog by remember { mutableStateOf(false) }
    var showCreateDeckDialog by remember { mutableStateOf(false) }

    // Open deep-linked card from Wiki cross-links
    LaunchedEffect(highlightedCardId, cards) {
        if (highlightedCardId != null) {
            cards.find { it.id == highlightedCardId }?.let { found ->
                subTab = 0
                inspectingCard = found
                viewModel.clearHighlightedCard()
            }
        }
    }

    val rulesets = remember(cards) {
        listOf("All") + cards.map { it.ruleset }.distinct().sorted()
    }

    val filteredCards = remember(cards, searchQuery, selectedRuleset) {
        val q = searchQuery.trim().lowercase()
        cards.filter { card ->
            val rulesetOk = selectedRuleset == "All" || card.ruleset.equals(selectedRuleset, ignoreCase = true)
            val queryOk = q.isBlank() ||
                card.name.lowercase().contains(q) ||
                card.codeString.lowercase().contains(q) ||
                card.abilityText.lowercase().contains(q) ||
                card.ruleset.lowercase().contains(q) ||
                card.attributes.any { (k, v) -> k.lowercase().contains(q) || v.lowercase().contains(q) }
            rulesetOk && queryOk
        }.sortedWith { a, b -> BigIntMath.compareNumericCode(b.codeString, a.codeString) }
    }

    val totalPages = remember(filteredCards.size) {
        maxOf(1, ceil(filteredCards.size / PAGE_SIZE.toDouble()).toInt())
    }
    LaunchedEffect(filteredCards.size, searchQuery, selectedRuleset) {
        if (currentPage >= totalPages) currentPage = 0
    }
    val pagedCards = remember(filteredCards, currentPage) {
        val from = (currentPage * PAGE_SIZE).coerceAtMost(filteredCards.size)
        val to = (from + PAGE_SIZE).coerceAtMost(filteredCards.size)
        filteredCards.subList(from, to)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NexusBackground)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            NexusSectionHeader(
                title = "Infinite Card Vault",
                subtitle = "Arbitrary-length BigInt IDs, extensible attributes & federated JSON packs",
                trailing = {
                    Button(
                        onClick = {
                            when (subTab) {
                                0 -> showCreateCardDialog = true
                                1 -> showImportPackDialog = true
                                else -> showCreateDeckDialog = true
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground),
                        modifier = Modifier.testTag("vault_primary_create_button")
                    ) {
                        Icon(
                            imageVector = if (subTab == 1) Icons.Default.CloudDownload else Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (subTab) {
                                0 -> "Card"
                                1 -> "Import"
                                else -> "Deck"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        }

        TabRow(
            selectedTabIndex = subTab,
            containerColor = NexusSurface,
            contentColor = NexusCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[subTab]),
                    color = NexusCyan
                )
            }
        ) {
            listOf("Cards (${cards.size})", "Packs (${packs.size})", "Decks (${decks.size})").forEachIndexed { idx, label ->
                Tab(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.testTag("vault_tab_$idx")
                )
            }
        }

        when (subTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; currentPage = 0 },
                            placeholder = { Text("Search name, 21-digit code, UUID, ATK, Mana...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search cards", tint = NexusCyan) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_search_input")
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rulesets.forEach { rs ->
                                val selected = selectedRuleset == rs
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedRuleset = rs; currentPage = 0 },
                                    label = { Text(rs, style = MaterialTheme.typography.labelMedium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NexusCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = NexusCyan,
                                        containerColor = NexusSurface,
                                        labelColor = NexusTextSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = NexusBorder,
                                        selectedBorderColor = NexusCyan
                                    )
                                )
                            }
                        }
                    }

                    if (pagedCards.isEmpty()) {
                        item {
                            NexusEmptyStateCard(
                                title = "No cards found",
                                subtitle = "Create a custom card or import a federated JSON pack URL."
                            )
                        }
                    } else {
                        items(pagedCards, key = { it.id }) { card ->
                            VaultCardListItem(
                                card = card,
                                onClick = { inspectingCard = card }
                            )
                        }

                        // Pagination Bar for fast mobile browsing of large libraries
                        item {
                            Surface(
                                color = NexusSurface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, NexusBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { if (currentPage > 0) currentPage-- },
                                        enabled = currentPage > 0,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Prev")
                                    }
                                    Text(
                                        text = "Page ${currentPage + 1} / $totalPages  (${filteredCards.size} total)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexusTextSecondary
                                    )
                                    OutlinedButton(
                                        onClick = { if (currentPage < totalPages - 1) currentPage++ },
                                        enabled = currentPage < totalPages - 1,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("Next")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(packs, key = { it.id }) { pack ->
                        val packCards = cards.filter { it.packId == pack.id }
                        CardPackListItem(
                            pack = pack,
                            cardsInPack = packCards,
                            onFeedback = { viewModel.showStatus(it) }
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(decks, key = { it.id }) { deck ->
                        DeckListItem(
                            deck = deck,
                            allCards = cards,
                            onPlayInTabletop = { viewModel.loadDeckIntoTabletop(deck, navigateToBoard = true) },
                            onDelete = if (deck.isUserCreated) {
                                { viewModel.deleteDeck(deck.id) }
                            } else null,
                            onFeedback = { viewModel.showStatus(it) }
                        )
                    }
                }
            }
        }
    }

    inspectingCard?.let { card ->
        CardDetailDialog(
            card = card,
            onDismiss = { inspectingCard = null },
            onSendToTabletop = {
                viewModel.loadSingleCardIntoHand(card, navigateToBoard = true)
                inspectingCard = null
            },
            onOpenLore = if (card.loreLink.isNotBlank()) {
                {
                    viewModel.openLoreOrCharacter(card.loreLink)
                    inspectingCard = null
                }
            } else null,
            onDelete = if (card.isUserCreated) {
                {
                    viewModel.deleteCard(card.id)
                    inspectingCard = null
                }
            } else null,
            onFeedback = { viewModel.showStatus(it) }
        )
    }

    if (showCreateCardDialog) {
        CreateCardDialog(
            packs = packs,
            onDismiss = { showCreateCardDialog = false },
            onSave = { newCard ->
                viewModel.saveCard(newCard)
                showCreateCardDialog = false
                inspectingCard = newCard
            }
        )
    }

    if (showImportPackDialog) {
        ImportFederatedPackDialog(
            isBusy = isBusy,
            onDismiss = { showImportPackDialog = false },
            onImportUrl = { url ->
                viewModel.importFederatedPackUrl(url) { ok, _ ->
                    if (ok) showImportPackDialog = false
                }
            },
            onImportRawJson = { raw ->
                viewModel.importFederatedPackJson(raw) { ok, _ ->
                    if (ok) showImportPackDialog = false
                }
            }
        )
    }

    if (showCreateDeckDialog) {
        DeckBuilderDialog(
            allCards = cards,
            onDismiss = { showCreateDeckDialog = false },
            onSave = { deck ->
                viewModel.saveDeck(deck)
                showCreateDeckDialog = false
            }
        )
    }
}

@Composable
private fun VaultCardListItem(
    card: CardEntry,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NexusCard,
        border = BorderStroke(1.dp, NexusBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("vault_card_${card.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NexusArtworkImage(
                imageUrl = card.imageUrl,
                contentDescription = card.name,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusPillBadge(
                        text = card.ruleset,
                        accentColor = when {
                            card.ruleset.contains("Hybrid", true) -> NexusCyan
                            card.ruleset.contains("Magic", true) -> NexusViolet
                            else -> NexusGold
                        }
                    )
                    Text(
                        text = BigIntMath.describeCodeType(card.codeString),
                        style = MaterialTheme.typography.labelSmall,
                        color = NexusTextMuted
                    )
                }

                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = NexusTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "# ${card.codeString}",
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (card.attributes.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        card.attributes.entries.take(4).forEach { (k, v) ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NexusSurface
                            ) {
                                Text(
                                    text = "$k: $v",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NexusTextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardPackListItem(
    pack: CardPackEntry,
    cardsInPack: List<CardEntry>,
    onFeedback: (String) -> Unit
) {
    val exportJson = remember(pack, cardsInPack) {
        JsonExportUtil.packWithCardsToJson(pack, cardsInPack)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NexusCard,
        border = BorderStroke(1.dp, NexusBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NexusPillBadge(text = pack.ruleset, accentColor = NexusViolet)
                Text(
                    text = "${cardsInPack.size} cards · v${pack.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusGold
                )
            }
            Text(text = pack.name, style = MaterialTheme.typography.titleLarge, color = NexusTextPrimary)
            Text(text = pack.description, style = MaterialTheme.typography.bodySmall, color = NexusTextSecondary)
            Text(
                text = "Source: ${pack.sourceUrl}",
                style = MaterialTheme.typography.labelSmall,
                color = NexusTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            JsonExportActionRow(
                jsonPayload = exportJson,
                exportTitle = pack.id,
                onFeedback = onFeedback
            )
        }
    }
}

@Composable
private fun DeckListItem(
    deck: DeckEntry,
    allCards: List<CardEntry>,
    onPlayInTabletop: () -> Unit,
    onDelete: (() -> Unit)?,
    onFeedback: (String) -> Unit
) {
    val deckJson = remember(deck) { JsonExportUtil.deckToJson(deck) }
    val cardMap = remember(allCards) { allCards.associateBy { it.id } }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NexusCard,
        border = BorderStroke(1.dp, NexusBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Style, contentDescription = null, tint = NexusCyan, modifier = Modifier.size(18.dp))
                    NexusPillBadge(text = deck.ruleset, accentColor = NexusCyan)
                    Text("${deck.cardIds.size} cards", style = MaterialTheme.typography.labelSmall, color = NexusGold)
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete deck", tint = NexusDanger)
                    }
                }
            }
            Text(text = deck.name, style = MaterialTheme.typography.titleLarge, color = NexusTextPrimary)
            Text(text = deck.description, style = MaterialTheme.typography.bodySmall, color = NexusTextSecondary)

            val previewNames = deck.cardIds.take(4).mapNotNull { cardMap[it]?.name }
            if (previewNames.isNotEmpty()) {
                Text(
                    text = "Includes: ${previewNames.joinToString(" · ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusTextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onPlayInTabletop,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("load_deck_${deck.id}"),
                colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Load Deck in Tabletop")
            }

            JsonExportActionRow(
                jsonPayload = deckJson,
                exportTitle = deck.id,
                onFeedback = onFeedback
            )
        }
    }
}
