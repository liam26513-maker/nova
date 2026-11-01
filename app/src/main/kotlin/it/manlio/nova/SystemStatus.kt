package it.manlio.nova

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs

/** Foto dello stato del tablet (SPEC §7.4): memoria, spazio e batteria. Solo lettura, nessun permesso. */
data class SystemStatus(
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val storageFreeMb: Long,
    val storageTotalMb: Long,
    val batteryPercent: Int,
    val batteryTempC: Float?,
    val batteryHealth: BatteryHealth,
    val batteryCycles: Int?,
    val charging: Boolean,
)

enum class BatteryHealth { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, COLD, FAILURE, UNKNOWN }

object SystemStatusReader {
    fun read(context: Context): SystemStatus {
        val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val stat = StatFs(Environment.getDataDirectory().path)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return SystemStatus(
            ramUsedMb = (mem.totalMem - mem.availMem) / MB,
            ramTotalMb = mem.totalMem / MB,
            storageFreeMb = stat.availableBytes / MB,
            storageTotalMb = stat.totalBytes / MB,
            batteryPercent = if (level < 0 || scale <= 0) 0 else level * PERCENT / scale,
            batteryTempC =
                battery?.getIntExtra(
                    BatteryManager.EXTRA_TEMPERATURE,
                    Int.MIN_VALUE,
                )?.takeIf { it != Int.MIN_VALUE }?.let { it / TENTHS },
            batteryHealth = healthOf(battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0),
            batteryCycles = battery?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 },
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }

    fun healthOf(code: Int): BatteryHealth =
        when (code) {
            BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
            else -> BatteryHealth.UNKNOWN
        }

    /** «3,2 GB» da megabyte; sotto il gigabyte, «512 MB». */
    fun size(mb: Long): String = if (mb >= GB_IN_MB) "%.1f GB".format(mb.toDouble() / GB_IN_MB) else "$mb MB"

    private const val MB = 1024L * 1024L
    private const val GB_IN_MB = 1024L
    private const val PERCENT = 100
    private const val TENTHS = 10f
}
