package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionGroupsTest {
    @Test
    fun `android permission names fall into short groups`() {
        assertEquals(PermissionGroups.CAMERA, PermissionGroups.of("android.permission.CAMERA"))
        assertEquals(PermissionGroups.LOCATION, PermissionGroups.of("android.permission.ACCESS_BACKGROUND_LOCATION"))
        assertEquals(PermissionGroups.MEDIA, PermissionGroups.of("android.permission.READ_MEDIA_IMAGES"))
        assertEquals(PermissionGroups.MEDIA, PermissionGroups.of("android.permission.READ_EXTERNAL_STORAGE"))
        assertEquals(PermissionGroups.PHONE, PermissionGroups.of("android.permission.READ_PHONE_STATE"))
        assertEquals(PermissionGroups.NEARBY, PermissionGroups.of("android.permission.BLUETOOTH_CONNECT"))
        assertEquals(PermissionGroups.NOTIFICATIONS, PermissionGroups.of("android.permission.POST_NOTIFICATIONS"))
        assertEquals(PermissionGroups.OTHER, PermissionGroups.of("com.example.permission.SOMETHING"))
    }
}
