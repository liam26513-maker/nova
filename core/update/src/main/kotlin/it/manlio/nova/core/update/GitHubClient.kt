package it.manlio.nova.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Legge le release del repository privato con un token a permessi minimi (SPEC §13.4).
 * Il token viaggia solo verso api.github.com: nei rinvii verso lo storage dei file non viene inviato.
 */
class GitHubClient(
    private val repo: String,
    private val token: () -> String?,
) {
    class HttpException(val code: Int, message: String) : IOException(message)

    suspend fun releases(): List<Release> =
        withContext(Dispatchers.IO) {
            val conn = open("https://api.github.com/repos/$repo/releases?per_page=30", "application/vnd.github+json")
            JSON.decodeFromString<List<Release>>(conn.read())
        }

    /**
     * Avvia un workflow di GitHub Actions (serve il permesso "Actions: Read and write" sul token).
     * Usato solo per il ritorno a una versione precedente: [inputs] sono già validati dal chiamante.
     */
    suspend fun dispatchWorkflow(
        workflow: String,
        ref: String,
        inputs: Map<String, String>,
    ) = withContext(Dispatchers.IO) {
        val body = JSON.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("ref", ref)
            put("inputs", buildJsonObject { inputs.forEach { (k, v) -> put(k, v) } })
        })
        val conn = open("https://api.github.com/repos/$repo/actions/workflows/$workflow/dispatches", "application/vnd.github+json")
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toByteArray()) }
        conn.read()
        Unit
    }

    suspend fun downloadText(asset: Asset): String = withContext(Dispatchers.IO) { fetch(asset).read() }

    suspend fun download(
        asset: Asset,
        target: File,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val conn = fetch(asset)
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: asset.size
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.part")
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buffer = ByteArray(BUFFER)
                var done = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        if (!tmp.renameTo(target)) throw IOException("Impossibile salvare ${target.name}")
    }

    /** Scarica un asset: l'API risponde con un rinvio a un URL firmato, da chiamare senza token. */
    private fun fetch(asset: Asset): HttpURLConnection {
        val api = open("https://api.github.com/repos/$repo/releases/assets/${asset.id}", "application/octet-stream", follow = false)
        if (api.responseCode in REDIRECTS) {
            val location = api.getHeaderField("Location") ?: throw IOException("Rinvio senza destinazione")
            api.disconnect()
            require(location.startsWith("https://")) { "Rinvio non sicuro" }
            return (URL(location).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
        }
        return api
    }

    private fun open(
        url: String,
        accept: String,
        follow: Boolean = true,
    ): HttpURLConnection {
        val t = token() ?: throw HttpException(UNAUTHORIZED, "Manca il token GitHub")
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = follow
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $t")
            setRequestProperty("Accept", accept)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
    }

    private fun HttpURLConnection.read(): String {
        val code = responseCode
        if (code !in OK_RANGE) throw HttpException(code, "GitHub ha risposto $code")
        return inputStream.bufferedReader().use { it.readText() }
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val REDIRECTS = setOf(301, 302, 303, 307, 308)
        val OK_RANGE = 200..299
        const val UNAUTHORIZED = 401
        const val TIMEOUT_MS = 20_000
        const val BUFFER = 64 * 1024
    }
}
