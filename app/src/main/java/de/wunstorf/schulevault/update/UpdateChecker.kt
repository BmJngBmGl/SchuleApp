package de.wunstorf.schulevault.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String
)

/**
 * Fragt den neuesten GitHub Release des App-Repos ab. Das Repo ist oeffentlich,
 * daher ohne Auth-Token abrufbar - ein Token in der APK waere aus ihr
 * extrahierbar und damit ein Sicherheitsrisiko.
 */
object UpdateChecker {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/BmJngBmGl/SchuleApp/releases/latest"

    /**
     * Liefert null bei jedem Fehler (offline, Rate-Limit, noch kein Release
     * vorhanden) - der Check darf den App-Start nie stoeren oder crashen.
     */
    suspend fun neuesteVersionPruefen(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.inputStream.use { input ->
                val json = JSONObject(input.bufferedReader(Charsets.UTF_8).readText())
                val versionName = json.getString("tag_name").removePrefix("v")
                val assets = json.getJSONArray("assets")
                val apkAsset = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                    ?: return@withContext null
                UpdateInfo(versionName = versionName, downloadUrl = apkAsset.getString("browser_download_url"))
            }
        } catch (e: Exception) {
            null
        }
    }
}
