package com.messagebackup.app.domain.mesh

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.sin

/**
 * High-performance audio recording and playback manager for "Mind-to-Mind" PTT walkie-talkie mode.
 * Records compressed voice snippets (AAC in MPEG-4 container or low-bitrate Opus),
 * converts to compact Base64 byte payloads for Raven Mesh transport,
 * and handles speaker playback with tactical walkie-talkie squelch/chime tones.
 */
class RavenVoicePttEngine(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var activeRecordingFile: File? = null
    private var recordingStartTimeMs: Long = 0L

    /**
     * Starts recording a voice transmission when the PTT button is held down.
     */
    @Synchronized
    fun startRecording(): Boolean {
        return try {
            val audioDir = File(context.cacheDir, "raven_ptt").apply { mkdirs() }
            val outputFile = File(audioDir, "ptt_tx_${System.currentTimeMillis()}.m4a")
            activeRecordingFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(24_000) // 24 kbps optimized for voice clarity & tiny mesh packet size
                setAudioSamplingRate(16_000)   // 16 kHz wideband voice
                setAudioChannels(1)            // Mono
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            recordingStartTimeMs = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            activeRecordingFile?.delete()
            activeRecordingFile = null
            false
        }
    }

    /**
     * Stops recording and returns the Base64-encoded audio payload and duration in milliseconds.
     * If duration is under 350ms (accidental tap), returns null to prevent empty chirps.
     */
    suspend fun stopRecording(): VoiceSnippet? = withContext(Dispatchers.IO) {
        val recorder = mediaRecorder ?: return@withContext null
        val file = activeRecordingFile ?: return@withContext null

        try {
            recorder.stop()
            recorder.release()
        } catch (e: Exception) {
            // Stop failed (e.g. released too fast)
        } finally {
            mediaRecorder = null
        }

        val duration = System.currentTimeMillis() - recordingStartTimeMs
        if (duration < 350L || !file.exists() || file.length() == 0L) {
            file.delete()
            activeRecordingFile = null
            return@withContext null
        }

        val bytes = file.readBytes()
        val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
        file.delete()
        activeRecordingFile = null

        VoiceSnippet(
            audioBase64 = base64Data,
            durationMs = duration
        )
    }

    /**
     * Cancels an ongoing recording (e.g., user slid finger off the cancel zone).
     */
    @Synchronized
    fun cancelRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        activeRecordingFile?.delete()
        activeRecordingFile = null
    }

    /**
     * Plays an inbound voice transmission directly through the phone's speaker.
     * Prefaces with a tactical walkie-talkie start squelch chime and concludes with a subtle Roger beep.
     */
    suspend fun playInboundVoice(audioBase64: String) = withContext(Dispatchers.IO) {
        try {
            // 1. Play subtle opening squelch tone (600Hz blip, 40ms)
            playTone(frequency = 720, durationMs = 45, volume = 0.25f)

            // 2. Write temp file and play voice payload
            val audioBytes = Base64.decode(audioBase64, Base64.NO_WRAP)
            val tempFile = File(context.cacheDir, "ptt_rx_${System.currentTimeMillis()}.m4a").apply {
                writeBytes(audioBytes)
            }

            val mediaPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                prepare()
            }

            val playbackComplete = kotlinx.coroutines.CompletableDeferred<Unit>()
            mediaPlayer.setOnCompletionListener {
                playbackComplete.complete(Unit)
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                playbackComplete.complete(Unit)
                true
            }

            mediaPlayer.start()
            playbackComplete.await()
            mediaPlayer.release()
            tempFile.delete()

            // 3. Play concluding "Roger" blip (880Hz, 35ms)
            playTone(frequency = 880, durationMs = 35, volume = 0.20f)
        } catch (e: Exception) {
            // Playback error
        }
    }

    /**
     * Synthesizes a clean sine wave audio tone in-memory and outputs through AudioTrack
     * without needing external sound resource assets.
     */
    private fun playTone(frequency: Int, durationMs: Int, volume: Float) {
        try {
            val sampleRate = 16000
            val numSamples = (durationMs * sampleRate) / 1000
            val sample = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val angle = 2.0 * Math.PI * i / (sampleRate / frequency)
                sample[i] = (sin(angle) * Short.MAX_VALUE * volume).toInt().toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
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
            Thread.sleep(durationMs.toLong() + 10L)
            audioTrack.stop()
            audioTrack.release()
        } catch (_: Exception) {}
    }
}

data class VoiceSnippet(
    val audioBase64: String,
    val durationMs: Long
)
