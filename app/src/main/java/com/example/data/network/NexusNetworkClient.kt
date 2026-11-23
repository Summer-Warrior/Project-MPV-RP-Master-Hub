package com.example.data.network

import com.example.data.model.CardEntry
import com.example.data.model.CardPackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ImportedPackResult(
    val pack: CardPackEntry,
    val cards: List<CardEntry>
)

class NexusNetworkClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Validates that a user-supplied URL uses HTTPS and points to a public host
     * (blocks local/private network addresses and non-HTTPS schemes).
     */
    fun validateSafeExternalHttpsUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim()
        val parsed = trimmed.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Enter a valid URL starting with https://"))
        if (parsed.scheme != "https") {
            return Result.failure(IllegalArgumentException("Only secure https:// URLs are allowed."))
        }
        val host = parsed.host.lowercase()
        if (host == "localhost" || host.endsWith(".local") || host == "169.254.169.254") {
            return Result.failure(IllegalArgumentException("Local or link-local hosts are not permitted."))
        }
        try {
            val addr = InetAddress.getByName(host)
            if (addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress) {
                return Result.failure(IllegalArgumentException("Private network addresses are not permitted."))
            }
        } catch (_: Exception) {
            // Allow DNS resolution during actual OkHttp call if offline or DNS delayed
        }
        return Result.success(trimmed)
    }

    /**
     * Strictly validates official Discord Webhook URLs. Rejects bot tokens or non-Discord hosts.
     */
    fun validateDiscordWebhookUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim()
        val parsed = trimmed.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Enter a valid Discord Webhook URL."))
        val allowedHosts = setOf(
            "discord.com",
            "discordapp.com",
            "ptb.discord.com",
            "canary.discord.com"
        )
        if (parsed.scheme != "https" || parsed.host.lowercase() !in allowedHosts) {
            return Result.failure(IllegalArgumentException("Webhook URL must use https://discord.com/api/webhooks/..."))
        }
        if (!parsed.encodedPath.startsWith("/api/webhooks/")) {
            return Result.failure(IllegalArgumentException("URL path must start with /api/webhooks/."))
        }
        return Result.success(trimmed)
    }

    suspend fun fetchFederatedCardPack(rawUrl: String): Result<ImportedPackResult> = withContext(Dispatchers.IO) {
        val validated = validateSafeExternalHttpsUrl(rawUrl).getOrElse { return@withContext Result.failure(it) }
        try {
            val request = Request.Builder()
                .url(validated)
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP ${response.code}: Unable to fetch card pack JSON.")
                    )
                }
                val body = response.body?.string()?.trim()
                    ?: return@withContext Result.failure(IllegalStateException("Empty response body."))
                if (body.length > 5_000_000) {
                    return@withContext Result.failure(IllegalStateException("Card pack exceeds 5MB safety limit."))
                }
                parseFederatedPackPayload(body, validated)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Import failed: ${e.message ?: "Network error"}"))
        }
    }

    fun parseFederatedPackPayload(jsonText: String, sourceUrl: String): Result<ImportedPackResult> {
        return try {
            val trimmed = jsonText.trim()
            val packId = "pack-${UUID.randomUUID().toString().take(8)}"
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                val cards = parseCardsArray(arr, packId, "Custom")
                val pack = CardPackEntry(
                    id = packId,
                    name = "Imported Pack (${cards.size} cards)",
                    ruleset = cards.firstOrNull()?.ruleset ?: "Hybrid Crossover",
                    version = "1.0.0",
                    description = "Imported from $sourceUrl",
                    sourceUrl = sourceUrl,
                    isBuiltIn = false
                )
                Result.success(ImportedPackResult(pack, cards))
            } else {
                val root = JSONObject(trimmed)
                val resolvedPackId = root.optString("id").ifBlank { packId }
                val ruleset = root.optString("ruleset").ifBlank { "Hybrid Crossover" }
                val cardsArr = root.optJSONArray("cards") ?: JSONArray()
                val cards = parseCardsArray(cardsArr, resolvedPackId, ruleset)
                val pack = CardPackEntry(
                    id = resolvedPackId,
                    name = root.optString("name").ifBlank { "Imported Federated Pack" },
                    ruleset = ruleset,
                    version = root.optString("version").ifBlank { "1.0.0" },
                    description = root.optString("description").ifBlank { "Federated pack imported from URL." },
                    sourceUrl = sourceUrl,
                    isBuiltIn = false
                )
                Result.success(ImportedPackResult(pack, cards))
            }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid JSON card pack format: ${e.message}"))
        }
    }

    private fun parseCardsArray(arr: JSONArray, defaultPackId: String, defaultRuleset: String): List<CardEntry> {
        val list = mutableListOf<CardEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id").ifBlank { "card-${UUID.randomUUID().toString().take(8)}" }
            val codeString = obj.optString("codeString")
                .ifBlank { obj.optString("code") }
                .ifBlank { id }
            val attrsObj = obj.optJSONObject("attributes")
            val attrs = linkedMapOf<String, String>()
            if (attrsObj != null) {
                val keys = attrsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    attrs[key] = attrsObj.optString(key)
                }
            }
            list.add(
                CardEntry(
                    id = id,
                    codeString = codeString,
                    name = obj.optString("name").ifBlank { "Unnamed Card #$i" },
                    ruleset = obj.optString("ruleset").ifBlank { defaultRuleset },
                    packId = obj.optString("packId").ifBlank { defaultPackId },
                    imageUrl = obj.optString("imageUrl"),
                    loreLink = obj.optString("loreLink"),
                    abilityText = obj.optString("abilityText"),
                    attributes = attrs,
                    isUserCreated = true
                )
            )
        }
        return list
    }

    suspend fun sendDiscordWebhookEmbed(
        rawWebhookUrl: String,
        username: String,
        avatarUrl: String,
        title: String,
        description: String,
        footerText: String,
        colorDecimal: Int = 0x00E5FF,
        fields: List<Pair<String, String>> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val validated = validateDiscordWebhookUrl(rawWebhookUrl).getOrElse { return@withContext Result.failure(it) }
        try {
            val embedFields = JSONArray()
            fields.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) {
                    embedFields.put(
                        JSONObject().apply {
                            put("name", name.take(256))
                            put("value", value.take(1024))
                            put("inline", true)
                        }
                    )
                }
            }
            val embed = JSONObject().apply {
                put("title", title.take(256))
                put("description", description.take(4000))
                put("color", colorDecimal)
                if (embedFields.length() > 0) put("fields", embedFields)
                put("footer", JSONObject().put("text", footerText.take(2048)))
            }
            val payload = JSONObject().apply {
                if (username.isNotBlank()) put("username", username.take(80))
                if (avatarUrl.startsWith("https://")) put("avatar_url", avatarUrl)
                put("embeds", JSONArray().put(embed))
            }

            val request = Request.Builder()
                .url(validated)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("Broadcast delivered to Discord (HTTP ${response.code}).")
                } else {
                    Result.failure(IllegalStateException("Discord returned HTTP ${response.code}. Check your webhook URL."))
                }
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Broadcast failed: ${e.message ?: "Network error"}"))
        }
    }
}
