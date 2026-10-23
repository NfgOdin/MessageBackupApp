package com.messagebackup.app.domain.mesh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.messagebackup.app.MainActivity
import com.messagebackup.app.MessageBackupApp
import com.messagebackup.app.data.local.RavenDatabaseHelper
import kotlin.math.sin

/**
 * Manages notifications, heads-up alerts, incoming chimes, and sent confirmation ticks
 * for "The Unkindness" Tactical Raven Mesh.
 */
class RavenNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "raven_mesh_messages"
        const val CHANNEL_NAME = "The Unkindness Messages"
        const val RADIO_CHANNEL_ID = "raven_radio_service"
        const val RADIO_CHANNEL_NAME = "Raven Tactical Radio"

        @Volatile
        private var instance: RavenNotificationHelper? = null

        fun getInstance(context: Context): RavenNotificationHelper {
            return instance ?: synchronized(this) {
                instance ?: RavenNotificationHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // High priority channel for instant text & voice note alerts
            val messageChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Direct messages, parcels, and voice transmissions from your Sworn Ravens"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 140, 70, 140)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(messageChannel)

            // Low priority channel for persistent background Walkie-Talkie Radio monitoring
            val radioChannel = NotificationChannel(
                RADIO_CHANNEL_ID,
                RADIO_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains persistent background radio connectivity for PTT walkie-talkie"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(radioChannel)
        }
    }

    /**
     * Posts a heads-up notification with sound, vibration, and tap-to-open when an incoming message arrives.
     */
    fun postIncomingMessageNotification(
        senderCallsign: String,
        senderDisplayName: String,
        messageText: String,
        messageType: String = "TEXT"
    ) {
        val isSoundEnabled = try {
            MessageBackupApp.instance.appPreferences.ravenNotificationSound.value
        } catch (_: Exception) {
            true
        }
        if (!isSoundEnabled) return

        val normalizedId = RavenDatabaseHelper.normalizePeerId(senderCallsign)
        val notificationId = (normalizedId.hashCode() and 0x7FFFFFFF)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.messagebackup.app.ACTION_OPEN_RAVEN_CHAT"
            putExtra("extra_raven_peer_id", normalizedId)
            putExtra("extra_raven_display_name", senderDisplayName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val displayText = when (messageType) {
            "VOICE" -> "🎙️ Voice Transmission received"
            "IMAGE" -> "📷 Photo parcel received"
            "FILE" -> "📎 File parcel: $messageText"
            "LOCATION" -> "📍 Tactical coordinates shared"
            else -> messageText.ifBlank { "New transmission received" }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("🦅 $senderDisplayName")
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Synthesizes and plays a subtle tactical audio tick when a message is successfully dispatched.
     */
    fun playSentTick() {
        try {
            val sampleRate = 16000
            val durationMs = 30
            val numSamples = (durationMs * sampleRate) / 1000
            val sample = ShortArray(numSamples)
            val frequency = 1450.0

            for (i in 0 until numSamples) {
                val angle = 2.0 * Math.PI * i / (sampleRate / frequency)
                val decay = 1.0 - (i.toDouble() / numSamples)
                sample[i] = (sin(angle) * Short.MAX_VALUE * 0.18f * decay).toInt().toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(sample, 0, numSamples)
            audioTrack.play()
        } catch (_: Exception) {}
    }
}
