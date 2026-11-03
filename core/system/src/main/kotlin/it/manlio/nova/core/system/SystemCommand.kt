package it.manlio.nova.core.system

/**
 * Lista consentita dei comandi di sistema (SPEC §7.1). Solo questi possono raggiungere la shell,
 * sempre come lista di argomenti (mai una stringa interpretata), con ogni campo validato.
 * Aggiungere un comando significa aggiungere una classe qui, con la sua validazione e un test.
 */
sealed interface SystemCommand {
    val argv: List<String>

    enum class Namespace(val value: String) { SYSTEM("system"), SECURE("secure"), GLOBAL("global") }

    data class GetSetting(val namespace: Namespace, val key: String) : SystemCommand {
        init {
            requireKey(key)
        }

        override val argv = listOf("settings", "get", namespace.value, key)
    }

    /** [value] null = cancella l'impostazione (ripristino di un valore che prima non esisteva). */
    data class PutSetting(val namespace: Namespace, val key: String, val value: String?) : SystemCommand {
        init {
            requireKey(key)
            require(value == null || VALUE.matches(value)) { "Valore non consentito per $key" }
        }

        override val argv =
            if (value == null) {
                listOf("settings", "delete", namespace.value, key)
            } else {
                listOf("settings", "put", namespace.value, key, value)
            }
    }

    data class DisablePackage(val packageName: String) : SystemCommand {
        init {
            requirePackage(packageName)
            require(!ProtectedPackages.isProtected(packageName)) { "Il pacchetto $packageName è protetto" }
        }

        override val argv = listOf("pm", "disable-user", "--user", "0", packageName)
    }

    data class EnablePackage(val packageName: String) : SystemCommand {
        init {
            requirePackage(packageName)
        }

        override val argv = listOf("pm", "enable", "--user", "0", packageName)
    }

    data class ForceStop(val packageName: String) : SystemCommand {
        init {
            requirePackage(packageName)
            require(!ProtectedPackages.isProtected(packageName)) { "Il pacchetto $packageName è protetto" }
        }

        override val argv = listOf("am", "force-stop", packageName)
    }

    /** Elenca i livelli grafici (per trovare quello di un gioco). Solo lettura. */
    data object ListSurfaceLayers : SystemCommand {
        override val argv = listOf("dumpsys", "SurfaceFlinger", "--list")
    }

    /**
     * Legge i tempi degli ultimi fotogrammi di un livello grafico (`dumpsys SurfaceFlinger --latency`): serve a misurare FPS e scatti
     * di un gioco. Solo lettura. Il nome del livello (es. `SurfaceView[com.gioco/com.gioco.Main](BLAST)#0`) ha un insieme chiuso
     * di caratteri; niente virgolette, punti e virgola o altro che una shell potrebbe interpretare (comunque non c'è shell: è una lista).
     */
    data class SurfaceLatency(val layer: String) : SystemCommand {
        init {
            require(LAYER.matches(layer)) { "Nome di livello non consentito" }
        }

        override val argv = listOf("dumpsys", "SurfaceFlinger", "--latency", layer)
    }

    /**
     * Le statistiche dei fotogrammi di SurfaceFlinger (`dumpsys SurfaceFlinger --timestats`): su Android 12+ è dove si leggono FPS e
     * fotogrammi persi dei giochi, perché `--latency` non dà più nulla per i livelli BLAST. Accendere è temporaneo (si spegne con
     * [Step.DISABLE], e al riavvio comunque) e non cambia nessuna impostazione salvata.
     */
    data class TimeStats(val step: Step) : SystemCommand {
        enum class Step(val flag: String) { ENABLE("-enable"), CLEAR("-clear"), DUMP("-dump"), DISABLE("-disable") }

        override val argv = listOf("dumpsys", "SurfaceFlinger", "--timestats", step.flag)
    }

    /** Legge la densità dello schermo: «Physical density» e, se cambiata, «Override density». */
    data object GetDensity : SystemCommand {
        override val argv = listOf("wm", "density")
    }

    /**
     * Cambia la densità dello schermo (DPI). [dpi] null = torna a quella di fabbrica. Limiti stretti (da [MIN_DPI] a [MAX_DPI]):
     * un valore fuori scala renderebbe lo schermo inutilizzabile. Si usa solo con il ripristino automatico ([SystemTools]).
     */
    data class SetDensity(val dpi: Int?) : SystemCommand {
        init {
            require(dpi == null || dpi in MIN_DPI..MAX_DPI) { "Densità non consentita: $dpi" }
        }

        override val argv = listOf("wm", "density", dpi?.toString() ?: "reset")
    }

    /** Spegne lo schermo (come il tasto di accensione). Non cambia impostazioni: niente da annullare. */
    data object Sleep : SystemCommand {
        override val argv = listOf("input", "keyevent", "KEYCODE_SLEEP")
    }

