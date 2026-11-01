package it.manlio.nova.core.system

/** Quanti pacchetti ha fermato il Turbo e quanti ha lasciato stare. */
data class TurboResult(val stopped: Int, val failed: Int)

/** Densità dello schermo: quella di fabbrica e quella attuale. */
data class Density(val physical: Int, val current: Int)

/**
 * Gli strumenti di sistema della Fase 2 (SPEC §7), costruiti solo sui comandi consentiti di [SystemCommand]:
 * ogni modifica passa dal registro [SystemChangeLog], quindi si annulla (anche dalla modalità provvisoria).
 * Non conosce Android: si prova con una shell finta.
 */
class SystemTools(
    private val runner: CommandRunner,
    private val log: SystemChangeLog,
) {
    // ---- Animazioni (§7.3) ----

    /** La scala attuale delle animazioni (1.0 = normale), o null se non si riesce a leggerla. */
    suspend fun animationScale(): String? {
        val r = runner.run(SystemCommand.GetSetting(SystemCommand.Namespace.GLOBAL, ANIMATOR_KEYS.first()))
        return r.output.trim().takeIf { r.ok && it.isNotEmpty() && it != "null" }
    }

    /** Imposta le tre scale (finestre, transizioni, animatori) allo stesso valore: 1, 0.5, 0.25 o 0. */
    suspend fun setAnimationScale(scale: String): Boolean {
        require(scale in ANIMATION_SCALES) { "Scala non consentita: $scale" }
        return ANIMATOR_KEYS.all { log.putSetting(SystemCommand.Namespace.GLOBAL, it, scale).ok }
    }

    suspend fun resetAnimationScale(): Boolean = log.revertSettings(SystemCommand.Namespace.GLOBAL, ANIMATOR_KEYS.toSet())

    // ---- DNS anti-pubblicità (§13.1) ----

    suspend fun adBlockDnsOn(): Boolean {
        val mode = runner.run(SystemCommand.GetSetting(SystemCommand.Namespace.GLOBAL, DNS_MODE))
        val host = runner.run(SystemCommand.GetSetting(SystemCommand.Namespace.GLOBAL, DNS_HOST))
        return mode.ok && mode.output.trim() == "hostname" && host.output.trim() == AD_BLOCK_HOST
    }

    /** Con [on] il DNS privato punta ad AdGuard (gratuito); senza, si torna a ciò che c'era prima. */
    suspend fun setAdBlockDns(on: Boolean): Boolean =
        if (on) {
            log.putSetting(SystemCommand.Namespace.GLOBAL, DNS_HOST, AD_BLOCK_HOST).ok &&
                log.putSetting(SystemCommand.Namespace.GLOBAL, DNS_MODE, "hostname").ok
        } else {
            log.revertSettings(SystemCommand.Namespace.GLOBAL, setOf(DNS_MODE, DNS_HOST))
        }

    // ---- Turbo (§7.3) ----

    /**
     * Ferma le app in [candidates]. I pacchetti protetti vengono saltati dal comando stesso; chi chiama toglie
     * quelle che servono (preferiti, musica in corso, tastiera). Le app si riavviano quando servono: niente da annullare.
     */
    suspend fun turbo(candidates: Collection<String>): TurboResult {
        var stopped = 0
        var failed = 0
        for (pkg in candidates.distinct()) {
            val result = runCatching { runner.run(SystemCommand.ForceStop(pkg)) }.getOrNull()
            if (result?.ok == true) stopped++ else failed++
        }
        return TurboResult(stopped, failed)
    }

    // ---- Densità dello schermo con ripristino automatico (§7.3) ----

    suspend fun density(): Density? = parseDensity(runner.run(SystemCommand.GetDensity).output)

    /**
     * Cambia la densità. Resta «non confermata» finché chi chiama non fa [confirmDensity]; se non succede
     * (o l'app si chiude), [revertUnconfirmedDensity] la annulla.
     */
    suspend fun setDensity(dpi: Int): Boolean {
        val now = density() ?: return false
        return log.setDensity(now.current.takeIf { it != now.physical }, dpi).ok
    }

    fun confirmDensity() = log.confirmDensity()

    suspend fun revertUnconfirmedDensity(): Boolean = log.revertUnconfirmedDensity()

    // ---- Congelamento (§7.2) ----

    /** I pacchetti congelati da NOVA (si riattivano con [unfreeze]). */
    fun frozen(): List<String> = log.entries().filterIsInstance<SystemChange.PackageDisabled>().map { it.packageName }.distinct()

    suspend fun freeze(packageName: String): Boolean = log.disablePackage(packageName).ok

    suspend fun unfreeze(packageName: String): Boolean = log.enablePackage(packageName).ok

    companion object {
        val ANIMATION_SCALES = listOf("1", "0.5", "0.25", "0")
        val ANIMATOR_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        const val DNS_MODE = "private_dns_mode"
        const val DNS_HOST = "private_dns_specifier"
        const val AD_BLOCK_HOST = "dns.adguard.com"

        /** Legge l'output di `wm density`: «Physical density: 160» e, se cambiata, «Override density: 240». */
        fun parseDensity(output: String): Density? {
            fun field(name: String) = Regex("$name density:\\s*(\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()
            val physical = field("Physical") ?: return null
            return Density(physical, field("Override") ?: physical)
        }
    }
}

/**
 * Quali app ferma il Turbo: tutte quelle avviabili, tranne le protette e quelle che chi chiama vuole tenere
 * (preferiti, musica, tastiera, Home).
 */
object TurboPlan {
    fun candidates(
        launchable: Collection<String>,
        keep: Set<String>,
    ): List<String> = launchable.distinct().filter { it !in keep && !ProtectedPackages.isProtected(it) }
}
