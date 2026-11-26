package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BoardCardInstance
import com.example.data.model.BoardLayoutMode
import com.example.data.model.BoardZone
import com.example.data.model.CardOrientation
import com.example.data.model.TurnPhase
import com.example.data.util.BigIntMath
import com.example.ui.DiscordBroadcastDraft
import com.example.ui.NexusViewModel
import com.example.ui.components.NexusArtworkImage
import com.example.ui.components.NexusPillBadge
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NexusBackground
import com.example.ui.theme.NexusBorder
import com.example.ui.theme.NexusCard
import com.example.ui.theme.NexusCardElevated
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusDanger
import com.example.ui.theme.NexusGold
import com.example.ui.theme.NexusSuccess
import com.example.ui.theme.NexusSurface
import com.example.ui.theme.NexusTextMuted
import com.example.ui.theme.NexusTextPrimary
import com.example.ui.theme.NexusTextSecondary
import com.example.ui.theme.NexusViolet

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import com.example.data.model.ChatChannelType
import com.example.data.model.NEXUS_20_COLOR_PIE
import com.example.data.model.StandardTurnFlowFlag
import com.example.data.model.TabletopRuleEngineMode

/**
 * ONE single unified playmat-first Tabletop board backed by ONE single state store.
 * Preserves Notebook TCG guardrails: Flagship Ultimate TCG (WIP) separate from AI/Hybrid Combo Mixer,
 * 20-Color Pie starting with 0 default colors, End Turn / Extra Turn only (zero time-travel flags),
 * and strict separation between IC Chat, OOC Normal, and OOC Ruling-Call logs.
 */
