package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.CardEntry
import com.example.data.model.CardPackEntry
import com.example.data.model.DeckEntry
import com.example.data.model.NEXUS_20_COLOR_PIE
import com.example.data.model.StandardTurnFlowFlag
import com.example.data.util.BigIntMath
import com.example.data.util.JsonExportUtil
import com.example.ui.components.JsonExportActionRow
import com.example.ui.components.NexusArtworkImage
import com.example.ui.components.NexusPillBadge
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
import java.math.BigInteger
import java.util.UUID
import kotlin.random.Random

@Composable
fun CardDetailDialog(
    card: CardEntry,
    onDismiss: () -> Unit,
    onSendToTabletop: () -> Unit,
    onOpenLore: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onFeedback: (String) -> Unit
) {
    val cardJson = remember(card) { JsonExportUtil.cardToJson(card) }
    val isPureNumber = remember(card.codeString) {
        card.codeString.isNotBlank() && card.codeString.all { it.isDigit() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusCard,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NexusPillBadge(text = card.ruleset, accentColor = NexusCyan)
                Text(card.name, style = MaterialTheme.typography.titleLarge, color = NexusTextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NexusArtworkImage(
                    imageUrl = card.imageUrl,
                    contentDescription = card.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Surface(
                    color = NexusSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NexusCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = BigIntMath.describeCodeType(card.codeString).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusGold
                        )
                        Text(
                            text = card.codeString,
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NexusCyan
                        )
                        if (isPureNumber) {
                            val nextCode = BigInteger(card.codeString).add(BigInteger.ONE).toString()
                            Text(
                                text = "BigInt verified (+1 -> $nextCode)",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusTextMuted
                            )
                        }
                    }
                }

                if (card.abilityText.isNotBlank()) {
                    Text(
                        text = card.abilityText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexusTextPrimary
                    )
                }

                if (card.attributes.isNotEmpty()) {
                    Text("EXTENSIBLE ATTRIBUTES", style = MaterialTheme.typography.labelSmall, color = NexusViolet)
                    Surface(
                        color = NexusSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NexusBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            card.attributes.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(key, style = MaterialTheme.typography.labelMedium, color = NexusTextSecondary)
                                    Text(value, style = MaterialTheme.typography.labelMedium, color = NexusTextPrimary)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onSendToTabletop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_send_to_tabletop"),
                    colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add to Tabletop Hand")
                }

                if (onOpenLore != null) {
                    OutlinedButton(
                        onClick = onOpenLore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_open_lore_link"),
                        border = BorderStroke(1.dp, NexusViolet),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusViolet)
                    ) {
                        Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Linked Lore / Persona")
                    }
                }

                JsonExportActionRow(
                    jsonPayload = cardJson,
                    exportTitle = card.id,
                    onFeedback = onFeedback
                )

                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                        Text("Delete Custom Card", color = NexusDanger)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = NexusCyan)
            }
        }
    )
}

