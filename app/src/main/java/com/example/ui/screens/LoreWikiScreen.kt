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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AVATAR_MEMORIAL_FILTERS_19_21
import com.example.data.model.CardEntry
import com.example.data.model.DeckEntry
import com.example.data.model.RpCharacter
import com.example.data.model.WikiArticle
import com.example.data.util.BigIntMath
import com.example.data.util.JsonExportUtil
import com.example.ui.DiscordBroadcastDraft
import com.example.ui.NexusViewModel
import com.example.ui.components.JsonExportActionRow
import com.example.ui.components.MarkdownLiteRenderer
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
import java.util.UUID

@Composable
fun LoreWikiScreen(
    viewModel: NexusViewModel,
    modifier: Modifier = Modifier
) {
    val articles by viewModel.wikiArticles.collectAsStateWithLifecycle()
    val characters by viewModel.rpCharacters.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val highlightedLoreId by viewModel.highlightedLoreId.collectAsStateWithLifecycle()

    var subTab by remember { mutableIntStateOf(0) } // 0 = Wiki Pages, 1 = RP Characters
    var searchQuery by remember { mutableStateOf("") }
    var readingArticle by remember { mutableStateOf<WikiArticle?>(null) }
    var editingArticle by remember { mutableStateOf<WikiArticle?>(null) }
    var showCreateArticle by remember { mutableStateOf(false) }
    var showCreateCharacter by remember { mutableStateOf(false) }

    // Cross-link navigation from Card Vault
    LaunchedEffect(highlightedLoreId, articles, characters) {
        val target = highlightedLoreId ?: return@LaunchedEffect
        val matchedArticle = articles.find { it.id == target }
        if (matchedArticle != null) {
            subTab = 0
            readingArticle = matchedArticle
            viewModel.clearHighlightedLore()
            return@LaunchedEffect
        }
        val matchedChar = characters.find { it.id == target }
        if (matchedChar != null) {
            subTab = 1
            searchQuery = matchedChar.name
            viewModel.clearHighlightedLore()
        }
    }

    val filteredArticles = remember(articles, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) articles else articles.filter {
            it.title.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                it.summary.lowercase().contains(q) ||
                it.markdownContent.lowercase().contains(q)
        }
    }

    val filteredCharacters = remember(characters, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) characters else characters.filter {
            it.name.lowercase().contains(q) ||
                it.title.lowercase().contains(q) ||
                it.faction.lowercase().contains(q) ||
                it.summonChant.lowercase().contains(q)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NexusBackground)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            NexusSectionHeader(
                title = "Lore & RP Wiki",
                subtitle = "Markdown worldbuilding archives & BigInt RP Character Personas",
                trailing = {
                    Button(
                        onClick = {
                            if (subTab == 0) showCreateArticle = true else showCreateCharacter = true
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NexusViolet, contentColor = NexusBackground),
                        modifier = Modifier.testTag("lore_create_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (subTab == 0) "Wiki" else "Persona", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }

        TabRow(
            selectedTabIndex = subTab,
            containerColor = NexusSurface,
            contentColor = NexusViolet,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[subTab]),
                    color = NexusViolet
                )
            }
        ) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = { Text("Wiki Articles (${articles.size})", style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.testTag("lore_tab_wiki")
            )
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                text = { Text("RP Personas (${characters.size})", style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.testTag("lore_tab_personas")
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search lore, factions, timelines, or personas...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search lore", tint = NexusViolet) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lore_search_input")
                )
            }

            if (subTab == 0) {
                if (filteredArticles.isEmpty()) {
                    item {
                        NexusEmptyStateCard(
                            title = "No wiki articles found",
                            subtitle = "Create a new Markdown lore entry and export it straight to GitHub."
                        )
                    }
                } else {
                    items(filteredArticles, key = { it.id }) { article ->
                        WikiArticleCard(
                            article = article,
                            onRead = { readingArticle = article },
                            onEdit = { editingArticle = article }
                        )
                    }
                }
            } else {
                if (filteredCharacters.isEmpty()) {
                    item {
                        NexusEmptyStateCard(
                            title = "No RP personas found",
                            subtitle = "Create an RP Character Persona with summon chant, deck link, and BigInt LP."
                        )
                    }
                } else {
                    items(filteredCharacters, key = { it.id }) { persona ->
                        RpCharacterCard(
                            character = persona,
                            linkedDeck = decks.find { it.id == persona.linkedDeckId },
                            onLoadDeck = { deck -> viewModel.loadDeckIntoTabletop(deck, navigateToBoard = true) },
                            onBroadcastChant = {
                                viewModel.prepareBroadcastAndNavigate(
                                    DiscordBroadcastDraft(
                                        username = persona.name,
                                        avatarUrl = persona.avatarUrl,
                                        title = "${persona.name} — ${persona.title}",
                                        body = "**Summon Chant:**\n> \"${persona.summonChant}\"\n\n*\"${persona.catchphrase}\"*",
                                        footer = "Faction: ${persona.faction} · Nexus Master Hub",
                                        accentHex = 0xB388FF,
                                        fields = listOf(
                                            "Current LP" to BigIntMath.formatWithCommas(persona.currentLp),
                                            "Faction" to persona.faction.ifBlank { "Independent" }
                                        )
                                    )
                                )
                            },
                            onDelete = if (persona.isUserCreated) {
                                { viewModel.deleteRpCharacter(persona.id) }
                            } else null,
                            onFeedback = { viewModel.showStatus(it) }
                        )
                    }
                }
            }
        }
    }

    // Wiki Article Reader Dialog with Cross-Links & Export
    readingArticle?.let { article ->
        val linkedCards = cards.filter { it.id in article.linkedCardIds }
        val linkedChars = characters.filter { it.id in article.linkedCharacterIds }
        val jsonPayload = remember(article) { JsonExportUtil.wikiArticleToJson(article) }
        val mdPayload = remember(article) { JsonExportUtil.wikiArticleToMarkdown(article) }

        AlertDialog(
            onDismissRequest = { readingArticle = null },
            containerColor = NexusCard,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NexusPillBadge(text = article.category, accentColor = NexusViolet)
                    Text(article.title, style = MaterialTheme.typography.titleLarge, color = NexusTextPrimary)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MarkdownLiteRenderer(markdown = article.markdownContent)

                    if (linkedCards.isNotEmpty()) {
                        Text("CROSS-LINKED CARDS", style = MaterialTheme.typography.labelSmall, color = NexusCyan)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            linkedCards.forEach { card ->
                                OutlinedButton(
                                    onClick = {
                                        readingArticle = null
                                        viewModel.openCardInVault(card.id)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, NexusCyan.copy(alpha = 0.6f))
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = NexusCyan)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(card.name, style = MaterialTheme.typography.labelSmall, color = NexusCyan)
                                }
                            }
                        }
                    }

                    if (linkedChars.isNotEmpty()) {
                        Text("CROSS-LINKED PERSONAS", style = MaterialTheme.typography.labelSmall, color = NexusGold)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            linkedChars.forEach { ch ->
                                OutlinedButton(
                                    onClick = {
                                        readingArticle = null
                                        subTab = 1
                                        searchQuery = ch.name
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, NexusGold.copy(alpha = 0.6f))
                                ) {
                                    Text(ch.name, style = MaterialTheme.typography.labelSmall, color = NexusGold)
                                }
                            }
                        }
                    }

                    JsonExportActionRow(
                        jsonPayload = jsonPayload,
                        exportTitle = article.id,
                        markdownPayload = mdPayload,
                        onFeedback = { viewModel.showStatus(it) }
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        editingArticle = article
                        readingArticle = null
                    }) {
                        Text("Edit", color = NexusGold)
                    }
                    TextButton(onClick = { readingArticle = null }) {
                        Text("Close", color = NexusCyan)
                    }
                }
            }
        )
    }

    if (showCreateArticle || editingArticle != null) {
        WikiEditorDialog(
            initial = editingArticle,
            availableCards = cards,
            onDismiss = {
                showCreateArticle = false
                editingArticle = null
            },
            onSave = { saved ->
                viewModel.saveWikiArticle(saved)
                showCreateArticle = false
                editingArticle = null
                readingArticle = saved
            }
        )
    }

    if (showCreateCharacter) {
        CreateCharacterDialog(
            decks = decks,
            onDismiss = { showCreateCharacter = false },
            onSave = { persona ->
                viewModel.saveRpCharacter(persona)
                showCreateCharacter = false
            }
        )
    }
}

