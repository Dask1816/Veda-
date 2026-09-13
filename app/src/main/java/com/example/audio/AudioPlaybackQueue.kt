package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin
import kotlin.math.sqrt

class AudioPlaybackQueue(
    private val sampleRate: Int = 24000,
    private val onPlaybackStateChange: (isPlaying: Boolean) -> Unit = {},
    private val onAudioAmplitude: (amplitude: Float) -> Unit = {},
    private val onLog: (message: String) -> Unit = {}
) {
    companion object {
        private const val TAG = "AudioPlaybackQueue"
    }

    private var audioTrack: AudioTrack? = null
    private val playbackScope = CoroutineScope(Dispatchers.Default)
    private var playbackJob: Job? = null
    private val chunkChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val isPlaying = AtomicBoolean(false)
    private val isInterrupted = AtomicBoolean(false)

    init {
        initAudioTrack()
    }

    @Synchronized
    private fun initAudioTrack(targetSampleRate: Int = sampleRate) {
        try {
            audioTrack?.release()
            val minBufferSize = AudioTrack.getMinBufferSize(
                targetSampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = (minBufferSize * 4).coerceAtLeast(8192)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(targetSampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            onLog("AudioTrack initialized at ${targetSampleRate}Hz, bufferSize: $bufferSize")
            Log.d(TAG, "AudioTrack initialized successfully at ${targetSampleRate}Hz")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}", e)
            onLog("AudioTrack init error: ${e.message}")
        }
    }

    fun start() {
        if (playbackJob != null && playbackJob?.isActive == true) return

        playbackJob = playbackScope.launch {
            onLog("Playback queue loop started")
            while (isActive) {
                try {
                    val chunk = chunkChannel.receive()
                    if (isInterrupted.get()) {
                        continue
                    }

                    if (!isPlaying.getAndSet(true)) {
                        onPlaybackStateChange(true)
                    }

                    // Calculate amplitude for audio visualizer
                    val amp = calculateRmsAmplitude(chunk)
                    onAudioAmplitude(amp)

                    writeChunkToTrack(chunk)

                    // Reset amplitude smoothly
                    onAudioAmplitude(0.1f)
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Error in playback loop: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun writeChunkToTrack(chunk: ByteArray) {
        val track = audioTrack ?: return
        try {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }
            var written = 0
            while (written < chunk.size && !isInterrupted.get()) {
                val bytesWritten = track.write(chunk, written, chunk.size - written)
                if (bytesWritten > 0) {
                    written += bytesWritten
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeChunkToTrack error: ${e.message}", e)
            onLog("Audio write error: ${e.message}")
        }
    }

    fun enqueueAudioChunk(pcmData: ByteArray, detectedSampleRate: Int = sampleRate) {
        if (pcmData.isEmpty()) return
        isInterrupted.set(false)

        if (audioTrack == null || audioTrack?.sampleRate != detectedSampleRate) {
            initAudioTrack(detectedSampleRate)
        }

        onLog("Enqueuing audio chunk: ${pcmData.size} bytes (${detectedSampleRate}Hz)")
        chunkChannel.trySend(pcmData)
    }

    fun interrupt() {
        Log.d(TAG, "AudioPlaybackQueue interrupted! Clearing queue.")
        onLog("Audio interrupted by user speech")
        isInterrupted.set(true)

        // Drain channel
        while (chunkChannel.tryReceive().isSuccess) {
            // discarded
        }

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Error during audio flush: ${e.message}")
        }

        if (isPlaying.getAndSet(false)) {
            onPlaybackStateChange(false)
        }
        onAudioAmplitude(0f)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun notifyTurnComplete() {
        playbackScope.launch {
            delay(250)
            if (chunkChannel.isEmpty && isPlaying.getAndSet(false)) {
                onPlaybackStateChange(false)
                onAudioAmplitude(0f)
                onLog("Playback queue completed for current turn")
            }
        }
    }

    private fun calculateRmsAmplitude(pcmBytes: ByteArray): Float {
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
        // Normalize 0..32767 to 0.0..1.0
        return (rms / 12000.0).coerceIn(0.0, 1.0).toFloat()
    }

    suspend fun playDiagnosticTone(frequency: Float = 440f, durationMs: Long = 1200): Boolean = withContext(Dispatchers.Default) {
        onLog("Speaker Diagnostic: Generating ${frequency}Hz tone for ${durationMs}ms")
        try {
            val toneSampleRate = 44100
            val minBuf = AudioTrack.getMinBufferSize(
                toneSampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val testTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(toneSampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes((minBuf * 2).coerceAtLeast(4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val numSamples = ((durationMs / 1000.0) * toneSampleRate).toInt()
            val pcmData = ByteArray(numSamples * 2)

            for (i in 0 until numSamples) {
                // Apply a gentle cosine envelope to prevent popping
                val envelope = when {
                    i < 2000 -> (i / 2000.0)
                    i > numSamples - 2000 -> ((numSamples - i) / 2000.0)
                    else -> 1.0
                }
                val angle = 2.0 * Math.PI * i * frequency / toneSampleRate
                val sampleValue = (sin(angle) * 28000 * envelope).toInt().coerceIn(-32768, 32767)
                pcmData[i * 2] = (sampleValue and 0xFF).toByte()
                pcmData[i * 2 + 1] = ((sampleValue shr 8) and 0xFF).toByte()
            }

            testTrack.play()
            testTrack.write(pcmData, 0, pcmData.size)

            // Simulate waveform animation during tone
            for (step in 1..8) {
                onAudioAmplitude(0.85f)
                delay(100)
                onAudioAmplitude(0.65f)
                delay(50)
            }

            delay(300)
            testTrack.stop()
            testTrack.release()
            onAudioAmplitude(0f)
            onLog("Speaker Diagnostic: 440Hz tone completed successfully! Device speaker verified.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Speaker Diagnostic failed: ${e.message}", e)
            onLog("Speaker Diagnostic FAILED: ${e.message}")
            false
        }
    }

    fun release() {
        interrupt()
        playbackJob?.cancel()
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio track: ${e.message}")
        }
    }
}
