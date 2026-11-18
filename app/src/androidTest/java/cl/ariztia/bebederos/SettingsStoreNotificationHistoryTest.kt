package cl.ariztia.bebederos

import androidx.test.platform.app.InstrumentationRegistry
import cl.ariztia.bebederos.data.local.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreNotificationHistoryTest {

    @Test
    fun notifiedAlertPersistsAcrossStoreInstancesWithoutContaminatingAnotherUser() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstStore = SettingsStore(context)
        val userId = System.nanoTime()
        val otherUserId = userId + 1L
        val alertId = System.currentTimeMillis()

        assertFalse(firstStore.wasNotified(userId, alertId))
        firstStore.markNotified(userId, alertId)

        val storeAfterRestart = SettingsStore(context)
        assertTrue(storeAfterRestart.wasNotified(userId, alertId))
        assertFalse(storeAfterRestart.wasNotified(otherUserId, alertId))
    }
}
