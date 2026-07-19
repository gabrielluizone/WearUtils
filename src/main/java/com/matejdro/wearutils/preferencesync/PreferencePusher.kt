package com.matejdro.wearutils.preferencesync

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.wearable.DataApi.DataItemResult
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

object PreferencePusher {
    private const val TAG = "PreferencePusher"

    /**
     * Data Layer only emits a change event when a DataItem's bytes change. A sync nonce therefore
     * belongs to the transport envelope rather than the destination SharedPreferences: it makes a
     * requested full refresh observable even when Play Services already has an identical cached
     * snapshot (for example after the watch app was reinstalled or its local preferences were
     * cleared).
     */
    const val SYNC_REVISION_KEY = "__wearutils_preferences_sync_revision"

    /** Inventory used by the receiver to distinguish phone-owned keys from watch-local keys and
     * remove a phone preference that was explicitly deleted instead of leaving a stale value. */
    const val SYNC_KEYS_KEY = "__wearutils_preferences_sync_keys"

    private val revision = AtomicLong(System.currentTimeMillis())
    private val coroutinePushMutex = Mutex()

    fun pushPreferences(connectedApiClient: GoogleApiClient, preferences: SharedPreferences, wearSchemePrefix: String, urgent: Boolean): PendingResult<DataItemResult> {
        val putDataMapRequest = PutDataMapRequest.create(wearSchemePrefix)
        populateDataMap(putDataMapRequest.dataMap, preferences.all, nextRevision())
        if (urgent) putDataMapRequest.setUrgent()
        return Wearable.DataApi.putDataItem(connectedApiClient, putDataMapRequest.asPutDataRequest())
    }

    suspend fun pushPreferences(context: Context, preferences: SharedPreferences, wearSchemePrefix: String, urgent: Boolean): DataItem {
        // Preference listeners can fire once per key for a bulk edit. Serializing here ensures an
        // older snapshot cannot finish after a newer one and become the final DataItem. Snapshot
        // inside the lock so every queued request sees the latest committed values.
        return coroutinePushMutex.withLock {
            // Task.await() cannot cancel the underlying Play Services Task. Once a put starts,
            // keep the mutex until that Task actually completes; otherwise a cancelled caller
            // could release the lock and let an older snapshot finish after its replacement.
            withContext(NonCancellable) {
                val dataClient = Wearable.getDataClient(context.applicationContext)
                val putDataMapRequest = PutDataMapRequest.create(wearSchemePrefix)
                populateDataMap(putDataMapRequest.dataMap, preferences.all, nextRevision())
                val putDataRequest = putDataMapRequest.asPutDataRequest()
                if (urgent) putDataRequest.setUrgent()
                dataClient.putDataItem(putDataRequest).await()
            }
        }
    }

    private fun nextRevision(): Long = revision.updateAndGet { previous ->
        max(previous + 1L, System.currentTimeMillis())
    }

    internal fun populateDataMap(dataMap: DataMap, values: Map<String, *>, syncRevision: Long) {
        val syncedKeys = ArrayList<String>(values.size)
        for ((key, value) in values) {
            if (value != null && putIntoDataMap(dataMap, key, value)) {
                syncedKeys += key
            }
        }
        syncedKeys.sort()
        dataMap.putStringArrayList(SYNC_KEYS_KEY, syncedKeys)
        dataMap.putLong(SYNC_REVISION_KEY, syncRevision)
    }

    /** Returns true only when [value] was encoded and should be part of the synced-key inventory. */
    private fun putIntoDataMap(dataMap: DataMap, key: String, value: Any): Boolean {
        when (value) {
            is Int -> dataMap.putInt(key, value)
            is Long -> dataMap.putLong(key, value)
            is Boolean -> dataMap.putBoolean(key, value)
            is Float -> dataMap.putFloat(key, value)
            is String -> dataMap.putString(key, value)
            is Set<*> -> {
                if (value.all { it is String }) {
                    val strings = ArrayList(value.filterIsInstance<String>())
                    strings.sort()
                    dataMap.putStringArrayList(key, strings)
                } else {
                    Timber.w("putIntoDataMap: Unsupported Set element type for %s", key)
                    return false
                }
            }
            else -> {
                Timber.w("putIntoDataMap: Unknown type of the object: " + value.javaClass.name)
                return false
            }
        }
        return true
    }
}
