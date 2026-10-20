package com.messagebackup.app.observer

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Real-time ContentObserver that detects changes to Android's native SMS and MMS telephony content provider.
 * Uses a debounce delay (default 1000ms) to consolidate rapid multi-part or streaming database mutations
 * before firing the change callback.
 */
class TelephonyContentObserver(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 1000L,
    private val onChangeDetected: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var debounceJob: Job? = null

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.Main) {
            delay(debounceMs)
            onChangeDetected()
        }
    }

    companion object {
        val SMS_URI: Uri = Telephony.Sms.CONTENT_URI
        val MMS_URI: Uri = Telephony.Mms.CONTENT_URI
        val MMS_SMS_URI: Uri = Uri.parse("content://mms-sms/")
    }
}
