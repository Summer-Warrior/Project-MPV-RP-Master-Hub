package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.util.JsonExportUtil
import com.example.ui.theme.NexusBorder
import com.example.ui.theme.NexusCard
import com.example.ui.theme.NexusCyan
import com.example.ui.theme.NexusGold
import com.example.ui.theme.NexusSurface
import com.example.ui.theme.NexusTextMuted
import com.example.ui.theme.NexusTextPrimary
import com.example.ui.theme.NexusTextSecondary
import com.example.ui.theme.NexusViolet

/**
 * Opens only safe https:// external URLs via Android browser Intent.
 */
fun openSafeExternalUrl(context: Context, rawUrl: String, onError: (String) -> Unit = {}) {
    val trimmed = rawUrl.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true)) {
        onError("Blocked non-HTTPS link.")
        return
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        onError("No browser available to open link.")
    }
}

@Composable
fun NexusArtworkImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val localDrawable = when (imageUrl.trim()) {
        "local://img_card_art_nexus" -> R.drawable.img_card_art_nexus_1795407675768
        "local://img_hub_banner" -> R.drawable.img_hub_banner_1795407674042
        "local://img_avatar_duelist" -> R.drawable.img_avatar_duelist_1795407675121
        else -> null
    }

    when {
        localDrawable != null -> {
            Image(
                painter = painterResource(id = localDrawable),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
        imageUrl.trim().startsWith("https://", ignoreCase = true) -> {
            AsyncImage(
                model = imageUrl.trim(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                placeholder = painterResource(id = R.drawable.img_hub_banner_1795407674042),
                error = painterResource(id = R.drawable.img_card_art_nexus_1795407675768)
            )
        }
        else -> {
            Box(
                modifier = modifier.background(
                    Brush.linearGradient(
                        listOf(Color(0xFF142138), Color(0xFF1D1633), Color(0xFF0E1A2C))
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = contentDescription,
                    tint = NexusCyan.copy(alpha = 0.65f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun NexusPillBadge(
    text: String,
    accentColor: Color = NexusCyan,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun NexusSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = NexusTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NexusTextSecondary
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun JsonExportActionRow(
    jsonPayload: String,
    exportTitle: String,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier,
    markdownPayload: String? = null
) {
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = {
                JsonExportUtil.copyToClipboard(context, exportTitle, jsonPayload)
                onFeedback("Copied $exportTitle JSON for GitHub paste.")
            },
            modifier = Modifier
                .weight(1f)
                .testTag("copy_json_button"),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            border = BorderStroke(1.dp, NexusCyan.copy(alpha = 0.55f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusCyan)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy JSON", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy JSON", maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }

        OutlinedButton(
            onClick = {
                JsonExportUtil.shareText(context, "$exportTitle.json", jsonPayload)
            },
            modifier = Modifier
                .weight(1f)
                .testTag("share_json_button"),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            border = BorderStroke(1.dp, NexusGold.copy(alpha = 0.55f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusGold)
        ) {
            Icon(Icons.Default.Share, contentDescription = "Export JSON", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export JSON", maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }

        if (markdownPayload != null) {
            OutlinedButton(
                onClick = {
                    JsonExportUtil.copyToClipboard(context, "$exportTitle.md", markdownPayload)
                    onFeedback("Copied Markdown for GitHub repo.")
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("copy_md_button"),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                border = BorderStroke(1.dp, NexusViolet.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusViolet)
            ) {
                Icon(Icons.Default.Description, contentDescription = "Copy Markdown", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy MD", maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Safe native Compose Markdown renderer for Wiki Lore pages (no raw HTML/WebView injection).
 */
@Composable
fun MarkdownLiteRenderer(
    markdown: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        markdown.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> Spacer(modifier = Modifier.height(2.dp))
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# "),
                        style = MaterialTheme.typography.headlineSmall,
                        color = NexusCyan
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge,
                        color = NexusGold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium,
                        color = NexusViolet,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                line.startsWith("> ") -> {
                    Surface(
                        color = NexusSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, NexusViolet.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formatInlineBold(line.removePrefix("> ")),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = NexusViolet,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "•",
                            color = NexusCyan,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = formatInlineBold(line.drop(2)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NexusTextPrimary
                        )
                    }
                }
                else -> {
                    Text(
                        text = formatInlineBold(line),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexusTextPrimary
                    )
                }
            }
        }
    }
}

private fun formatInlineBold(text: String) = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = NexusTextPrimary)) {
                append(part)
            }
        } else {
            append(part)
        }
    }
}

@Composable
fun NexusEmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NexusCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, NexusBorder)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = NexusCyan.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = NexusTextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = NexusTextMuted)
        }
    }
}
