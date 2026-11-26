package com.example.data.network

import android.util.Log
import com.example.data.model.AppEntry
import com.example.data.model.GitHubReleaseAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SatelliteRepoSnapshot(
    val repoName: String,
    val defaultBranch: String,
    val htmlUrl: String,
    val description: String,
    val updatedAt: String,
    val manifestJson: String?,
    val readmeSnippet: String?
)

/**
 * Auto-discovers Summer-Warrior satellite repositories using GitHub REST API
 * with header "X-GitHub-Api-Version: 2026-03-10", reads root nexus-app.json manifests,
 * and resolves /releases/latest binary assets for the 3-case AppLaunchRouter.
 */
class GitHubStoreService {

    companion object {
        private const val TAG = "GitHubStoreService"
        private const val API_VERSION_HEADER = "X-GitHub-Api-Version"
        private const val API_VERSION_VALUE = "2026-03-10"
        private const val SUMMER_WARRIOR_REPOS_URL =
            "https://api.github.com/users/Summer-Warrior/repos?sort=updated&per_page=100"

        val KNOWN_ECOSYSTEM_REPOS = listOf(
            "MPV-TCG-Card-Making-Software-Updated",
            "Tcg-game-engine-test",
            "Lore-Keeping-app",
            "Asset-Holding-Software",
            "Test-engine",
            "Test-Engine-1",
            "Test-Engine-2",
            "MPV-ROM-Maker",
            "MPV-Non-RP-store",
            "MPV-Animation-Making-Software",
            "MPV-Notebook-Source-Auditor"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .build()

    private fun gitHubRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header(API_VERSION_HEADER, API_VERSION_VALUE)
        .header("User-Agent", "Project-MPV-RP-Master-Hub-Android")
        .get()
        .build()

    suspend fun discoverSatelliteApps(): Result<List<AppEntry>> = withContext(Dispatchers.IO) {
        try {
            val reposBody = client.newCall(gitHubRequest(SUMMER_WARRIOR_REPOS_URL)).execute().use { res ->
                if (!res.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("GitHub API returned HTTP ${res.code} while discovering repos.")
                    )
                }
                res.body?.string().orEmpty()
            }
            val reposArr = JSONArray(reposBody)
            val discovered = mutableListOf<AppEntry>()

            for (i in 0 until reposArr.length()) {
                val repo = reposArr.optJSONObject(i) ?: continue
                val repoName = repo.optString("name")
                if (repoName.isBlank()) continue
                val defaultBranch = repo.optString("default_branch", "main").ifBlank { "main" }
                val htmlUrl = repo.optString("html_url", "https://github.com/Summer-Warrior/$repoName")
                val repoDesc = repo.optString("description", "")

                val manifestRaw = fetchRawFile("Summer-Warrior", repoName, defaultBranch, "nexus-app.json")
                if (manifestRaw != null) {
                    parseManifestApp(manifestRaw, repoName, htmlUrl, repoDesc)?.let { discovered.add(it) }
                }
            }
            Result.success(discovered)
        } catch (e: Exception) {
            Log.w(TAG, "GitHub satellite auto-discovery failed", e)
            Result.failure(IllegalStateException("GitHub sync failed: ${e.message ?: "Network error"}"))
        }
    }

    suspend fun fetchLatestReleaseAsset(
        repoOwner: String,
        repoName: String,
        preferredExtensions: List<String> = listOf(".apk")
    ): Result<GitHubReleaseAsset?> = withContext(Dispatchers.IO) {
        if (repoOwner.isBlank() || repoName.isBlank()) {
            return@withContext Result.success(null)
        }
        val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        try {
            client.newCall(gitHubRequest(url)).execute().use { res ->
                if (res.code == 404) {
                    return@withContext Result.success(null)
                }
                if (!res.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Release lookup HTTP ${res.code} for $repoOwner/$repoName.")
                    )
                }
                val body = res.body?.string().orEmpty()
                val obj = JSONObject(body)
                val tag = obj.optString("tag_name", "latest")
                val releaseName = obj.optString("name", tag)
                val htmlUrl = obj.optString("html_url", "https://github.com/$repoOwner/$repoName/releases")
                val publishedAt = obj.optString("published_at", "")
                val assets = obj.optJSONArray("assets") ?: JSONArray()

                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val fileName = asset.optString("name")
                    val matchesExt = preferredExtensions.any { ext ->
                        fileName.endsWith(ext, ignoreCase = true)
                    }
                    if (matchesExt) {
                        val downloadUrl = asset.optString("browser_download_url")
                        if (downloadUrl.startsWith("https://")) {
                            return@withContext Result.success(
                                GitHubReleaseAsset(
                                    tagName = tag,
                                    releaseName = releaseName,
                                    assetFileName = fileName,
                                    assetSizeBytes = asset.optLong("size", 0L),
                                    downloadUrl = downloadUrl,
                                    releasePageUrl = htmlUrl,
                                    publishedAt = publishedAt
                                )
                            )
                        }
                    }
                }
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching latest release for $repoOwner/$repoName", e)
            Result.failure(IllegalStateException("Release lookup failed: ${e.message}"))
        }
    }

