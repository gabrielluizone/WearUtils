package com.matejdro.wearutils.preferencesync;

import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Service that implements receiver part of the preference syncing between phone and watch.
 *
 * To use, create implementation and add it to manifest:
 *
 * {@code         <service android:name=".PreferenceReceiverService"
                        tools:ignore="ExportedService">
                        <intent-filter>
                            <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
                            <data android:scheme="wear" android:host="*" android:pathPrefix="/Settings" />
                        </intent-filter>
                    </service>
   }
 */
public abstract class PreferenceReceiverService extends WearableListenerService
{
    private static final String TAG = "PreferenceReceiver";
    private static final String SYNC_STATE_PREFERENCES = "wearutils.preference_sync_state";

    private final String preferencesPrefix;

    public PreferenceReceiverService(String preferencesPrefix)
    {
        this.preferencesPrefix = preferencesPrefix;
    }

    protected abstract SharedPreferences.Editor getDestinationPreferences();

    /** Called only after a matching snapshot has been committed to disk. */
    protected void onPreferencesCommitted()
    {
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer)
    {
        SharedPreferences.Editor preferenceEditor = null;
        Set<String> latestSyncedKeys = null;

        for (DataEvent dataEvent : dataEventBuffer)
        {
            if (dataEvent.getType() != DataEvent.TYPE_CHANGED)
                continue;

            DataItem dataItem = dataEvent.getDataItem();
            if (!preferencesPrefix.equals(dataItem.getUri().getPath()))
                continue;

            DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
            preferenceEditor = getDestinationPreferences();
            latestSyncedKeys = readSyncedKeys(dataMap);

            // Only remove keys that a previous phone snapshot declared as phone-owned. This keeps
            // watch-local state intact while allowing a reset/remove on the phone to propagate.
            Set<String> previouslySyncedKeys = getSharedPreferences(
                    SYNC_STATE_PREFERENCES, MODE_PRIVATE)
                    .getStringSet(preferencesPrefix, Collections.emptySet());
            for (String oldKey : previouslySyncedKeys)
            {
                if (!latestSyncedKeys.contains(oldKey))
                    preferenceEditor.remove(oldKey);
            }

            for (String key : dataMap.keySet())
            {
                if (PreferencePusher.SYNC_REVISION_KEY.equals(key) ||
                        PreferencePusher.SYNC_KEYS_KEY.equals(key))
                    continue;
                putIntoSharedPreferences(preferenceEditor, key, dataMap.get(key));
            }
        }

        if (preferenceEditor == null || latestSyncedKeys == null)
            return;

        // WearableListenerService may be torn down immediately after this callback. commit()
        // makes the snapshot durable before we publish it to live UI observers.
        if (!preferenceEditor.commit())
        {
            Log.w(TAG, "Could not commit received preference snapshot");
            return;
        }

        boolean inventoryCommitted = getSharedPreferences(SYNC_STATE_PREFERENCES, MODE_PRIVATE)
                .edit()
                .putStringSet(preferencesPrefix, new HashSet<>(latestSyncedKeys))
                .commit();
        if (!inventoryCommitted)
            Log.w(TAG, "Could not commit preference sync inventory");
        onPreferencesCommitted();
    }

    private static Set<String> readSyncedKeys(DataMap dataMap)
    {
        ArrayList<String> inventory = dataMap.getStringArrayList(PreferencePusher.SYNC_KEYS_KEY);
        if (inventory != null)
            return new HashSet<>(inventory);

        // Backward compatibility with snapshots produced before the explicit inventory existed.
        Set<String> keys = new HashSet<>(dataMap.keySet());
        keys.remove(PreferencePusher.SYNC_REVISION_KEY);
        keys.remove(PreferencePusher.SYNC_KEYS_KEY);
        return keys;
    }

    private static void putIntoSharedPreferences(SharedPreferences.Editor editor, String key, Object value)
    {
        if (value instanceof Integer)
            editor.putInt(key, (Integer) value);
        else if (value instanceof Long)
            editor.putLong(key, (Long) value);
        else if (value instanceof Boolean)
            editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Float)
            editor.putFloat(key, (Float) value);
        else if (value instanceof String)
            editor.putString(key, (String) value);
        else if (value instanceof ArrayList)
        {
            ArrayList<?> list = (ArrayList<?>) value;
            Set<String> strings = new HashSet<>();
            for (Object element : list)
            {
                if (!(element instanceof String))
                {
                    Log.w(TAG, "putIntoSharedPreferences: Unsupported list element for " + key);
                    return;
                }
                strings.add((String) element);
            }
            editor.putStringSet(key, strings);
        }
        else
            Log.w(TAG, "putIntoSharedPreferences: Unknown type of the object: " + value.getClass().getName());
    }
}