@Composable
private fun WikiArticleCard(
    article: WikiArticle,
    onRead: () -> Unit,
    onEdit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NexusCard,
        border = BorderStroke(1.dp, NexusBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onRead)
            .testTag("wiki_article_${article.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NexusPillBadge(text = article.category, accentColor = NexusViolet)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (article.updatedAt.isNotBlank()) {
                        Text(article.updatedAt, style = MaterialTheme.typography.labelSmall, color = NexusTextMuted)
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit wiki page", tint = NexusTextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(article.title, style = MaterialTheme.typography.titleLarge, color = NexusTextPrimary)
            Text(
                text = article.summary.ifBlank { article.markdownContent.take(120) },
                style = MaterialTheme.typography.bodySmall,
                color = NexusTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (article.linkedCardIds.isNotEmpty() || article.linkedCharacterIds.isNotEmpty()) {
                Text(
                    text = "${article.linkedCardIds.size} linked cards · ${article.linkedCharacterIds.size} linked personas",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusCyan
                )
            }
        }
    }
}

@Composable
private fun RpCharacterCard(
    character: RpCharacter,
    linkedDeck: DeckEntry?,
    onLoadDeck: (DeckEntry) -> Unit,
    onBroadcastChant: () -> Unit,
    onDelete: (() -> Unit)?,
    onFeedback: (String) -> Unit
) {
    val json = remember(character) { JsonExportUtil.characterToJson(character) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = NexusCard,
        border = BorderStroke(1.dp, NexusViolet.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("persona_card_${character.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NexusArtworkImage(
                    imageUrl = character.avatarUrl,
                    contentDescription = character.name,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(character.name, style = MaterialTheme.typography.titleLarge, color = NexusTextPrimary)
                    Text(character.title, style = MaterialTheme.typography.labelMedium, color = NexusViolet)
                    if (character.faction.isNotBlank()) {
                        Text(character.faction, style = MaterialTheme.typography.labelSmall, color = NexusTextMuted)
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete persona", tint = NexusDanger)
                    }
                }
            }

            // BigInt LP Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = NexusSurface,
                border = BorderStroke(1.dp, NexusCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CURRENT LP (BIGINT)", style = MaterialTheme.typography.labelSmall, color = NexusTextMuted)
                    Text(
                        text = BigIntMath.formatWithCommas(character.currentLp),
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.labelLarge,
                        color = NexusCyan
                    )
                }
            }

            if (character.catchphrase.isNotBlank()) {
                Text(
                    text = "\"${character.catchphrase}\"",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = NexusGold
                )
            }

            if (character.memorialFilters.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    character.memorialFilters.forEach { f ->
                        NexusPillBadge(text = f, accentColor = NexusGold)
                    }
                }
            }

            if (character.summonChant.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NexusBackground,
                    border = BorderStroke(1.dp, NexusBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("SUMMON CHANT", style = MaterialTheme.typography.labelSmall, color = NexusViolet)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(character.summonChant, style = MaterialTheme.typography.bodySmall, color = NexusTextPrimary)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBroadcastChant,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("persona_broadcast_${character.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = NexusViolet, contentColor = NexusBackground)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RP Broadcast", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }

                if (linkedDeck != null) {
                    OutlinedButton(
                        onClick = { onLoadDeck(linkedDeck) },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, NexusCyan),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusCyan)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Load Deck", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            JsonExportActionRow(
                jsonPayload = json,
                exportTitle = character.id,
                onFeedback = onFeedback
            )
        }
    }
}

