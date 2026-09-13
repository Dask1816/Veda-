package com.example.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioRecordManager(
    private val context: Context,
    private val sampleRate: Int = 16000,
    private val onAudioChunk: (base64Chunk: String, rawBytes: ByteArray) -> Unit,
    private val onAmplitude: (amplitude: Float) -> Unit,
    private val onUserInterruption: () -> Unit,
    private val onLog: (message: String) -> Unit = {}
) {
    companion object {
        private const val TAG = "AudioRecordManager"
        private const val SAMPLES_PER_CHUNK = 1600 // 100ms at 16000Hz
        private const val BYTES_PER_SAMPLE = 2 // 16-bit PCM
        private const val CHUNK_SIZE_BYTES = SAMPLES_PER_CHUNK * BYTES_PER_SAMPLE // 3200 bytes
        private const val SPEECH_THRESHOLD = 0.12f // RMS threshold for VAD interruption
    }

    private var audioRecord: AudioRecord? = null
    private val recordScope = CoroutineScope(Dispatchers.IO)
    private var recordJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    var isVedaSpeaking: Boolean = false

    fun startRecording(): Boolean {
        if (isRecording.get()) return true

        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onLog("RECORD_AUDIO permission missing")
            Log.e(TAG, "RECORD_AUDIO permission missing")
            return false
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 4).coerceAtLeast(CHUNK_SIZE_BYTES * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Try standard MIC source if VOICE_COMMUNICATION fails
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onLog("AudioRecord failed to initialize")
                Log.e(TAG, "AudioRecord state not initialized")
                return false
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            onLog("Microphone capture started: 16000Hz 16-bit Mono")
            Log.d(TAG, "Microphone capture started successfully")

            recordJob = recordScope.launch {
                val buffer = ByteArray(CHUNK_SIZE_BYTES)
                while (isActive && isRecording.get()) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        val chunkData = buffer.copyOf(bytesRead)
                        val amp = calculateRms(chunkData)
                        onAmplitude(amp)

                        // Check for user interruption if Veda is speaking
                        if (isVedaSpeaking && amp > SPEECH_THRESHOLD) {
                            Log.d(TAG, "Speech detected during Veda speaking! Triggering interruption.")
                            onUserInterruption()
                        }

                        val base64 = Base64.encodeToString(chunkData, Base64.NO_WRAP)
                        onAudioChunk(base64, chunkData)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            onLog("Microphone start failed: ${e.message}")
            stopRecording()
            return false
        }
    }

    fun stopRecording() {
        if (!isRecording.getAndSet(false)) return
        onLog("Microphone capture stopped")
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        onAmplitude(0f)
    }

    private fun calculateRms(pcmBytes: ByteArray): Float {
        if (pcmBytes.isEmpty()) return 0f
        var sumSquares = 0.0
        val numSamples = pcmBytes.size / 2
        for (i in 0 until numSamples) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            sumSquares += (sample * sample).toDouble()
        }
        val rms = sqrt(sumSquares / numSamples)
        return (rms / 9000.0).coerceIn(0.0, 1.0).toFloat()
    }
}
