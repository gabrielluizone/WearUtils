package com.matejdro.wearutils.companionnotice

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

abstract class WearCompanionWatchActivity : AppCompatActivity() {
    companion object {
        private const val CAPABILITY_CHECK_ATTEMPTS = 4
        private const val CAPABILITY_CHECK_RETRY_DELAY_MS = 1500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val capabilityClient = Wearable.getCapabilityClient(this@WearCompanionWatchActivity)

            var installedOnWatch = false
            for (attempt in 1..CAPABILITY_CHECK_ATTEMPTS) {
                val matchingCapabilities = capabilityClient.getCapability(
                    getPhoneAppPresenceCapability(),
                    CapabilityClient.FILTER_ALL
                ).await()

                installedOnWatch = matchingCapabilities.nodes.isNotEmpty()
                if (installedOnWatch || attempt == CAPABILITY_CHECK_ATTEMPTS) {
                    break
                }

                // Right after the watch wakes up from a long idle/off period, the Wearable Data
                // Layer connection to the phone may not have re-established yet, so a single
                // capability check can look like the phone app is missing even though it's
                // running fine - retry for a few seconds before concluding it's really gone.
                delay(CAPABILITY_CHECK_RETRY_DELAY_MS)
            }

            onWatchAppInstalledResult(installedOnWatch)
        }
    }


    protected fun onWatchAppInstalledResult(watchAppInstalled: Boolean) {
        if (watchAppInstalled) {
            return
        }
        startActivity(Intent(this, PhoneAppNoticeActivity::class.java))
        finish()
    }

    abstract fun getPhoneAppPresenceCapability(): String
}