@Composable
fun TabletopScreen(
    viewModel: NexusViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.tabletopState.collectAsStateWithLifecycle()
    val decks by viewModel.decks.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()

    var showDeckPicker by remember { mutableStateOf(false) }
    var showBigIntEditor by remember { mutableStateOf(false) }
    var showAddStackDialog by remember { mutableStateOf(false) }
    var showColorPie by remember { mutableStateOf(false) }
    var activeChatChannel by remember { mutableStateOf(ChatChannelType.IC_CHAT) }
    var chatInput by remember { mutableStateOf("") }

    val frontZones = remember(state.zones) { state.zones.filter { it.rowGroup == "FRONT" } }
    val backZones = remember(state.zones) { state.zones.filter { it.rowGroup == "BACK" } }
    val specialZones = remember(state.zones) { state.zones.filter { it.rowGroup == "SPECIAL" } }
    val inspectingZone = remember(state.zones, state.inspectingZoneId) {
        state.zones.find { it.zoneId == state.inspectingZoneId }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NexusBackground),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header + Separate Ruleset Track (Flagship Ultimate TCG WIP vs AI/Hybrid Combo Mixer) + Board Layout Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unified Playmat",
                            style = MaterialTheme.typography.headlineSmall,
                            color = NexusTextPrimary
                        )
                        Text(
                            text = "${state.engineMode.label} · Deck: ${state.activeDeckName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { showDeckPicker = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, NexusCyan.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusCyan),
                            modifier = Modifier.testTag("tabletop_deck_picker")
                        ) {
                            Icon(Icons.Default.Style, contentDescription = "Select Deck", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deck", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(
                            onClick = {
                                val placedCount = state.zones.count { it.cardInstance != null }
                                viewModel.prepareBroadcastAndNavigate(
                                    DiscordBroadcastDraft(
                                        username = "Nexus Tabletop Relay",
                                        title = "Duel Telemetry — Turn ${state.turnNumber} (${state.currentPhase.label})",
                                        body = "Track: **${state.engineMode.label}**\nBoard: **${state.layoutMode.label}**\nTurn Flag: **${state.activeTurnFlowFlag.label}**\nLatest Action: ${state.eventLog.firstOrNull()?.message ?: "Board ready."}",
                                        accentHex = 0x00E5FF,
                                        fields = listOf(
                                            "Life Points" to BigIntMath.formatWithCommas(state.lifePoints),
                                            "Resource Pool" to BigIntMath.formatWithCommas(state.resourcePool),
                                            "Units on Board" to "$placedCount",
                                            "Chain/Stack" to "${state.chainStack.size} links"
                                        )
                                    )
                                )
                            },
                            modifier = Modifier.testTag("tabletop_broadcast_discord")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Broadcast duel status to Discord",
                                tint = NexusViolet
                            )
                        }
                    }
                }

                // Separate Flagship Ultimate TCG (WIP) vs AI/Hybrid Combo Mixer selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabletopRuleEngineMode.entries.forEach { mode ->
                        val selected = state.engineMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setTabletopEngineMode(mode) },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("engine_track_${mode.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NexusViolet.copy(alpha = 0.22f),
                                selectedLabelColor = NexusViolet,
                                containerColor = NexusSurface,
                                labelColor = NexusTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = NexusBorder,
                                selectedBorderColor = NexusViolet
                            )
                        )
                    }
                }

                // Board Layout Selector + 20-Color Pie toggle + Standard Turn-Flow Flags (End Turn / Extra Turn ONLY)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BoardLayoutMode.entries.forEach { mode ->
                        val selected = state.layoutMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.switchBoardLayout(mode) },
                            label = { Text(mode.shortLabel, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("board_mode_${mode.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NexusCyan.copy(alpha = 0.22f),
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
                    FilterChip(
                        selected = showColorPie,
                        onClick = { showColorPie = !showColorPie },
                        label = { Text("20-Color Pie (${state.selectedPieColors.size})", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("toggle_20_color_pie")
                    )
                }

                // Collapsible 20-Color Pie (starts with 0 default colors selected!)
                AnimatedVisibility(visible = showColorPie) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NexusSurface,
                        border = BorderStroke(1.dp, NexusBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "20-COLOR PIE (${state.selectedPieColors.size}/20 selected · 0 default)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NexusGold
                                )
                                if (state.selectedPieColors.isNotEmpty()) {
                                    TextButton(onClick = { viewModel.clearPieColors() }, contentPadding = PaddingValues(0.dp)) {
                                        Text("Clear", style = MaterialTheme.typography.labelSmall, color = NexusTextMuted)
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                NEXUS_20_COLOR_PIE.forEach { (colorName, hex) ->
                                    val selected = colorName in state.selectedPieColors
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.togglePieColor(colorName) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(hex))
                                            )
                                        },
                                        label = { Text(colorName, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Standard Match Turn-Flow Guardrail: ONLY End Turn and Extra Turn allowed (zero time-travel flags)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MATCH FLAG:", style = MaterialTheme.typography.labelSmall, color = NexusTextMuted)
                    StandardTurnFlowFlag.entries.forEach { flag ->
                        val active = state.activeTurnFlowFlag == flag
                        FilterChip(
                            selected = active,
                            onClick = { viewModel.setTurnFlowFlag(flag) },
                            label = { Text(flag.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NexusGold.copy(alpha = 0.22f),
                                selectedLabelColor = NexusGold
                            )
                        )
                    }
                }
            }
        }

        // 2. ONE BigInt Life Points & Resource Counter Bar
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NexusCard,
                border = BorderStroke(1.dp, NexusCyan.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("LIFE POINTS (BIGINT)", style = MaterialTheme.typography.labelSmall, color = NexusTextMuted)
                            Text(
                                text = BigIntMath.formatWithCommas(state.lifePoints),
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = NexusCyan,
                                modifier = Modifier.testTag("tabletop_lp_value")
                            )
                        }
                        Surface(
                            color = NexusSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, NexusGold.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = "Resource pool", tint = NexusGold, modifier = Modifier.size(16.dp))
                                Text(
                                    text = BigIntMath.formatWithCommas(state.resourcePool),
                                    fontFamily = MonoFontFamily,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NexusGold
                                )
                                TextButton(
                                    onClick = { viewModel.adjustResourcePool("-1") },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(28.dp)
                                ) { Text("-", color = NexusTextSecondary) }
                                TextButton(
                                    onClick = { viewModel.adjustResourcePool("1") },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(28.dp)
                                ) { Text("+", color = NexusGold) }
                            }
                        }
                        IconButton(
                            onClick = { showBigIntEditor = true },
                            modifier = Modifier.testTag("edit_bigint_lp_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit exact BigInt LP", tint = NexusGold)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LpQuickChip("-1000", NexusDanger) { viewModel.adjustLifePoints("-1000") }
                        LpQuickChip("-500", NexusDanger) { viewModel.adjustLifePoints("-500") }
                        LpQuickChip("+500", NexusSuccess) { viewModel.adjustLifePoints("500") }
                        LpQuickChip("+1000", NexusSuccess) { viewModel.adjustLifePoints("1000") }
                        LpQuickChip("x10", NexusViolet) { viewModel.multiplyLifePoints("10") }
                        LpQuickChip("21-Digit Max", NexusGold) { viewModel.setLifePointsExact("999999999999999999999") }
                    }
                }
            }
        }

        // 3. 5-Phase Turn Indicator
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = NexusSurface,
                border = BorderStroke(1.dp, NexusBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NexusPillBadge(text = "T${state.turnNumber}", accentColor = NexusGold)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TurnPhase.entries.forEach { phase ->
                            val active = state.currentPhase == phase
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) NexusCyan else NexusCard,
                                border = BorderStroke(1.dp, if (active) NexusCyan else NexusBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.selectPhase(phase) }
                                    .testTag("phase_${phase.name.lowercase()}")
                            ) {
                                Text(
                                    text = phase.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) NexusBackground else NexusTextSecondary,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { viewModel.advancePhase() },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("advance_phase_button")
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next phase", tint = NexusCyan)
                    }
                }
            }
        }

        // 4. THE ONE UNIFIED INTERACTIVE BOARD
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = NexusCard,
                border = BorderStroke(1.dp, NexusCyan.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unified_tabletop_board")
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(NexusCard, NexusSurface, NexusCard)
                            )
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.layoutMode.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusCyan
                        )
                        if (state.selectedHandInstanceId != null) {
                            Text(
                                text = "Tap an empty zone to deploy",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusGold
                            )
                        } else {
                            Text(
                                text = "Tap placed card to inspect / rotate",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusTextMuted
                            )
                        }
                    }

                    // Special Zones (Field / Extra / Skill / Command / Omni-Nexus)
                    if (specialZones.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            specialZones.forEach { zone ->
                                BoardZoneSlot(
                                    zone = zone,
                                    isDeployTarget = state.selectedHandInstanceId != null && zone.cardInstance == null,
                                    isInspecting = state.inspectingZoneId == zone.zoneId,
                                    onClick = { viewModel.onZoneTapped(zone.zoneId) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Frontline / Monster / Lane 1-3 Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        frontZones.forEach { zone ->
                            BoardZoneSlot(
                                zone = zone,
                                isDeployTarget = state.selectedHandInstanceId != null && zone.cardInstance == null,
                                isInspecting = state.inspectingZoneId == zone.zoneId,
                                onClick = { viewModel.onZoneTapped(zone.zoneId) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Backline / Spell-Trap / Lands / Lane 4-6 Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        backZones.forEach { zone ->
                            BoardZoneSlot(
                                zone = zone,
                                isDeployTarget = state.selectedHandInstanceId != null && zone.cardInstance == null,
                                isInspecting = state.inspectingZoneId == zone.zoneId,
                                onClick = { viewModel.onZoneTapped(zone.zoneId) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Inline Active Zone Controls (shows only when a placed card is selected)
                    inspectingZone?.cardInstance?.let { inst ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NexusBackground,
                            border = BorderStroke(1.dp, NexusGold.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${inspectingZone.label}: ${inst.card.name}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = NexusTextPrimary
                                        )
                                        Text(
                                            text = "${inst.orientation.label} · Counters: ${inst.counters}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NexusGold
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        OutlinedButton(
                                            onClick = { viewModel.adjustZoneCardCounters(inspectingZone.zoneId, -1) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) { Text("-1") }
                                        OutlinedButton(
                                            onClick = { viewModel.adjustZoneCardCounters(inspectingZone.zoneId, 1) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) { Text("+1") }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.toggleZoneCardOrientation(inspectingZone.zoneId) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground),
                                        modifier = Modifier.testTag("toggle_orientation_button")
                                    ) {
                                        Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ATK/DEF · Tap", style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.pushChainLink(
                                                title = inst.card.name,
                                                detail = "Activated from ${inspectingZone.label}",
                                                sourceCardId = inst.card.id
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        border = BorderStroke(1.dp, NexusViolet),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusViolet)
                                    ) {
                                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+ Stack", style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.returnZoneCardToHand(inspectingZone.zoneId) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("To Hand", style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.sendZoneCardToGraveyard(inspectingZone.zoneId) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        border = BorderStroke(1.dp, NexusDanger.copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusDanger)
                                    ) {
                                        Text("To GY/Exile", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Hand Dock & Deck Actions
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NexusCard,
                border = BorderStroke(1.dp, NexusBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HAND (${state.hand.size}) · DECK (${state.library.size}) · GY (${state.graveyard.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusTextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (state.selectedHandInstanceId != null) {
                                Button(
                                    onClick = { viewModel.quickDeploySelectedHandCard() },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NexusGold, contentColor = NexusBackground),
                                    modifier = Modifier.testTag("quick_deploy_button")
                                ) {
                                    Text("Deploy", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.shuffleLibrary() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle deck", modifier = Modifier.size(15.dp))
                            }
                            Button(
                                onClick = { viewModel.drawCard() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground),
                                modifier = Modifier.testTag("tabletop_draw_button")
                            ) {
                                Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Draw", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    if (state.hand.isEmpty()) {
                        Text(
                            text = "Hand is empty — tap Draw or add cards from the Card Vault.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusTextMuted,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.hand.forEach { inst ->
                                val isSelected = state.selectedHandInstanceId == inst.instanceId
                                HandCardTile(
                                    instance = inst,
                                    isSelected = isSelected,
                                    onClick = { viewModel.selectHandCard(inst.instanceId) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Manual Chain / Stack Tracker
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NexusCard,
                border = BorderStroke(1.dp, NexusViolet.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = NexusViolet, modifier = Modifier.size(18.dp))
                            Text(
                                text = "CHAIN / STACK (${state.chainStack.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusViolet
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { showAddStackDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                border = BorderStroke(1.dp, NexusViolet),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusViolet),
                                modifier = Modifier.testTag("add_chain_link_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Add Link", style = MaterialTheme.typography.labelSmall)
                            }
                            if (state.chainStack.isNotEmpty()) {
                                Button(
                                    onClick = { viewModel.resolveTopChainLink() },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NexusViolet, contentColor = NexusBackground),
                                    modifier = Modifier.testTag("resolve_chain_button")
                                ) {
                                    Text("Resolve Top", style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(
                                    onClick = { viewModel.clearChainStack() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear stack", tint = NexusTextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    if (state.chainStack.isEmpty()) {
                        Text(
                            text = "Stack empty. Push spell activations, triggers, or counter-traps (resolves LIFO).",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusTextMuted
                        )
                    } else {
                        state.chainStack.reversed().forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NexusSurface,
                                border = BorderStroke(1.dp, NexusBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NexusPillBadge(text = "CL${item.linkNumber}", accentColor = NexusViolet)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.labelLarge, color = NexusTextPrimary)
                                        if (item.detail.isNotBlank()) {
                                            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = NexusTextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. ONE Collapsible Event Log
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NexusSurface,
                border = BorderStroke(1.dp, NexusBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.toggleEventLogExpanded() }
                            .padding(vertical = 2.dp)
                            .testTag("event_log_toggle"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "EVENT LOG (${state.eventLog.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = NexusCyan
                            )
                            if (!state.isEventLogExpanded) {
                                Text(
                                    text = state.eventLog.firstOrNull()?.message ?: "Ready.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NexusTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            imageVector = if (state.isEventLogExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle event log",
                            tint = NexusTextSecondary
                        )
                    }

                    AnimatedVisibility(visible = state.isEventLogExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            state.eventLog.take(20).forEach { entry ->
                                Text(
                                    text = "[T${entry.turn} ${entry.phase}] ${entry.message}",
                                    fontFamily = MonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NexusTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 8. Strictly Separated IC Chat / OOC Normal / OOC Ruling-Call Logs
        item {
            val channelAccent = when (activeChatChannel) {
                ChatChannelType.IC_CHAT -> NexusCyan
                ChatChannelType.OOC_NORMAL -> NexusViolet
                ChatChannelType.OOC_RULING_CALL -> NexusGold
            }
            val channelMessages = remember(chatMessages, activeChatChannel) {
                chatMessages.filter { it.channel == activeChatChannel }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NexusCard,
                border = BorderStroke(1.dp, channelAccent.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("separated_chat_panel")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SEPARATED MATCH CHANNELS",
                            style = MaterialTheme.typography.labelSmall,
                            color = channelAccent
                        )
                        NexusPillBadge(
                            text = activeChatChannel.label,
                            accentColor = channelAccent
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ChatChannelType.entries.forEach { channel ->
                            val chipAccent = when (channel) {
                                ChatChannelType.IC_CHAT -> NexusCyan
                                ChatChannelType.OOC_NORMAL -> NexusViolet
                                ChatChannelType.OOC_RULING_CALL -> NexusGold
                            }
                            FilterChip(
                                selected = activeChatChannel == channel,
                                onClick = { activeChatChannel = channel },
                                label = { Text(channel.label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = chipAccent.copy(alpha = 0.18f),
                                    selectedLabelColor = chipAccent
                                ),
                                modifier = Modifier.testTag("chat_channel_${channel.name.lowercase()}")
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NexusSurface,
                        border = BorderStroke(1.dp, NexusBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (channelMessages.isEmpty()) {
                                Text(
                                    text = "No messages in ${activeChatChannel.label} yet. Logs stay strictly isolated from other channels.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NexusTextMuted
                                )
                            } else {
                                channelMessages.takeLast(8).forEach { msg ->
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "${msg.senderName} · ${msg.channel.shortTag} · ${msg.timestampLabel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = channelAccent
                                        )
                                        Text(
                                            text = msg.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NexusTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = {
                                Text(
                                    text = when (activeChatChannel) {
                                        ChatChannelType.IC_CHAT -> "Speak in-character..."
                                        ChatChannelType.OOC_NORMAL -> "Post OOC banter..."
                                        ChatChannelType.OOC_RULING_CALL -> "Log a formal ruling call..."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_channel_input")
                        )
                        Button(
                            onClick = {
                                if (chatInput.isNotBlank()) {
                                    viewModel.postChatMessage(activeChatChannel, chatInput)
                                    chatInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = channelAccent,
                                contentColor = NexusBackground
                            ),
                            modifier = Modifier.testTag("chat_channel_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send to ${activeChatChannel.label}",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Deck Picker Dialog
    if (showDeckPicker) {
        AlertDialog(
            onDismissRequest = { showDeckPicker = false },
            containerColor = NexusCard,
            title = { Text("Select Deck for Tabletop", color = NexusTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    decks.forEach { deck ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NexusSurface,
                            border = BorderStroke(
                                1.dp,
                                if (deck.id == state.activeDeckId) NexusCyan else NexusBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.loadDeckIntoTabletop(deck)
                                    showDeckPicker = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(deck.name, style = MaterialTheme.typography.titleMedium, color = NexusTextPrimary)
                                Text(
                                    "${deck.ruleset} · ${deck.cardIds.size} cards",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NexusCyan
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeckPicker = false }) { Text("Close", color = NexusCyan) }
            }
        )
    }

    // Arbitrary-Precision BigInt LP & Resource Editor
    if (showBigIntEditor) {
        var lpInput by remember(state.lifePoints) { mutableStateOf(state.lifePoints) }
        var resInput by remember(state.resourcePool) { mutableStateOf(state.resourcePool) }
        AlertDialog(
            onDismissRequest = { showBigIntEditor = false },
            containerColor = NexusCard,
            title = { Text("BigInt LP & Resource Editor", color = NexusTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Supports arbitrary-length integers (21+ digits up to and beyond 999999999999999999999).",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusTextSecondary
                    )
                    OutlinedTextField(
                        value = lpInput,
                        onValueChange = { lpInput = it },
                        label = { Text("Exact Life Points") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bigint_lp_input")
                    )
                    OutlinedTextField(
                        value = resInput,
                        onValueChange = { resInput = it },
                        label = { Text("Exact Mana / Resource Pool") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setLifePointsExact(lpInput)
                        viewModel.setResourcePoolExact(resInput)
                        showBigIntEditor = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexusCyan, contentColor = NexusBackground)
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showBigIntEditor = false }) { Text("Cancel", color = NexusTextSecondary) }
            }
        )
    }

    // Push Manual Chain/Stack Link Dialog
    if (showAddStackDialog) {
        var title by remember { mutableStateOf("") }
        var detail by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddStackDialog = false },
            containerColor = NexusCard,
            title = { Text("Add Chain / Stack Link", color = NexusTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Effect / Spell Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = detail,
                        onValueChange = { detail = it },
                        label = { Text("Target / Resolution Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            viewModel.pushChainLink(title, detail)
                            showAddStackDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexusViolet, contentColor = NexusBackground)
                ) { Text("Push Link") }
            },
            dismissButton = {
                TextButton(onClick = { showAddStackDialog = false }) { Text("Cancel", color = NexusTextSecondary) }
            }
        )
    }
}

@Composable
private fun LpQuickChip(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun BoardZoneSlot(
    zone: BoardZone,
    isDeployTarget: Boolean,
    isInspecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val placed = zone.cardInstance
    val isTappedOrDef = placed?.orientation == CardOrientation.DEFENSE_TAPPED
    val borderColor = when {
        isInspecting -> NexusGold
        isDeployTarget -> NexusCyan
        isTappedOrDef -> NexusGold.copy(alpha = 0.75f)
        placed != null -> NexusCyan.copy(alpha = 0.7f)
        else -> NexusBorder
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (placed != null) NexusCardElevated else NexusBackground.copy(alpha = 0.7f),
        border = BorderStroke(if (isInspecting || isDeployTarget) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("zone_${zone.zoneId}")
    ) {
        if (placed == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = zone.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDeployTarget) NexusCyan else NexusTextMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isTappedOrDef) "DEF/TAP" else "ATK/RDY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTappedOrDef) NexusGold else NexusCyan,
                        maxLines = 1
                    )
                    if (placed.counters > 0) {
                        Text(
                            text = "+${placed.counters}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusViolet
                        )
                    }
                }
                Text(
                    text = placed.card.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = NexusTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val statPreview = placed.card.attributes["ATK"]?.let { "ATK $it" }
                    ?: placed.card.attributes["Power/Toughness"]
                    ?: placed.card.attributes["Power"]?.let { "$it/${placed.card.attributes["Toughness"] ?: "?"}" }
                    ?: placed.card.attributes["Mana Cost"]
                    ?: zone.label
                Text(
                    text = statPreview,
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HandCardTile(
    instance: BoardCardInstance,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) NexusCyan.copy(alpha = 0.16f) else NexusSurface,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) NexusCyan else NexusBorder),
        modifier = Modifier
            .width(126.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("hand_card_${instance.card.id}")
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NexusArtworkImage(
                imageUrl = instance.card.imageUrl,
                contentDescription = instance.card.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Text(
                text = instance.card.name,
                style = MaterialTheme.typography.labelMedium,
                color = NexusTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = instance.card.attributes["ATK"]?.let { "ATK $it" }
                    ?: instance.card.attributes["Mana Cost"]
                    ?: instance.card.ruleset,
                style = MaterialTheme.typography.labelSmall,
                color = NexusCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