    /**
     * Fetches live snapshots from the 8 Summer-Warrior repos for the Owner/Admin Source Auditor.
     */
    suspend fun fetchEcosystemAuditSnapshots(): Result<List<SatelliteRepoSnapshot>> = withContext(Dispatchers.IO) {
        val snapshots = mutableListOf<SatelliteRepoSnapshot>()
        try {
            for (repoName in KNOWN_ECOSYSTEM_REPOS) {
                val repoUrl = "https://api.github.com/repos/Summer-Warrior/$repoName"
                var defaultBranch = "main"
                var htmlUrl = "https://github.com/Summer-Warrior/$repoName"
                var desc = ""
                var updatedAt = ""

                try {
                    client.newCall(gitHubRequest(repoUrl)).execute().use { res ->
                        if (res.isSuccessful) {
                            val obj = JSONObject(res.body?.string().orEmpty())
                            defaultBranch = obj.optString("default_branch", "main").ifBlank { "main" }
                            htmlUrl = obj.optString("html_url", htmlUrl)
                            desc = obj.optString("description", "")
                            updatedAt = obj.optString("updated_at", "")
                        }
                    }
                } catch (repoErr: Exception) {
                    Log.w(TAG, "Repo metadata fetch failed for $repoName", repoErr)
                }

                val manifest = fetchRawFile("Summer-Warrior", repoName, defaultBranch, "nexus-app.json")
                val readme = fetchRawFile("Summer-Warrior", repoName, defaultBranch, "README.md")?.take(1500)
                snapshots.add(
                    SatelliteRepoSnapshot(
                        repoName = repoName,
                        defaultBranch = defaultBranch,
                        htmlUrl = htmlUrl,
                        description = desc,
                        updatedAt = updatedAt,
                        manifestJson = manifest,
                        readmeSnippet = readme
                    )
                )
            }
            Result.success(snapshots)
        } catch (e: Exception) {
            Log.w(TAG, "Audit snapshot sweep failed", e)
            Result.failure(IllegalStateException("Unable to inspect GitHub repos: ${e.message}"))
        }
    }

    private fun fetchRawFile(owner: String, repo: String, branch: String, path: String): String? {
        val rawUrl = "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
        return try {
            val req = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", "Project-MPV-RP-Master-Hub-Android")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string() else null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Raw file not present: $rawUrl (${e.message})")
            null
        }
    }

    private fun parseManifestApp(
        rawJson: String,
        repoName: String,
        htmlUrl: String,
        fallbackDesc: String
    ): AppEntry? {
        return try {
            val obj = JSONObject(rawJson)
            val id = obj.optString("id").ifBlank { repoName.lowercase() }
            val platformsArr = obj.optJSONArray("platforms")
            val platforms = if (platformsArr != null) {
                List(platformsArr.length()) { platformsArr.optString(it) }.filter { it.isNotBlank() }
            } else listOf("Android", "Web")
            val allowedArr = obj.optJSONArray("allowedTiers")
            val allowed = if (allowedArr != null) {
                List(allowedArr.length()) { allowedArr.optString(it) }.filter { it.isNotBlank() }
            } else emptyList()
            val tagsArr = obj.optJSONArray("tags")
            val tags = if (tagsArr != null) {
                List(tagsArr.length()) { tagsArr.optString(it) }.filter { it.isNotBlank() }
            } else listOf(obj.optString("type", "TCG_TOOL"))

            AppEntry(
                id = id,
                name = obj.optString("name").ifBlank { repoName },
                subtitle = obj.optString("subtitle").ifBlank { "Auto-discovered nexus-app.json" },
                description = obj.optString("description").ifBlank { fallbackDesc.ifBlank { "Satellite app manifest." } },
                type = obj.optString("type", "TCG_TOOL"),
                platforms = platforms,
                repoOwner = "Summer-Warrior",
                repoName = repoName,
                deepLinkScheme = obj.optString("deepLinkScheme"),
                androidPackageName = obj.optString("androidPackageName"),
                webUrl = obj.optString("webUrl").ifBlank { "https://summer-warrior.github.io/$repoName/" },
                externalUrl = obj.optString("externalUrl").ifBlank { htmlUrl },
                launchInApp = false,
                accessTier = obj.optString("accessTier", "PUBLIC"),
                hiddenFromUnauthorized = obj.optBoolean("hiddenFromUnauthorized", false),
                allowedTiers = allowed,
                lockedUpstream = false,
                version = obj.optString("version", "GitHub Sync"),
                tags = tags
            )
        } catch (e: Exception) {
            Log.w(TAG, "Malformed nexus-app.json in $repoName", e)
            null
        }
    }
}
