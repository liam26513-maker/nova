package it.manlio.nova.core.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.StatFs
import android.os.UserManager
import android.view.Display
import android.view.InputDevice
import kotlinx.serialization.json.Json
import java.io.File

/** Legge l'hardware reale e conserva il profilo in un file privato dell'app. */
class DeviceProfiler(private val context: Context) {
    private val file = File(context.filesDir, "device-profile.json")

    fun load(): DeviceProfile? = runCatching { JSON.decodeFromString<DeviceProfile>(file.readText()) }.getOrNull()

    fun save(profile: DeviceProfile) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(JSON.encodeToString(DeviceProfile.serializer(), profile))
        tmp.renameTo(file)
    }

    /** Lettura istantanea (senza benchmark). Da chiamare fuori dal thread principale: legge /sys. */
    fun readHardware(): DeviceProfile {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        val metrics = context.resources.displayMetrics
        val pm = context.packageManager
        return DeviceProfile(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            soc = listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" "),
            androidSdk = Build.VERSION.SDK_INT,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuClustersMhz = cpuClustersMhz(),
            ramMb = mem.totalMem / MB,
            freeStorageMb = StatFs(context.filesDir.path).availableBytes / MB,
            screenWidthPx = maxOf(mode.physicalWidth, mode.physicalHeight),
            screenHeightPx = minOf(mode.physicalWidth, mode.physicalHeight),
            densityDpi = metrics.densityDpi,
            refreshRatesHz = display.supportedModes.map { it.refreshRate }.distinct().sortedDescending(),
            // RenderEffect esiste da Android 12 (minSdk 31).
            supportsRealtimeBlur = true,
            supportsFreeform = pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT),
            supportsMultipleUsers = UserManager.supportsMultipleUsers(),
            hasStylusDigitizer =
                InputDevice.getDeviceIds().any { id ->
                    InputDevice.getDevice(id)?.supportsSource(InputDevice.SOURCE_STYLUS) == true
                },
        )
    }

    /** Frequenze massime per cluster da /sys (accessibile senza permessi per cpufreq). */
    private fun cpuClustersMhz(): List<Int> =
        (0 until Runtime.getRuntime().availableProcessors())
            .mapNotNull { cpu ->
                runCatching {
                    File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toInt() / KHZ_PER_MHZ
                }.getOrNull()
            }.distinct()
            .sortedDescending()

    companion object {
        private const val MB = 1024L * 1024L
        private const val KHZ_PER_MHZ = 1000
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
