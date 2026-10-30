package it.manlio.nova.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import it.manlio.nova.core.system.ShizukuBridge
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemCommand
import java.io.File
import java.security.MessageDigest

/** Esito della verifica di un APK scaricato. */
sealed interface ApkCheck {
    data object Ok : ApkCheck

    data class Rejected(val reason: String) : ApkCheck
}

/**
 * Verifica e installa un aggiornamento di NOVA (SPEC §4.6):
 * stesso pacchetto, versione attesa, **stessa firma** dell'app installata.
 * Con Shizuku l'installazione è silenziosa; senza, Android chiede una conferma.
 */
class ApkInstaller(
    private val context: Context,
    private val shizuku: ShizukuBridge,
) {
    fun check(
        apk: File,
        expectedVersionCode: Int,
    ): ApkCheck {
        val pm = context.packageManager
        val archive =
            pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
                ?: return ApkCheck.Rejected("Il file scaricato non è un'app valida")
        if (archive.packageName != context.packageName) return ApkCheck.Rejected("L'app scaricata non è NOVA")
        if (archive.longVersionCode != expectedVersionCode.toLong()) return ApkCheck.Rejected("Versione inattesa nell'app scaricata")
        val installed =
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        val ours = installed.signingInfo?.apkContentsSigners.fingerprints()
        val theirs = archive.signingInfo?.apkContentsSigners.fingerprints()
        if (ours.isEmpty() || ours != theirs) return ApkCheck.Rejected("La firma non corrisponde: aggiornamento rifiutato")
        return ApkCheck.Ok
    }

    /** @return null se avviata, altrimenti il motivo dell'errore. */
    suspend fun install(apk: File): String? =
        if (shizuku.status.value == ShizukuStatus.READY) {
            val result = shizuku.run(SystemCommand.InstallApk(apk.path, context.packageName))
            if (result.ok) null else "Installazione non riuscita: ${result.output.trim().take(MAX_REASON)}"
        } else {
            installWithConfirmation(apk)
            null
        }

    private fun installWithConfirmation(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
            }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("nova.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(pending.intentSender)
        }
    }

    private fun Array<Signature>?.fingerprints(): Set<String> =
        orEmpty()
            .map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }
            .toSet()

    private companion object {
        const val MAX_REASON = 200
    }
}

/** Riceve l'esito dell'installazione senza Shizuku e, se serve, mostra la conferma di Android. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
