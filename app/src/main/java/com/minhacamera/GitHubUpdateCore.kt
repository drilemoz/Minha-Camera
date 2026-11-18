package com.minhacamera

import org.json.JSONArray

/** Pure release policy: GitHub Releases, including prereleases created by this project's CI. */
internal object GitHubUpdateCore {
    const val REPOSITORY = "drilemoz/Minha-Camera"
    const val RELEASES_URL = "https://github.com/$REPOSITORY/releases"
    const val API_URL = "https://api.github.com/repos/$REPOSITORY/releases?per_page=20"
    private val versionPattern = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")
    private val shaPattern = Regex("^sha256:([a-fA-F0-9]{64})$")
    const val MAX_APK_BYTES = 200L * 1024L * 1024L

    /** A resposta 206 precisa corresponder ao byte solicitado e ao tamanho da Release. */
    fun matchingContentRange(header: String?, start: Long, total: Long): Boolean {
        if (start <= 0L || total <= 0L) return false
        val match = Regex("^bytes (\\d+)-(\\d+)/(\\d+)$").matchEntire(header.orEmpty()) ?: return false
        val first = match.groupValues[1].toLongOrNull() ?: return false
        val last = match.groupValues[2].toLongOrNull() ?: return false
        val reportedTotal = match.groupValues[3].toLongOrNull() ?: return false
        return first == start && last == total - 1 && reportedTotal == total
    }

    fun versionParts(tag: String): List<Int>? {
        val match = versionPattern.matchEntire(tag.trim()) ?: return null
        return (1..3).map { match.groupValues[it].toIntOrNull() ?: return null }
    }

    fun newer(candidate: String, installed: String): Boolean {
        val a = versionParts(candidate) ?: return false
        val b = versionParts(installed) ?: return false
        for (i in 0..2) {
            if (a[i] != b[i]) return a[i] > b[i]
        }
        return false
    }

    fun parseReleases(payload: String, installed: String): GitHubUpdateRelease? {
        val entries = JSONArray(payload)
        var chosen: GitHubUpdateRelease? = null
        val availableNotes = mutableMapOf<String, GitHubReleaseNote>()
        for (i in 0 until entries.length().coerceAtMost(100)) {
            val entry = entries.optJSONObject(i) ?: continue
            if (entry.optBoolean("draft", false)) continue
            val tag = entry.optString("tag_name")
            if (!versionPattern.matches(tag)) continue
            if (!newer(tag, installed)) continue
            val version = tag.removePrefix("v")
            val name = "MinhaCamera-$version-universal.apk"
            val expectedUrl = "$RELEASES_URL/download/$tag/$name"
            val assets = entry.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val url = asset.optString("browser_download_url")
                if (asset.optString("name") != name || url != expectedUrl) continue
                val size = asset.optLong("size", -1L)
                if (size <= 0L || size > MAX_APK_BYTES || asset.optString("state") != "uploaded") continue
                val hash = shaPattern.matchEntire(asset.optString("digest"))?.groupValues?.get(1)?.lowercase()
                val releaseUrl = "$RELEASES_URL/tag/$tag"
                val release = GitHubUpdateRelease(version, name, url, releaseUrl,
                    entry.optString("body").take(1800), size, hash)
                if (release.notes.isNotBlank()) availableNotes.putIfAbsent(version,
                    GitHubReleaseNote(version, release.notes))
                if (chosen == null || newer(release.version, chosen.version)) chosen = release
            }
        }
        val latest = chosen ?: return null
        val previous = availableNotes.values.filter { newer(latest.version, it.version) }
            .sortedWith(Comparator { a, b -> when {
                newer(a.version, b.version) -> -1
                newer(b.version, a.version) -> 1
                else -> 0
            } }).take(8)
        return latest.copy(previousNotes = previous)
    }

    fun displayNotes(raw: String): String = raw.lineSequence().map { line ->
        line.trim().removePrefix("#").trimStart('#', ' ').replace("**", "").replace("`", "")
    }.filter { it.isNotBlank() }.take(14).joinToString("\n")
}

internal data class GitHubReleaseNote(val version: String, val notes: String)

internal data class GitHubUpdateRelease(
    val version: String,
    val apkName: String,
    val apkUrl: String,
    val releaseUrl: String,
    val notes: String,
    val size: Long,
    val sha256: String?,
    val previousNotes: List<GitHubReleaseNote> = emptyList()
)
