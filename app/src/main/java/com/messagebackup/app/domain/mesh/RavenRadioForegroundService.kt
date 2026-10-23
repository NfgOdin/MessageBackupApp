package com.messagebackup.app.domain.mesh

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.messagebackup.app.MainActivity
import com.messagebackup.app.MessageBackupApp

/**
 * Foreground Service that keeps "The Unkindness" socket listeners, cellular relay,
 * and Boost Mobile / Nextel walkie-talkie audio engine alive when the phone is locked or app minimized.
 */
class RavenRadioForegroundService : Service() {

    companion object {
        const val NOTIFICATION_ID = 87441
        const val ACTION_START_RADIO = "com.messagebackup.app.ACTION_START_RADIO"
        const val ACTION_STOP_RADIO = "com.messagebackup.app.ACTION_STOP_RADIO"
        const val ACTION_UPDATE_CHANNEL = "com.messagebackup.app.ACTION_UPDATE_CHANNEL"
        const val EXTRA_CHANNEL_INDEX = "extra_channel_index"

        fun startService(context: Context, channel: Int = 1) {
            val intent = Intent(context, RavenRadioForegroundService::class.java).apply {
                action = ACTION_START_RADIO
                putExtra(EXTRA_CHANNEL_INDEX, channel)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, RavenRadioForegroundService::class.java).apply {
                action = ACTION_STOP_RADIO
            }
            context.stopService(intent)
        }
    }

    private var currentChannel: Int = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RavenNotificationHelper.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_RADIO

        if (action == ACTION_STOP_RADIO) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        currentChannel = intent?.getIntExtra(EXTRA_CHANNEL_INDEX, currentChannel) ?: currentChannel
        val notification = buildRadioNotification(currentChannel)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Ensure transport and relay are running
        try {
            val transport = RavenSocketTransport.getInstance(this)
            transport.setMeshEnabled(true)
        } catch (_: Exception) {}

        return START_STICKY
    }

    private fun buildRadioNotification(channel: Int): android.app.Notification {
        val channelName = getChannelName(channel)
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.messagebackup.app.ACTION_OPEN_RAVEN_RADIO"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, RavenNotificationHelper.RADIO_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
            .setContentTitle("📻 Raven Tactical Radio • Ch $channel")
            .setContentText("Monitoring $channelName • Boost Mobile PTT Ready")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun getChannelName(channel: Int): String {
        return when (channel) {
            1 -> "Bifrost (Public Hail)"
            2 -> "Valhalla (General)"
            3 -> "Midgard (Squad 1)"
            4 -> "Asgard (Squad 2)"
            else -> "Tactical Frequency $channel"
        }
    }
}
