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
import timber.log.Timber

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
            var gotDefinitiveAnswer = false
            for (attempt in 1..CAPABILITY_CHECK_ATTEMPTS) {
                try {
                    val matchingCapabilities = capabilityClient.getCapability(
                        getPhoneAppPresenceCapability(),
                        CapabilityClient.FILTER_ALL
                    ).await()

                    gotDefinitiveAnswer = true
                    installedOnWatch = matchingCapabilities.nodes.isNotEmpty()
                } catch (e: Exception) {
                    // Play Services can throw here (stale/updating Play Services, transient
                    // Wearable API failures). This check exists purely to show an install
                    // notice - it must never take the whole app down.
                    Timber.w(e, "Phone app capability check failed (attempt %d)", attempt)
                }

                if (installedOnWatch || attempt == CAPABILITY_CHECK_ATTEMPTS) {
                    break
                }

                // Right after the watch wakes up from a long idle/off period, the Wearable Data
                // Layer connection to the phone may not have re-established yet, so a single
                // capability check can look like the phone app is missing even though it's
                // running fine - retry for a few seconds before concluding it's really gone.
                delay(CAPABILITY_CHECK_RETRY_DELAY_MS)
            }

            // Only a *successful* lookup that came back empty means "phone app missing". If
            // every attempt failed we simply don't know - let the app run rather than closing
            // it into the install notice on a false positive.
            if (gotDefinitiveAnswer) {
                onWatchAppInstalledResult(installedOnWatch)
            }
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