@Composable
fun CreateCardDialog(
    packs: List<CardPackEntry>,
    onDismiss: () -> Unit,
    onSave: (CardEntry) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var codeString by remember { mutableStateOf("999999999999999999999") }
    var ruleset by remember { mutableStateOf("Hybrid Crossover") }
    var packId by remember { mutableStateOf(packs.firstOrNull()?.id ?: "pack-custom") }
    var imageUrl by remember { mutableStateOf("local://img_card_art_nexus") }
    var loreLink by remember { mutableStateOf("") }
    var abilityText by remember { mutableStateOf("") }
    var attrKey by remember { mutableStateOf("") }
    var attrValue by remember { mutableStateOf("") }
    val selectedColors = remember { mutableStateListOf<String>() } // 20-Color Pie starts with 0 default colors selected!
    var turnFlowFlag by remember { mutableStateOf("End Turn") } // Strictly End Turn or Extra Turn only (zero time-travel flags)
    val attributes = remember {
        mutableStateListOf(
            "ATK" to "3500",
            "DEF" to "2800",
            "Mana Cost" to "3UB"
        )
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusCard,
        title = { Text("Custom Card Creator", color = NexusTextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("Card Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_card_name")
                )

                OutlinedTextField(
                    value = codeString,
                    onValueChange = { codeString = it; errorText = null },
                    label = { Text("Code ID (8-digit, 21+ digit BigInt, or UUID)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_card_code")
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val sb = StringBuilder("9")
                            repeat(20) { sb.append(Random.nextInt(0, 10)) }
                            codeString = sb.toString()
                        }
                    ) { Text("21-Digit BigInt", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = { codeString = Random.nextInt(10_000_000, 99_999_999).toString() }
                    ) { Text("8-Digit", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = { codeString = UUID.randomUUID().toString() }
                    ) { Text("UUID", style = MaterialTheme.typography.labelSmall) }
                }

                Text("Ruleset", style = MaterialTheme.typography.labelSmall, color = NexusCyan)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Hybrid Crossover", "Yu-Gi-Oh!", "Magic: The Gathering", "Custom RP").forEach { rs ->
                        FilterChip(
                            selected = ruleset == rs,
                            onClick = { ruleset = rs },
                            label = { Text(rs, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text(
                    text = "20-Color Pie (${selectedColors.size} selected — starts at 0)",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusGold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NEXUS_20_COLOR_PIE.forEach { (colorName, colorHex) ->
                        val isSelected = colorName in selectedColors
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selectedColors.remove(colorName)
                                else selectedColors.add(colorName)
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorHex))
                                )
                            },
                            label = { Text(colorName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("card_pie_${colorName.lowercase()}")
                        )
                    }
                }

                Text(
                    text = "Standard Match Turn-Flow (End Turn / Extra Turn only — zero time-travel flags)",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusViolet
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StandardTurnFlowFlag.entries.forEach { flag ->
                        FilterChip(
                            selected = turnFlowFlag == flag.label,
                            onClick = { turnFlowFlag = flag.label },
                            label = { Text(flag.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("card_turn_flow_${flag.name.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = abilityText,
                    onValueChange = { abilityText = it },
                    label = { Text("Ability / Effect / Script Text") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("Image URL (https:// or local://img_card_art_nexus)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = loreLink,
                    onValueChange = { loreLink = it },
                    label = { Text("Lore / Character Cross-Link ID (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Extensible Attributes (Key / Value)", style = MaterialTheme.typography.labelSmall, color = NexusGold)
                attributes.forEachIndexed { idx, (k, v) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("$k: $v", style = MaterialTheme.typography.bodySmall, color = NexusTextPrimary)
                        IconButton(onClick = { attributes.removeAt(idx) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove attribute", tint = NexusTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = attrKey,
                        onValueChange = { attrKey = it },
                        label = { Text("Key") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = attrValue,
                        onValueChange = { attrValue = it },
                        label = { Text("Value") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (attrKey.isNotBlank() && attrValue.isNotBlank()) {
                                attributes.add(attrKey.trim() to attrValue.trim())
                                attrKey = ""
                                attrValue = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add attribute", tint = NexusCyan)
                    }
                }

                errorText?.let { Text(it, color = NexusDanger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isBlank() || codeString.trim().isBlank()) {
                        errorText = "Name and Code ID are required."
                        return@Button
                    }
                    val slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    onSave(
                        CardEntry(
                            id = "${slug.ifBlank { "card" }}-${UUID.randomUUID().toString().take(5)}",
                            codeString = codeString.trim(),
                            name = name.trim(),
                            ruleset = ruleset,
                            packId = packId,
                            imageUrl = imageUrl.trim(),
                            loreLink = loreLink.trim(),
                            abilityText = abilityText.trim(),
                            attributes = linkedMapOf<String, String>().apply {
                                attributes.forEach { (k, v) -> put(k, v) }
                                put("Turn Flow", turnFlowFlag)
                                if (selectedColors.isNotEmpty()) {
                                    put("Colors", selectedColors.joinToString(", "))
                                }
                            },
                            isUserCreated = true
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground),
                modifier = Modifier.testTag("save_card_button")
            ) {
                Text("Save Card")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NexusTextSecondary)
            }
        }
    )
}

@Composable
fun ImportFederatedPackDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onImportUrl: (String) -> Unit,
    onImportRawJson: (String) -> Unit
) {
    var modeUrl by remember { mutableStateOf(true) }
    var packUrl by remember { mutableStateOf("https://raw.githubusercontent.com/") }
    var rawJson by remember {
        mutableStateOf(
            """
            {
              "id": "pack-community-sample",
              "name": "Community Custom Pack",
              "ruleset": "Hybrid Crossover",
              "version": "1.0.0",
              "cards": [
                {
                  "codeString": "987654321098765432101",
                  "name": "Nebula Forge Titan",
                  "abilityText": "Deploy onto any open lane. Gains +1000 ATK per linked card.",
                  "attributes": { "ATK": "3200", "DEF": "2900", "Mana Cost": "4UR" }
                }
              ]
            }
            """.trimIndent()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusCard,
        title = { Text("Import Federated Card Pack", color = NexusTextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = modeUrl,
                        onClick = { modeUrl = true },
                        label = { Text("Raw HTTPS URL") }
                    )
                    FilterChip(
                        selected = !modeUrl,
                        onClick = { modeUrl = false },
                        label = { Text("Paste JSON") }
                    )
                }

                if (modeUrl) {
                    Text(
                        text = "Paste a raw GitHub or HTTPS JSON Card Pack URL to fetch and store locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusTextSecondary
                    )
                    OutlinedTextField(
                        value = packUrl,
                        onValueChange = { packUrl = it },
                        label = { Text("HTTPS JSON Pack URL") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_pack_url_input")
                    )
                } else {
                    Text(
                        text = "Paste a Card Pack JSON object or array of cards to import offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusTextSecondary
                    )
                    OutlinedTextField(
                        value = rawJson,
                        onValueChange = { rawJson = it },
                        label = { Text("Card Pack JSON") },
                        minLines = 6,
                        maxLines = 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_pack_json_input")
                    )
                }

                if (isBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NexusCyan, strokeWidth = 2.dp)
                        Text("Fetching federated pack...", style = MaterialTheme.typography.bodySmall, color = NexusCyan)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = { if (modeUrl) onImportUrl(packUrl) else onImportRawJson(rawJson) },
                colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground),
                modifier = Modifier.testTag("confirm_import_pack_button")
            ) {
                Text("Import Pack")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NexusTextSecondary)
            }
        }
    )
}

@Composable
fun DeckBuilderDialog(
    allCards: List<CardEntry>,
    onDismiss: () -> Unit,
    onSave: (DeckEntry) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var ruleset by remember { mutableStateOf("Hybrid Crossover") }
    var description by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexusCard,
        title = { Text("Deck Builder (${selectedIds.size} selected)", color = NexusTextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("Deck Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deck Strategy / Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Hybrid Crossover", "Yu-Gi-Oh!", "Magic: The Gathering", "Speed Duel").forEach { rs ->
                        FilterChip(
                            selected = ruleset == rs,
                            onClick = { ruleset = rs },
                            label = { Text(rs, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Select Cards from Vault", style = MaterialTheme.typography.labelSmall, color = NexusCyan)
                allCards.forEach { card ->
                    val checked = card.id in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (checked) selectedIds.remove(card.id) else selectedIds.add(card.id)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                if (isChecked && card.id !in selectedIds) selectedIds.add(card.id)
                                else if (!isChecked) selectedIds.remove(card.id)
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(card.name, style = MaterialTheme.typography.bodyMedium, color = NexusTextPrimary)
                            Text(
                                "${card.ruleset} · ${card.codeString}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusTextMuted
                            )
                        }
                    }
                }

                errorText?.let { Text(it, color = NexusDanger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isBlank() || selectedIds.isEmpty()) {
                        errorText = "Enter a deck name and select at least 1 card."
                        return@Button
                    }
                    val slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    onSave(
                        DeckEntry(
                            id = "deck-${slug.ifBlank { "custom" }}-${UUID.randomUUID().toString().take(4)}",
                            name = name.trim(),
                            ruleset = ruleset,
                            description = description.trim().ifBlank { "Custom cross-ruleset deck." },
                            cardIds = selectedIds.toList(),
                            isUserCreated = true
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground)
            ) {
                Text("Save Deck")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NexusTextSecondary)
            }
        }
    )
}