    /**
     * Installa un aggiornamento di NOVA leggendo l'APK dallo standard input (`pm install -S <byte>`): il file lo apre
     * l'app e lo passa alla shell (`ShizukuBridge.installApk`), che non potrebbe leggerlo dalle cartelle private dell'app.
     * [size] è la dimensione esatta in byte (da 1 a [MAX_APK_BYTES]); [ownPackage] il pacchetto di NOVA, mai un altro.
     */
    data class InstallApk(val size: Long, val ownPackage: String) : SystemCommand {
        init {
            requirePackage(ownPackage)
            require(size in 1..MAX_APK_BYTES) { "Dimensione dell'APK non consentita: $size" }
        }

        override val argv = listOf("pm", "install", "-r", "-i", ownPackage, "-S", size.toString())
    }

    /**
     * Permette (o toglie il permesso) a NOVA di usare le impostazioni con restrizioni di Android, necessarie per accendere il
     * servizio di accessibilità di un'app installata a mano (`appops set … ACCESS_RESTRICTED_SETTINGS`). Vale **solo per NOVA
     * stessa** ([ownPackage] deve essere il suo pacchetto): mai per un'altra app. [allow] falso torna al valore predefinito.
     */
    data class SetRestrictedSettings(val ownPackage: String, val allow: Boolean) : SystemCommand {
        init {
            requirePackage(ownPackage)
            require(ownPackage == OWN_PACKAGE) { "Solo NOVA può sbloccare le proprie impostazioni: $ownPackage" }
        }

        override val argv = listOf("appops", "set", ownPackage, "ACCESS_RESTRICTED_SETTINGS", if (allow) "allow" else "default")
    }

    /**
     * Accende o spegne uno degli interruttori del pannello rapido di Android (Wi-Fi, Bluetooth, aereo…). L'insieme è chiuso
     * ([QuickToggle]): nessuna stringa dell'utente arriva alla shell, solo questi comandi fissi.
     * Si usa sempre tramite [SystemTools.setToggle], che registra lo stato precedente.
     */
    data class SetToggle(val toggle: QuickToggle, val on: Boolean) : SystemCommand {
        override val argv = toggle.argv(on)
    }

    companion object {
        const val MIN_DPI = 120
        const val MAX_DPI = 640
        const val MAX_APK_BYTES = 200L * 1024 * 1024
        const val OWN_PACKAGE = "it.manlio.nova"
        private const val MAX_PACKAGE_LENGTH = 128
        private val LAYER = Regex("^[A-Za-z0-9_.#/\\[\\]()\\$:=, -]{1,250}$")
        private val KEY = Regex("^[A-Za-z0-9_.]{1,64}$")
        private val VALUE = Regex("^[A-Za-z0-9_.:,/+-]{0,128}$")
        private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        private fun requireKey(key: String) = require(KEY.matches(key)) { "Chiave non consentita: $key" }

        private fun requirePackage(name: String) =
            require(PACKAGE.matches(name) && name.length <= MAX_PACKAGE_LENGTH) {
                "Nome di pacchetto non valido: $name"
            }
    }
}

/**
 * Gli interruttori che il centro di controllo può cambiare con un tocco, se Shizuku è attivo (un'app normale non può:
 * Android lo vieta dal 10). Ognuno ha il suo comando fisso, in `argv`: nulla viene mai interpretato da una shell.
 */
enum class QuickToggle(private val onCommand: String, private val offCommand: String) {
    WIFI("svc wifi enable", "svc wifi disable"),
    BLUETOOTH("svc bluetooth enable", "svc bluetooth disable"),
    AIRPLANE("cmd connectivity airplane-mode enable", "cmd connectivity airplane-mode disable"),
    DND("cmd notification set_dnd priority", "cmd notification set_dnd off"),
    DARK_THEME("cmd uimode night yes", "cmd uimode night no"),
    LOCATION("cmd location set-location-enabled true", "cmd location set-location-enabled false"),
    BATTERY_SAVER("settings put global low_power 1", "settings put global low_power 0"),
    DATA_SAVER("cmd netpolicy set restrict-background true", "cmd netpolicy set restrict-background false"),
    NIGHT_LIGHT("settings put secure night_display_activated 1", "settings put secure night_display_activated 0"),
    INVERSION(
        "settings put secure accessibility_display_inversion_enabled 1",
        "settings put secure accessibility_display_inversion_enabled 0",
    ),
    ;

    /** Gli argomenti del comando: una lista fissa, mai una stringa interpretata da una shell. */
    internal fun argv(on: Boolean): List<String> = (if (on) onCommand else offCommand).split(' ')
}

/** Pacchetti che NOVA non disattiva né chiude mai (SPEC §7.1). */
object ProtectedPackages {
    private val exact =
        setOf(
            "android",
            "it.manlio.nova",
            "com.android.settings",
            "com.android.systemui",
            "com.android.phone",
            "com.android.shell",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.launcher3",
            "com.android.inputmethod.latin",
            "com.google.android.inputmethod.latin",
            "com.google.android.gms",
            "com.android.vending",
            "com.android.networkstack",
            "com.google.android.networkstack",
            "com.android.bluetooth",
            "moe.shizuku.privileged.api",
        )
    private val prefixes = listOf("com.android.providers.", "com.android.server.", "com.android.networkstack.", "it.manlio.nova.")

    fun isProtected(packageName: String): Boolean = packageName in exact || prefixes.any { packageName.startsWith(it) }
}
