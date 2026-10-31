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

    /** Spegne lo schermo (come il tasto di accensione). Non cambia impostazioni: niente da annullare. */
    data object Sleep : SystemCommand {
        override val argv = listOf("input", "keyevent", "KEYCODE_SLEEP")
    }

    /**
     * Installa un aggiornamento di NOVA. Il file deve stare nella cartella esterna privata dell'app
     * (leggibile dalla shell), mai altrove: [ownPackage] è il pacchetto dell'app stessa.
     */
    data class InstallApk(val path: String, val ownPackage: String) : SystemCommand {
        init {
            requirePackage(ownPackage)
            val allowed = Regex("^/(storage/emulated/0|sdcard)/Android/data/${Regex.escape(ownPackage)}/(cache|files)/[\\w.-]+\\.apk$")
            require(allowed.matches(path) && ".." !in path) { "Percorso APK non consentito: $path" }
        }

        override val argv = listOf("pm", "install", "-r", "-i", ownPackage, path)
    }

    private companion object {
        const val MAX_PACKAGE_LENGTH = 128
        val KEY = Regex("^[A-Za-z0-9_.]{1,64}$")
        val VALUE = Regex("^[A-Za-z0-9_.:,/+-]{0,128}$")
        val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        fun requireKey(key: String) = require(KEY.matches(key)) { "Chiave non consentita: $key" }

        fun requirePackage(name: String) =
            require(PACKAGE.matches(name) && name.length <= MAX_PACKAGE_LENGTH) {
                "Nome di pacchetto non valido: $name"
            }
    }
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
