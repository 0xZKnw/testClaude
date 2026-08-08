package com.zknw.unoduo.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pulls the APK published by the repository's build workflow and hands it to the
 * system installer. Everything here is plain HttpURLConnection on purpose: one more
 * HTTP library would dwarf the few requests this actually makes.
 */
object Updater {

    const val OWNER = "0xZKnw"
    const val REPO = "testClaude"
    const val TAG = "apk-latest"
    private const val ASSET_NAME = "uno-duo.apk"

    private const val RELEASE_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/tags/$TAG"

    /** What the release says is available, versus what is installed right now. */
    data class Available(
        val commit: String,
        val downloadUrl: String,
        val needsToken: Boolean,
        val sizeBytes: Long,
        val publishedAt: String
    )

    sealed interface Outcome {
        data class UpToDate(val commit: String) : Outcome
        data class Ready(val release: Available) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    suspend fun check(installedCommit: String, token: String?): Outcome = withContext(Dispatchers.IO) {
        try {
            val body = get(RELEASE_URL, token, accept = "application/vnd.github+json")
                ?: return@withContext Outcome.Failed(
                    "Impossible de joindre GitHub. Vérifie ta connexion — et si le dépôt " +
                        "redevient privé, ajoute un jeton dans les réglages."
                )
            val json = JSONObject(body)
            val notes = json.optString("body", "")
            val commit = parseCommit(notes)
                ?: return@withContext Outcome.Failed("La release ne dit pas de quel commit elle vient.")

            val assets = json.optJSONArray("assets")
                ?: return@withContext Outcome.Failed("Aucun fichier attaché à la release.")
            var apiUrl: String? = null
            var browserUrl: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name") == ASSET_NAME) {
                    apiUrl = asset.optString("url").takeIf { it.isNotEmpty() }
                    browserUrl = asset.optString("browser_download_url").takeIf { it.isNotEmpty() }
                    size = asset.optLong("size")
                }
            }
            if (apiUrl == null && browserUrl == null) {
                return@withContext Outcome.Failed("L'APK n'est pas dans la release.")
            }

            if (commit.equals(installedCommit, ignoreCase = true)) {
                return@withContext Outcome.UpToDate(commit)
            }
            // A private repository only serves the binary through the API, with a token.
            val useApi = token != null && apiUrl != null
            Outcome.Ready(
                Available(
                    commit = commit,
                    downloadUrl = if (useApi) apiUrl!! else (browserUrl ?: apiUrl!!),
                    needsToken = useApi,
                    sizeBytes = size,
                    publishedAt = json.optString("published_at", "")
                )
            )
        } catch (e: Exception) {
            Outcome.Failed(e.message ?: "Échec de la vérification")
        }
    }

    /** The workflow writes `sha=<short sha>` into the notes; that is the contract. */
    internal fun parseCommit(notes: String): String? =
        Regex("""sha=([0-9a-fA-F]{7,40})""").find(notes)?.groupValues?.get(1)

    suspend fun download(
        release: Available,
        token: String?,
        context: Context,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, ASSET_NAME)
            if (target.exists()) target.delete()

            val connection = open(
                release.downloadUrl,
                token.takeIf { release.needsToken },
                accept = if (release.needsToken) "application/octet-stream" else "*/*"
            )
            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException("Téléchargement refusé (${connection.responseCode})")
                )
            }
            val total = if (release.sizeBytes > 0) release.sizeBytes else connection.contentLength.toLong()
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            connection.disconnect()
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Sends the user to the "install unknown apps" toggle for this app. */
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))

    private fun get(url: String, token: String?, accept: String): String? {
        val connection = open(url, token, accept)
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, token: String?, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "UnoDuo-Updater")
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer ${token.trim()}")
            }
        }
}