@Composable
private fun WikiEditorDialog(
    initial: WikiArticle?,
    availableCards: List<CardEntry>,
    onDismiss: () -> Unit,
    onSave: (WikiArticle) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "World Lore") }
    var summary by remember { mutableStateOf(initial?.summary ?: "") }
    var markdown by remember { mutableStateOf(initial?.markdownContent ?: "# New Lore Entry\n\nWrite your world lore in **Markdown** here.\n\n- Key faction or rule\n- Timeline milestone") }
    var previewMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusCard,
        title = { Text(if (initial == null) "Create Wiki Page" else "Edit Wiki Page", color = NexusTextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !previewMode, onClick = { previewMode = false }, label = { Text("Markdown") })
                    FilterChip(selected = previewMode, onClick = { previewMode = true }, label = { Text("Preview") })
                }

                if (previewMode) {
                    MarkdownLiteRenderer(markdown = markdown)
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Article Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("World Lore", "Faction", "Timeline", "Ruleset").forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = summary,
                        onValueChange = { summary = it },
                        label = { Text("Short Summary") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = markdown,
                        onValueChange = { markdown = it },
                        label = { Text("Markdown Content") },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.trim().isBlank()) return@Button
                    val slug = title.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    onSave(
                        WikiArticle(
                            id = initial?.id ?: "wiki-${slug.ifBlank { "page" }}-${UUID.randomUUID().toString().take(4)}",
                            title = title.trim(),
                            category = category,
                            summary = summary.trim(),
                            markdownContent = markdown,
                            linkedCardIds = initial?.linkedCardIds ?: availableCards.take(1).map { it.id },
                            linkedCharacterIds = initial?.linkedCharacterIds ?: emptyList(),
                            updatedAt = "2026-11-22",
                            isUserCreated = true
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusViolet, contentColor = NexusBackground)
            ) { Text("Save Wiki") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = NexusTextSecondary) }
        }
    )
}

