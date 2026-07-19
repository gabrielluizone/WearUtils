package com.matejdro.wearutils.preferencesync

import com.google.android.gms.wearable.DataMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencePusherTest {

    @Test
    fun `snapshot includes transport revision sorted inventory and string sets`() {
        val dataMap = DataMap()

        PreferencePusher.populateDataMap(
                dataMap,
                linkedMapOf(
                        "title" to "value",
                        "enabled" to true,
                        "apps" to linkedSetOf("music.two", "music.one")),
                syncRevision = 42L)

        assertEquals(42L, dataMap.getLong(PreferencePusher.SYNC_REVISION_KEY))
        assertEquals(
                arrayListOf("apps", "enabled", "title"),
                dataMap.getStringArrayList(PreferencePusher.SYNC_KEYS_KEY))
        assertEquals(setOf("music.one", "music.two"),
                dataMap.getStringArrayList("apps")!!.toSet())
        assertEquals("value", dataMap.getString("title"))
        assertTrue(dataMap.getBoolean("enabled"))
    }

    @Test
    fun `unsupported values are omitted from both payload and inventory`() {
        val dataMap = DataMap()

        PreferencePusher.populateDataMap(
                dataMap,
                mapOf("supported" to 7, "unsupported" to listOf(1, 2)),
                syncRevision = 7L)

        assertEquals(arrayListOf("supported"),
                dataMap.getStringArrayList(PreferencePusher.SYNC_KEYS_KEY))
        assertFalse(dataMap.containsKey("unsupported"))
    }
}
