package com.matejdro.wearutils.companionnotice

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.google.android.wearable.intent.RemoteIntent
import com.matejdro.wearutils.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Phone-side counterpart of [WearCompanionWatchActivity]: shows a dialog nudging the user to
 * install the watch app if the paired watch doesn't advertise it.
 *
 * This used to run on the legacy blocking `GoogleApiClient` + `CapabilityApi.getCapability(...)
 * .setResultCallback(...)` API (pre-2019). That API is long deprecated and, on current Play
 * Services builds, `GoogleApiClient.connect()` frequently never calls `onConnected()` for the
 * Wearable API - so the check silently never resolved and the phone could never confirm the
 * watch app was there, even when it plainly was. Rewritten to the modern Task-based
 * [CapabilityClient], mirroring [WearCompanionWatchActivity]'s retry loop: a fresh install/pairing
 * or a watch waking from idle can take a beat for the Data Layer to resync, so one immediate
 * empty result isn't proof the watch app is missing.
 */
abstract class WearCompanionPhoneActivity : AppCompatActivity() {
    companion object {
        private const val CAPABILITY_CHECK_ATTEMPTS = 4
        private const val CAPABILITY_CHECK_RETRY_DELAY_MS = 1500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val capabilityClient = Wearable.getCapabilityClient(this@WearCompanionPhoneActivity)

            var installedOnWatch = false
            var gotDefinitiveAnswer = false
            for (attempt in 1..CAPABILITY_CHECK_ATTEMPTS) {
                try {
                    val matchingCapabilities = capabilityClient.getCapability(
                            getWatchAppPresenceCapability(),
                            CapabilityClient.FILTER_ALL
                    ).await()

                    gotDefinitiveAnswer = true
                    installedOnWatch = matchingCapabilities.nodes.isNotEmpty()
                } catch (e: Exception) {
                    // Play Services can throw here (e.g. Wearable.API is not available on this device).
                    // This check is purely a user convenience - it must never crash the app.
                    Timber.w(e, "Watch app capability check failed (attempt %d)", attempt)
                }

                if (installedOnWatch || attempt == CAPABILITY_CHECK_ATTEMPTS) {
                    break
                }

                delay(CAPABILITY_CHECK_RETRY_DELAY_MS)
            }

            if (gotDefinitiveAnswer) {
                onWatchAppInstalledResult(installedOnWatch)
            }
        }
    }

    protected open fun onWatchAppInstalledResult(watchAppInstalled: Boolean) {
        if (watchAppInstalled) {
            return
        }

        AlertDialog.Builder(this)
                .setTitle(R.string.no_watch_app_title)
                .setMessage(R.string.no_watch_app_description)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.no_watch_app_button) { _: DialogInterface, _: Int ->
                    openWatchPlayStorePage()
                }
                .show()
    }

    protected open fun openWatchPlayStorePage() {
        val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            data = Uri.parse("market://details?id=$packageName")
        }

        try {
            RemoteIntent.startRemoteActivity(this, playStoreIntent, null)
        } catch (e: Exception) {
            Timber.e(e, "Activity start crash")
        }
    }

    abstract fun getWatchAppPresenceCapability(): String
}
