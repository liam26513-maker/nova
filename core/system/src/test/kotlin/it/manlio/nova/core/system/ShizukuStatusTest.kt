package it.manlio.nova.core.system

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuStatusTest {
    @Test
    fun `Shizuku not yet attached does not crash NOVA, it counts as not running`() {
        val status = ShizukuBridge.statusOf({ true }, { throw IllegalStateException("Not an attached client") })
        assertEquals(ShizukuStatus.NOT_RUNNING, status)
    }

    @Test
    fun `the usual states stay the same`() {
        assertEquals(ShizukuStatus.NOT_RUNNING, ShizukuBridge.statusOf({ false }, { PackageManager.PERMISSION_GRANTED }))
        assertEquals(ShizukuStatus.NOT_RUNNING, ShizukuBridge.statusOf({ error("binder") }, { PackageManager.PERMISSION_GRANTED }))
        assertEquals(ShizukuStatus.READY, ShizukuBridge.statusOf({ true }, { PackageManager.PERMISSION_GRANTED }))
        assertEquals(ShizukuStatus.NEEDS_PERMISSION, ShizukuBridge.statusOf({ true }, { PackageManager.PERMISSION_DENIED }))
    }
}
