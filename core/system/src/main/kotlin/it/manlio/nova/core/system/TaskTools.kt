package it.manlio.nova.core.system

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import java.io.ByteArrayOutputStream

/**
 * Le recenti di Android lette e comandate dal processo con i privilegi della shell ([ShellService]). Le API sono nascoste
 * (`IActivityTaskManager`), quindi si chiamano per nome: se una versione di Android le cambia, ogni funzione risponde «niente»
 * invece di rompersi. La shell ha i permessi che servono (REAL_GET_TASKS, READ_FRAME_BUFFER, START_TASKS_FROM_RECENTS,
 * REMOVE_TASKS); verificato sul tablet di riferimento il 9 novembre 2026.
 */
internal object TaskTools {
    private const val RECENT_IGNORE_UNAVAILABLE = 0x0002
    private const val JPEG_QUALITY = 85
    private const val SNAPSHOT_ARGS_V14 = 3 // Android 14: (id, bassa risoluzione, fai la foto se manca)

    private val service: Any by lazy {
        Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)!!
    }

    private fun method(name: String) = service.javaClass.methods.firstOrNull { it.name == name }

    fun recentTasks(max: Int): String =
        runCatching {
            val slice = method("getRecentTasks")!!.invoke(service, max, RECENT_IGNORE_UNAVAILABLE, 0)!!
            val list = slice.javaClass.getMethod("getList").invoke(slice) as List<*>
            RecentTasksCodec.encode(
                list.filterNotNull().mapNotNull { info ->
                    val id = info.javaClass.getField("taskId").getInt(info)
                    val top = info.javaClass.getField("topActivity").get(info) as ComponentName?
                    val base = (info.javaClass.getField("baseIntent").get(info) as Intent?)?.component
                    val component = top ?: base ?: return@mapNotNull null
                    RecentTask(id, component.packageName, component.flattenToString())
                },
            )
        }.getOrDefault("")

    fun snapshot(
        taskId: Int,
        maxSide: Int,
    ): ByteArray =
        runCatching {
            val get = method("getTaskSnapshot")!!
            val snap =
                (
                    if (get.parameterCount == SNAPSHOT_ARGS_V14) {
                        get.invoke(
                            service,
                            taskId,
                            true,
                            false,
                        )
                    } else {
                        get.invoke(service, taskId, true)
                    }
                )!!
            val buffer = snap.javaClass.getMethod("getHardwareBuffer").invoke(snap) as HardwareBuffer
            val hardware = Bitmap.wrapHardwareBuffer(buffer, null)!!
            val soft = hardware.copy(Bitmap.Config.ARGB_8888, false)
            hardware.recycle()
            buffer.close()
            val scale = minOf(1f, maxSide.toFloat() / maxOf(soft.width, soft.height))
            val sized =
                if (scale < 1f) Bitmap.createScaledBitmap(soft, (soft.width * scale).toInt(), (soft.height * scale).toInt(), true) else soft
            ByteArrayOutputStream().use { out ->
                sized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                if (sized !== soft) sized.recycle()
                soft.recycle()
                out.toByteArray()
            }
        }.getOrDefault(ByteArray(0))

    fun open(taskId: Int): Boolean =
        runCatching {
            method("startActivityFromRecents")!!.invoke(service, taskId, null) as Int >= 0
        }.getOrDefault(false)

    fun remove(taskId: Int): Boolean = runCatching { method("removeTask")!!.invoke(service, taskId) as Boolean }.getOrDefault(false)
}
