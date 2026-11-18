package cl.ariztia.bebederos.util

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class NotificationPermissionPolicyTest {

    @Test
    fun android13RequestsPermissionOnceWhenACriticalAlertBecomesVisible() {
        assertTrue(shouldRequestNotificationPermission(33, true, true, true, false, false))
        assertFalse(shouldRequestNotificationPermission(33, true, true, true, false, true))
    }

    @Test
    fun olderAndroidGrantedPermissionAndDisabledPreferenceDoNotRequestIt() {
        assertFalse(shouldRequestNotificationPermission(32, true, true, true, false, false))
        assertFalse(shouldRequestNotificationPermission(33, true, true, true, true, false))
        assertFalse(shouldRequestNotificationPermission(33, true, true, false, false, false))
    }

    @Test
    fun requestWaitsForSessionCriticalDataAndDataStoreInitialization() {
        assertFalse(shouldRequestNotificationPermission(33, false, true, true, false, false))
        assertFalse(shouldRequestNotificationPermission(33, true, false, true, false, false))
        assertFalse(shouldRequestNotificationPermission(33, true, true, true, false, null))
    }
}
