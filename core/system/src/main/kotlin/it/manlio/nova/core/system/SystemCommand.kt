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

    /**
     * Fa compilare ad ART il codice di un'app di NOVA (tutto, `speed`). Dopo ogni aggiornamento fuori dal Play Store Android lo
     * lascia in «verify»: interpretato e compilato a pezzi mentre gira, quindi più lento finché non passa l'ottimizzazione notturna.
     * Solo i pacchetti di NOVA.
     */
    data class CompileOwn(val packageName: String) : SystemCommand {
        init {
            require(packageName in OWN_PACKAGES) { "Si compilano solo le app di NOVA, non $packageName" }
        }

        override val argv = listOf("cmd", "package", "compile", "-m", "speed", "-f", packageName)

        companion object {
            val OWN_PACKAGES = listOf("it.manlio.nova", "it.manlio.nova.keyboard")
        }
    }

    data class ForceStop(val packageName: String) : SystemCommand {
        init {
            requirePackage(packageName)
            require(!ProtectedPackages.isProtected(packageName)) { "Il pacchetto $packageName è protetto" }
        }

        override val argv = listOf("am", "force-stop", packageName)
    }

    /**
     * La risoluzione a cui gira un gioco (`cmd game`, SPEC §8.2): [percent] è una di [GAME_SCALES], null = torna normale (modalità
     * `standard` del gioco). Vale al prossimo avvio del gioco. Mai sotto il 65%: più in basso il gioco diventa troppo sgranato.
     * Si usa tramite [SystemChangeLog.setGameScale], che la registra.
     */
    data class GameScale(val packageName: String, val percent: Int?) : SystemCommand {
        init {
            requirePackage(packageName)
            require(!ProtectedPackages.isProtected(packageName)) { "Il pacchetto $packageName è protetto" }
            require(percent == null || percent in GAME_SCALES) { "Risoluzione non consentita: $percent" }
        }

        override val argv =
            if (percent == null) {
                listOf("cmd", "game", "mode", "standard", packageName)
            } else {
                listOf("cmd", "game", "set", "--downscale", "0.$percent", packageName)
            }
    }

    /** Elenca i livelli grafici (per trovare quello di un gioco). Solo lettura. */
    data object ListSurfaceLayers : SystemCommand {
        override val argv = listOf("dumpsys", "SurfaceFlinger", "--list")
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

    /**
     * Impedisce alla tendina di Android di aprirsi, così il gesto dall'alto è solo di NOVA. Si usa tramite
     * [SystemChangeLog.setShadeLock], che la registra. Non è un'impostazione salvata: un riavvio la toglie.
     */
    data class ShadeLock(val locked: Boolean) : SystemCommand {
        override val argv = listOf("cmd", "statusbar", "send-disable-flag", if (locked) "statusbar-expansion" else "none")
    }

    /** Elenca i pacchetti del tablet, solo lettura: NOVA da sola non vede quelli di sistema senza icona. */
    data object ListPackages : SystemCommand {
        override val argv = listOf("pm", "list", "packages")
    }

    /** Chiude la tendina di Android: sul tablet di riferimento l'azione di accessibilità non fa niente. Non cambia impostazioni. */
    data object CollapseShade : SystemCommand {
        override val argv = listOf("cmd", "statusbar", "collapse")
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

    /**
     * Registra lo schermo in `Movies` per al massimo [LIMIT_S] secondi: il comando resta in corso finché non finisce o non arriva
     * [ScreenRecordStop]. [stamp] sono solo cifre, così il percorso non può uscire dalla cartella. Non cambia impostazioni.
     */
    data class ScreenRecord(val stamp: String) : SystemCommand {
        init {
            require(stamp.isNotEmpty() && stamp.all(Char::isDigit)) { "Nome di registrazione non consentito" }
        }

        val path = "/sdcard/Movies/nova-$stamp.mp4"
        override val argv = listOf("screenrecord", "--time-limit", LIMIT_S.toString(), path)

        companion object {
            const val LIMIT_S = 180
        }
    }

    /** Ferma [ScreenRecord] e chiude il file come si deve. */
    data object ScreenRecordStop : SystemCommand {
        override val argv = listOf("pkill", "-INT", "screenrecord")
    }

    /**
     * Apre un'app in una finestra flottante (SPEC §8.3). Android non dà questa scelta alle app normali; `5` è la modalità freeform.
     * Sul tablet di riferimento funziona senza cambiare impostazioni. Non modifica niente: niente da annullare.
     */
    data class OpenInWindow(val packageName: String, val activity: String) : SystemCommand {
        init {
            requirePackage(packageName)
            require(ACTIVITY.matches(activity)) { "Nome di activity non valido: $activity" }
        }

        override val argv = listOf("am", "start", "--windowingMode", "5", "-n", "$packageName/$activity")
    }

    /** Il filtro di rete per app di Android (SPEC §13.2): senza, il blocco di una singola app non ha effetto. Si perde al riavvio. */
    data class NetFilter(val on: Boolean) : SystemCommand {
        override val argv = listOf("cmd", "connectivity", "set-chain3-enabled", on.toString())
    }

    /** Toglie o rida Internet a un'app. Mai ai pacchetti protetti né a NOVA, che senza rete non si aggiornerebbe. */
    data class AppNetwork(val packageName: String, val enabled: Boolean) : SystemCommand {
        init {
            requirePackage(packageName)
            require(!ProtectedPackages.isProtected(packageName) && packageName != OWN_PACKAGE) { "Il pacchetto $packageName è protetto" }
        }

        override val argv = listOf("cmd", "connectivity", "set-package-networking-enabled", enabled.toString(), packageName)
    }

    /** Una scorciatoia da tastiera per l'app davanti (SPEC §9.4): solo i tasti di [Key], niente testo libero. */
    data class KeyCombo(val keys: List<Key>) : SystemCommand {
        enum class Key(val code: String) { CTRL("KEYCODE_CTRL_LEFT"), SHIFT("KEYCODE_SHIFT_LEFT"), Z("KEYCODE_Z"), Y("KEYCODE_Y") }

        init {
            require(keys.size in 2..MAX_KEYS) { "Scorciatoia non valida" }
        }

        override val argv = listOf("input", "keycombination") + keys.map { it.code }

        private companion object {
            const val MAX_KEYS = 3
        }
    }

    /**
     * Un solo tasto per gli strumenti delle app di disegno (barra della penna, SPEC §9.4): pennello, gomma, contagocce, dimensione.
     * Solo questi tasti, mai lettere libere: si mandano solo quando davanti c'è un'app con quel profilo. Non cambia impostazioni.
     */
    data class ToolKey(val key: Key) : SystemCommand {
        enum class Key(val code: String) {
            B("KEYCODE_B"),
            E("KEYCODE_E"),
            I("KEYCODE_I"),
            LEFT_BRACKET("KEYCODE_LEFT_BRACKET"),
            RIGHT_BRACKET("KEYCODE_RIGHT_BRACKET"),
        }

        override val argv = listOf("input", "keyevent", key.code)
    }

    /** L'elenco dei task recenti, per sapere quali app sono aperte in finestra (SPEC §8.3). Solo lettura. */
    data object ListRecents : SystemCommand {
        override val argv = listOf("dumpsys", "activity", "recents")
    }

    /** Legge i permessi usati da un'app e quando (`appops get`, SPEC §13.3). Solo lettura. */
    data class AppOps(val packageName: String) : SystemCommand {
        init {
            requirePackage(packageName)
        }

        override val argv = listOf("appops", "get", packageName)
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

        /** Le risoluzioni scelte per un gioco, in percentuale, dalla più alta alla più bassa (il minimo è l'ultima). */
        val GAME_SCALES = listOf(90, 80, 70, 65)

        /** La risoluzione dopo [percent] (null = normale): si scende fino al minimo e poi si torna normale. */
        fun nextGameScale(percent: Int?): Int? =
            if (percent == null) GAME_SCALES.first() else GAME_SCALES.getOrNull(GAME_SCALES.indexOf(percent) + 1)

        private const val MAX_PACKAGE_LENGTH = 128
        private val KEY = Regex("^[A-Za-z0-9_.]{1,64}$")
        private val VALUE = Regex("^[A-Za-z0-9_.:,/+-]{0,128}$")
        private val ACTIVITY = Regex("^[A-Za-z][A-Za-z0-9_.$]*$")
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

    /** Navigazione con i 3 tasti al posto dei gesti (niente «Indietro» dal bordo); spento = gesti di Android. */
    THREE_BUTTON_NAV(
        "cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton",
        "cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural",
    ),
    ;

    /** Gli argomenti del comando: una lista fissa, mai una stringa interpretata da una shell. */
    internal fun argv(on: Boolean): List<String> = (if (on) onCommand else offCommand).split(' ')
}

/** Legge l'uscita di `pm list packages`: una riga «package:nome» per pacchetto. */
fun parsePackages(output: String): Set<String> =
    output
        .lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("package:") }
        .map { it.removePrefix("package:") }
        .toSet()

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
