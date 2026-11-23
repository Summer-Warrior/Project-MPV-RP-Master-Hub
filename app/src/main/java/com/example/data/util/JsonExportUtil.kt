package com.example.data.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.example.data.model.AppEntry
import com.example.data.model.CardEntry
import com.example.data.model.CardPackEntry
import com.example.data.model.DeckEntry
import com.example.data.model.DiscordResource
import com.example.data.model.RpCharacter
import com.example.data.model.WikiArticle
import org.json.JSONArray
import org.json.JSONObject

/**
 * Solo Mobile Developer Workflow exporter: formats registry items into clean,
 * GitHub-ready JSON or Markdown and provides one-tap Copy or Android Share export.
 */
object JsonExportUtil {

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareText(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun appToJson(app: AppEntry): String = appToJsonObject(app).toString(2)

    fun appsListToJson(apps: List<AppEntry>): String {
        val arr = JSONArray()
        apps.forEach { arr.put(appToJsonObject(it)) }
        return arr.toString(2)
    }

    private fun appToJsonObject(app: AppEntry): JSONObject = JSONObject().apply {
        put("id", app.id)
        put("name", app.name)
        put("subtitle", app.subtitle)
        put("description", app.description)
        put("type", app.type)
        put("platforms", JSONArray(app.platforms))
        put("externalUrl", app.externalUrl)
        put("launchInApp", app.launchInApp)
        put("version", app.version)
        put("tags", JSONArray(app.tags))
    }

    fun cardToJson(card: CardEntry): String = cardToJsonObject(card).toString(2)

    fun packWithCardsToJson(pack: CardPackEntry, cards: List<CardEntry>): String {
        return JSONObject().apply {
            put("id", pack.id)
            put("name", pack.name)
            put("ruleset", pack.ruleset)
            put("version", pack.version)
            put("description", pack.description)
            put("sourceUrl", pack.sourceUrl)
            val cardsArr = JSONArray()
            cards.forEach { cardsArr.put(cardToJsonObject(it)) }
            put("cards", cardsArr)
        }.toString(2)
    }

    fun deckToJson(deck: DeckEntry): String = JSONObject().apply {
        put("id", deck.id)
        put("name", deck.name)
        put("ruleset", deck.ruleset)
        put("description", deck.description)
        put("cardIds", JSONArray(deck.cardIds))
    }.toString(2)

    private fun cardToJsonObject(card: CardEntry): JSONObject = JSONObject().apply {
        put("id", card.id)
        put("codeString", card.codeString)
        put("name", card.name)
        put("ruleset", card.ruleset)
        put("packId", card.packId)
        put("imageUrl", card.imageUrl)
        put("loreLink", card.loreLink)
        put("abilityText", card.abilityText)
        val attrs = JSONObject()
        card.attributes.forEach { (k, v) -> attrs.put(k, v) }
        put("attributes", attrs)
    }

    fun wikiArticleToJson(article: WikiArticle): String = JSONObject().apply {
        put("id", article.id)
        put("title", article.title)
        put("category", article.category)
        put("summary", article.summary)
        put("markdownContent", article.markdownContent)
        put("linkedCardIds", JSONArray(article.linkedCardIds))
        put("linkedCharacterIds", JSONArray(article.linkedCharacterIds))
        put("updatedAt", article.updatedAt)
    }.toString(2)

    fun wikiArticleToMarkdown(article: WikiArticle): String = buildString {
        appendLine("---")
        appendLine("id: ${article.id}")
        appendLine("title: \"${article.title}\"")
        appendLine("category: \"${article.category}\"")
        appendLine("updatedAt: \"${article.updatedAt}\"")
        if (article.linkedCardIds.isNotEmpty()) {
            appendLine("linkedCards: [${article.linkedCardIds.joinToString(", ")}]")
        }
        appendLine("---")
        appendLine()
        append(article.markdownContent)
    }

    fun characterToJson(character: RpCharacter): String = JSONObject().apply {
        put("id", character.id)
        put("name", character.name)
        put("title", character.title)
        put("avatarUrl", character.avatarUrl)
        put("faction", character.faction)
        put("catchphrase", character.catchphrase)
        put("summonChant", character.summonChant)
        put("linkedDeckId", character.linkedDeckId)
        put("currentLp", character.currentLp)
        put("bio", character.bio)
    }.toString(2)

    fun discordResourceToJson(res: DiscordResource): String = JSONObject().apply {
        put("id", res.id)
        put("name", res.name)
        put("category", res.category)
        put("description", res.description)
        put("inviteUrl", res.inviteUrl)
        put("tags", JSONArray(res.tags))
    }.toString(2)
}