@Composable
private fun CreateCharacterDialog(
    decks: List<DeckEntry>,
    onDismiss: () -> Unit,
    onSave: (RpCharacter) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var faction by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("local://img_avatar_duelist") }
    var catchphrase by remember { mutableStateOf("") }
    var summonChant by remember { mutableStateOf("") }
    var currentLp by remember { mutableStateOf("8000") }
    var linkedDeckId by remember { mutableStateOf(decks.firstOrNull()?.id ?: "") }
    val selectedMemorialFilters = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusCard,
        title = { Text("Create RP Character Persona", color = NexusTextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Character Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / Epithet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = faction,
                    onValueChange = { faction = it },
                    label = { Text("Faction / Guild") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentLp,
                    onValueChange = { currentLp = it },
                    label = { Text("Current BigInt LP (e.g. 999999999999999999999)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = catchphrase,
                    onValueChange = { catchphrase = it },
                    label = { Text("Catchphrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = summonChant,
                    onValueChange = { summonChant = it },
                    label = { Text("Signature Summon Chant") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = { avatarUrl = it },
                    label = { Text("Avatar URL (https:// or local://img_avatar_duelist)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Avatar / Memorial Forge Filters 19-21 (Dossiers Only)",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusGold
                )
                AVATAR_MEMORIAL_FILTERS_19_21.forEach { filterLabel ->
                    val checked = filterLabel in selectedMemorialFilters
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) selectedMemorialFilters.remove(filterLabel)
                                else selectedMemorialFilters.add(filterLabel)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                if (isChecked && filterLabel !in selectedMemorialFilters) {
                                    selectedMemorialFilters.add(filterLabel)
                                } else if (!isChecked) {
                                    selectedMemorialFilters.remove(filterLabel)
                                }
                            }
                        )
                        Text(
                            text = filterLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusTextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isBlank()) return@Button
                    val slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    onSave(
                        RpCharacter(
                            id = "char-${slug.ifBlank { "rp" }}-${UUID.randomUUID().toString().take(4)}",
                            name = name.trim(),
                            title = title.trim().ifBlank { "Nexus Duelist" },
                            avatarUrl = avatarUrl.trim(),
                            faction = faction.trim().ifBlank { "Independent" },
                            catchphrase = catchphrase.trim(),
                            summonChant = summonChant.trim(),
                            linkedDeckId = linkedDeckId,
                            currentLp = if (BigIntMath.isValidBigInt(currentLp)) {
                                BigIntMath.parseOrZero(currentLp).toString()
                            } else "8000",
                            bio = "",
                            memorialFilters = selectedMemorialFilters.toList(),
                            isUserCreated = true
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusViolet, contentColor = NexusBackground)
            ) { Text("Save Persona") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = NexusTextSecondary) }
        }
    )
}
