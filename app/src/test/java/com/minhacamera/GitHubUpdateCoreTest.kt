package com.minhacamera

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCoreTest {
    private val hash = "ab".repeat(32)
    private fun release(
        version: String, draft: Boolean = false, source: String = GitHubUpdateCore.RELEASES_URL,
        state: String = "uploaded", digest: String = "sha256:$hash",
        size: Long = 9000, file: String = "MinhaCamera-$version-universal.apk"
    ): JSONObject {
        val asset = JSONObject().put("name", file).put("size", size)
            .put("state", state).put("digest", digest)
            .put("browser_download_url", "$source/download/v$version/$file")
        return JSONObject().put("tag_name", "v$version").put("draft", draft)
            .put("body", "# Mudanças $version").put("assets", JSONArray().put(asset))
    }

    private fun parse(vararg entries: JSONObject): GitHubUpdateRelease? {
        val list = JSONArray()
        entries.forEach { list.put(it) }
        return GitHubUpdateCore.parseReleases(list.toString(), "1.4.81")
    }

    @Test fun upToDateAndUntrustedAssetsExcluded() {
        assertNull(parse(release("1.4.81"), release("1.4.80"), release("1.4.82", draft = true),
            release("1.4.83", source = "https://github.com/another/other/releases"),
            release("1.4.84", file = "MinhaCamera-1.4.84-arm64-v8a.apk"),
            release("1.4.85", state = "new"), release("1.4.86", size = GitHubUpdateCore.MAX_APK_BYTES + 1)))
    }

    @Test fun comparesNumericVersionsAndIncludesIntermediateNotes() {
        val update = parse(release("1.4.99"), release("1.4.100"), release("1.4.90"))!!
        assertEquals("1.4.100", update.version)
        assertEquals(hash, update.sha256)
        assertEquals(listOf("1.4.99", "1.4.90"), update.previousNotes.map { it.version })
        assertEquals("Mudanças 1.4.100", GitHubUpdateCore.displayNotes(update.notes))
    }

    @Test fun missingOrBadHashCannotEnableAutoDownload() {
        assertNull(parse(release("1.4.82", digest = "unknown"))!!.sha256)
    }

    @Test fun verifiesCompleteRangeBeforeAppend() {
        assertTrue(GitHubUpdateCore.matchingContentRange("bytes 100-499/500", 100, 500))
        assertFalse(GitHubUpdateCore.matchingContentRange("bytes 100-400/500", 100, 500))
        assertFalse(GitHubUpdateCore.matchingContentRange("bytes 200-499/500", 100, 500))
        assertFalse(GitHubUpdateCore.matchingContentRange("bytes 100-500/501", 100, 500))
    }
}
