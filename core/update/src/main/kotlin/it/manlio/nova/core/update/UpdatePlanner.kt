package it.manlio.nova.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** Release di GitHub, solo i campi che servono. */
@Serializable
data class Release(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String? = null,
    val assets: List<Asset> = emptyList(),
)

@Serializable
data class Asset(
    val id: Long,
    val name: String,
    val size: Long = 0,
)

/** Un aggiornamento dell'app: APK firmato e file con la sua impronta SHA-256. */
data class AppUpdate(
    val versionCode: Int,
    val tag: String,
    val apk: Asset,
    val checksum: Asset,
    val notes: String?,
    val title: String? = null,
    /** Data di pubblicazione ISO-8601 (es. 2026-10-30T22:15:00Z). */
    val publishedAt: String? = null,
)

/** Un aggiornamento della sola configurazione (si applica a caldo, senza reinstallare). */
data class ConfigUpdate(
    val version: String,
    val tag: String,
    val bundle: Asset,
    val checksum: Asset,
)

/**
 * Una versione precedente a cui si può tornare. [standby] è l'APK già pronto (stesso codice di
 * [update], numero di versione più alto) allegato alla release installata: c'è solo per la versione
 * immediatamente precedente e rende il ritorno immediato, senza aspettare una nuova build.
 */
data class PreviousVersion(
    val update: AppUpdate,
    val standby: AppUpdate? = null,
)

data class UpdatePlan(
    val app: AppUpdate?,
    val config: ConfigUpdate?,
) {
    val isEmpty get() = app == null && config == null
}

/**
 * Contratto delle release (vedi docs/AGGIORNAMENTI.md):
 * - app: tag `vX.Y.Z`, asset `nova-<versionCode>.apk` e `nova-<versionCode>.apk.sha256`;
 * - configurazione: tag `config-vX.Y.Z`, asset `nova-config.json` e `nova-config.json.sha256`.
 * Bozze e pre-release sono ignorate; senza file di impronta un asset non viene mai usato.
 */
object UpdatePlanner {
    private val APK = Regex("^nova-(\\d+)\\.apk$")
    private val KEYBOARD_APK = Regex("^nova-tastiera-(\\d+)\\.apk$")
    private val STANDBY = Regex("^nova-ripristino-(\\d+)-(v\\d+\\.\\d+\\.\\d+)\\.apk$")
    private val CONFIG_TAG = Regex("^config-v(\\d+\\.\\d+\\.\\d+)$")
    const val CONFIG_ASSET = "nova-config.json"
    const val KEYBOARD_PACKAGE = "it.manlio.nova.keyboard"

    /** Il numero di versione della tastiera è questo più quello di NOVA (`NOVA_VERSION_BASE` in `keyboard/`). */
    const val KEYBOARD_VERSION_BASE = 100_000

    fun plan(
        releases: List<Release>,
        installedVersionCode: Int,
        installedConfigVersion: String,
    ): UpdatePlan {
        val published = releases.filter { !it.draft && !it.prerelease }
        val app = appReleases(published).filter { it.versionCode > installedVersionCode }.maxByOrNull { it.versionCode }
        val config =
            published
                .mapNotNull { r ->
                    val version = CONFIG_TAG.find(r.tag)?.groupValues?.get(1) ?: return@mapNotNull null
                    val bundle = r.assets.firstOrNull { it.name == CONFIG_ASSET } ?: return@mapNotNull null
                    val checksum = r.assets.firstOrNull { it.name == "$CONFIG_ASSET.sha256" } ?: return@mapNotNull null
                    ConfigUpdate(version, r.tag, bundle, checksum)
                }.filter { compareVersions(it.version, installedConfigVersion) > 0 }
                .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
        return UpdatePlan(app, config)
    }

    /**
     * La tastiera più recente sopra [installedVersionCode] (`nova-tastiera-<code>.apk` + `.sha256`); [AppUpdate.versionCode] è
     * quello vero della tastiera, cioè il numero nel nome più [KEYBOARD_VERSION_BASE].
     */
    fun keyboard(
        releases: List<Release>,
        installedVersionCode: Long,
    ): AppUpdate? =
        releases
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { r ->
                val apk = r.assets.firstOrNull { KEYBOARD_APK.matches(it.name) } ?: return@mapNotNull null
                val checksum = r.assets.firstOrNull { it.name == "${apk.name}.sha256" } ?: return@mapNotNull null
                val code = KEYBOARD_APK.find(apk.name)!!.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                AppUpdate(KEYBOARD_VERSION_BASE + code, r.tag, apk, checksum, null)
            }.filter { it.versionCode > installedVersionCode }
            .maxByOrNull { it.versionCode }

    /**
     * Versioni dell'app più vecchie di quella installata, dalla più recente, per il ritorno indietro
     * d'emergenza (SPEC §4.6). Al massimo [limit].
     */
    fun previousVersions(
        releases: List<Release>,
        installedVersionCode: Int,
        limit: Int = 10,
    ): List<PreviousVersion> {
        val published = releases.filter { !it.draft && !it.prerelease }
        val standby = standby(published, installedVersionCode)
        return appReleases(published)
            .filter { it.versionCode < installedVersionCode }
            .sortedByDescending { it.versionCode }
            .take(limit)
            .map { v -> PreviousVersion(v, standby?.takeIf { it.tag == v.tag && it.versionCode > installedVersionCode }) }
    }

    /** APK di ripristino allegato alla release installata (`nova-ripristino-<code>-<tag>.apk` + `.sha256`). */
    private fun standby(
        published: List<Release>,
        installedVersionCode: Int,
    ): AppUpdate? {
        val installed = published.firstOrNull { r -> r.assets.any { it.name == "nova-$installedVersionCode.apk" } } ?: return null
        val apk = installed.assets.firstOrNull { STANDBY.matches(it.name) } ?: return null
        val checksum = installed.assets.firstOrNull { it.name == "${apk.name}.sha256" } ?: return null
        val (code, tag) = STANDBY.find(apk.name)!!.destructured
        return AppUpdate(code.toInt(), tag, apk, checksum, null)
    }

    private fun appReleases(published: List<Release>): List<AppUpdate> =
        published.mapNotNull { r ->
            val apk = r.assets.firstOrNull { APK.matches(it.name) } ?: return@mapNotNull null
            val checksum = r.assets.firstOrNull { it.name == "${apk.name}.sha256" } ?: return@mapNotNull null
            val code = APK.find(apk.name)!!.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            AppUpdate(code, r.tag, apk, checksum, r.body, r.name, r.publishedAt)
        }

    /** Confronto semantico "1.10.0" > "1.9.3". Parti mancanti o non numeriche valgono 0. */
    fun compareVersions(
        a: String,
        b: String,
    ): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}

/** Impronte SHA-256 (SPEC §4.6). */
object Checksums {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sha256(file: java.io.File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Il formato di `sha256sum`: "impronta  nomefile". Restituisce l'impronta in minuscolo, o null. */
    fun parse(text: String): String? = text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()?.takeIf { HEX.matches(it) }

    private val HEX = Regex("^[0-9a-f]{64}$")
    private const val BUFFER = 64 * 1024
}
