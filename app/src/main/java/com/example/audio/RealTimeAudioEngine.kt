package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.example.core.config.AlexConfig
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "RealTimeAudioEngine"
private const val WAVEFORM_BAR_COUNT = 24

class RealTimeAudioEngine(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private var engineScope: CoroutineScope? = null
    private var recordJob: Job? = null
    private var playbackJob: Job? = null

    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()
    private val isRecordingActive = AtomicBoolean(false)
    private val isPlaybackActive = AtomicBoolean(false)

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isModelPlayingAudio = MutableStateFlow(false)
    val isModelPlayingAudio: StateFlow<Boolean> = _isModelPlayingAudio.asStateFlow()

    private val _isUserSpeakingVad = MutableStateFlow(false)
    val isUserSpeakingVad: StateFlow<Boolean> = _isUserSpeakingVad.asStateFlow()

    private val _inputAmplitude = MutableStateFlow(0f)
    val inputAmplitude: StateFlow<Float> = _inputAmplitude.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    private val _waveformBars = MutableStateFlow(List(WAVEFORM_BAR_COUNT) { 0.08f })
    val waveformBars: StateFlow<List<Float>> = _waveformBars.asStateFlow()

    private var vadSensitivity: Float = 0.55f
    private var autoBargeInEnabled: Boolean = true
    private var consecutiveSpeechFrames = 0

    var onUserBargeInDetected: (() -> Unit)? = null
    var onVadUtteranceCompleted: (suspend (ByteArray) -> Unit)? = null

    fun updateAudioSettings(
        vadSensitivity: Float,
        autoBargeIn: Boolean
    ) {
        this.vadSensitivity = vadSensitivity.coerceIn(0.2f, 0.9f)
        this.autoBargeInEnabled = autoBargeIn
    }

    fun setMicMuted(muted: Boolean) {
        _isMicMuted.value = muted
        if (muted) {
            _inputAmplitude.value = 0f
            _isUserSpeakingVad.value = false
            updateWaveformFromLevel(0f, isSpeaking = false)
        }
    }

    fun toggleMicMute(): Boolean {
        val next = !_isMicMuted.value
        setMicMuted(next)
        return next
    }

    @SuppressLint("MissingPermission")
    fun startFullDuplexStreaming(
        enableEchoCancellation: Boolean = true,
        enableNoiseSuppression: Boolean = true,
        onPcmChunkCaptured: suspend (ByteArray) -> Unit,
        onAudioError: (String) -> Unit
    ) {
        stopFullDuplexStreaming()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        engineScope = scope

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
            }

            val minRecordBuffer = AudioRecord.getMinBufferSize(
                AlexConfig.INPUT_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minRecordBuffer <= 0) {
                onAudioError("Unsupported device microphone configuration (16kHz PCM Mono).")
                return
            }

            val recordBufferSize = (minRecordBuffer * 2).coerceAtLeast(3200)
            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(AlexConfig.INPUT_SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(recordBufferSize)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                onAudioError("Microphone initialization failed. Please check microphone permissions.")
                return
            }

            val sessionId = record.audioSessionId
            if (enableEchoCancellation && AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                }
            }
            if (enableNoiseSuppression && NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                }
            }

            val minTrackBuffer = AudioTrack.getMinBufferSize(
                AlexConfig.OUTPUT_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4800)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(AlexConfig.OUTPUT_SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minTrackBuffer * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioRecord = record
            audioTrack = track

            isRecordingActive.set(true)
            isPlaybackActive.set(true)

            record.startRecording()
            track.play()

            // Continuous Microphone Capture + Real-Time VAD Utterance Segmentation
            recordJob = scope.launch {
                val chunkBytes = ByteArray(1600) // 50ms of 16kHz 16-bit mono PCM
                val utterancePcmStream = ByteArrayOutputStream()
                var activeSpeechFramesInUtterance = 0
                var silenceFramesAfterSpeech = 0

                while (isActive && isRecordingActive.get()) {
                    if (_isMicMuted.value) {
                        utterancePcmStream.reset()
                        activeSpeechFramesInUtterance = 0
                        silenceFramesAfterSpeech = 0
                        delay(40)
                        continue
                    }

                    val readCount = record.read(chunkBytes, 0, chunkBytes.size)
                    if (readCount > 0) {
                        val validBytes = if (readCount == chunkBytes.size) {
                            chunkBytes.copyOf()
                        } else {
                            chunkBytes.copyOfRange(0, readCount)
                        }

                        val rms = computeNormalizedRms(validBytes)
                        _inputAmplitude.value = rms

                        val threshold = (0.18f - (vadSensitivity * 0.13f)).coerceIn(0.03f, 0.20f)
                        val isVoiceFrame = rms > threshold

                        if (isVoiceFrame) {
                            consecutiveSpeechFrames++
                            silenceFramesAfterSpeech = 0
                            activeSpeechFramesInUtterance++
                            utterancePcmStream.write(validBytes)

                            if (consecutiveSpeechFrames >= 2) {
                                _isUserSpeakingVad.value = true
                                if (autoBargeInEnabled && _isModelPlayingAudio.value && consecutiveSpeechFrames >= 3) {
                                    flushPlaybackForBargeIn()
                                    onUserBargeInDetected?.invoke()
                                }
                            }
                        } else {
                            consecutiveSpeechFrames = 0
                            if (activeSpeechFramesInUtterance > 0) {
                                silenceFramesAfterSpeech++
                                // Keep trailing 350ms of natural pause in the utterance buffer
                                if (silenceFramesAfterSpeech <= 7) {
                                    utterancePcmStream.write(validBytes)
                                }
                                // Natural turn end detected after ~800ms of pause following >= 350ms of speech
                                if (silenceFramesAfterSpeech >= 16) {
                                    _isUserSpeakingVad.value = false
                                    if (activeSpeechFramesInUtterance >= 7) {
                                        val rawPcm = utterancePcmStream.toByteArray()
                                        utterancePcmStream.reset()
                                        activeSpeechFramesInUtterance = 0
                                        silenceFramesAfterSpeech = 0
                                        val wavBytes = pcmToWavBytes(rawPcm, AlexConfig.INPUT_SAMPLE_RATE_HZ)
                                        onVadUtteranceCompleted?.invoke(wavBytes)
                                    } else {
                                        utterancePcmStream.reset()
                                        activeSpeechFramesInUtterance = 0
                                        silenceFramesAfterSpeech = 0
                                    }
                                }
                            } else {
                                _isUserSpeakingVad.value = false
                            }
                        }

                        // Cap single continuous utterance at ~12 seconds to maintain low latency
                        if (activeSpeechFramesInUtterance >= 240) {
                            val rawPcm = utterancePcmStream.toByteArray()
                            utterancePcmStream.reset()
                            activeSpeechFramesInUtterance = 0
                            silenceFramesAfterSpeech = 0
                            _isUserSpeakingVad.value = false
                            val wavBytes = pcmToWavBytes(rawPcm, AlexConfig.INPUT_SAMPLE_RATE_HZ)
                            onVadUtteranceCompleted?.invoke(wavBytes)
                        }

                        if (!_isModelPlayingAudio.value) {
                            updateWaveformFromPcm(validBytes, rms)
                        }

                        onPcmChunkCaptured(validBytes)
                    } else {
                        delay(15)
                    }
                }
            }

            // Continuous AudioTrack Playback Coroutine
            playbackJob = scope.launch {
                while (isActive && isPlaybackActive.get()) {
                    val nextChunk = playbackQueue.poll()
                    if (nextChunk != null) {
                        _isModelPlayingAudio.value = true
                        val rms = computeNormalizedRms(nextChunk)
                        _outputAmplitude.value = rms
                        updateWaveformFromPcm(nextChunk, rms)

                        try {
                            track.write(nextChunk, 0, nextChunk.size)
                        } catch (e: Exception) {
                            Log.w(TAG, "AudioTrack write warning: ${e.message}")
                        }
                    } else {
                        if (_isModelPlayingAudio.value) {
                            _isModelPlayingAudio.value = false
                            _outputAmplitude.value = 0f
                        }
                        delay(15)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start full-duplex audio engine", e)
            stopFullDuplexStreaming()
            onAudioError(e.localizedMessage ?: "Failed to initialize real-time audio hardware.")
        }
    }

    /**
     * Wraps raw 16-bit mono little-endian PCM bytes in a standard 44-byte RIFF/WAV header.
     */
    fun pcmToWavBytes(pcmData: ByteArray, sampleRate: Int = AlexConfig.INPUT_SAMPLE_RATE_HZ): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val totalDataLen = pcmData.size + 36

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM chunk size
        header.putShort(1.toShort()) // AudioFormat = 1 (PCM)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmData.size)

        val wavBytes = ByteArray(44 + pcmData.size)
        System.arraycopy(header.array(), 0, wavBytes, 0, 44)
        System.arraycopy(pcmData, 0, wavBytes, 44, pcmData.size)
        return wavBytes
    }

    fun enqueueModelAudioChunk(pcmBytes: ByteArray) {
        if (pcmBytes.isNotEmpty() && isPlaybackActive.get()) {
            playbackQueue.offer(pcmBytes)
        }
    }

    fun flushPlaybackForBargeIn() {
        playbackQueue.clear()
        _isModelPlayingAudio.value = false
        _outputAmplitude.value = 0f
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    track.play()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error flushing AudioTrack on barge-in: ${e.message}")
        }
    }

    fun updateWaveformFromLevel(level: Float, isSpeaking: Boolean) {
        val clamped = level.coerceIn(0f, 1f)
        if (!isSpeaking || clamped <= 0.02f) {
            _waveformBars.value = List(WAVEFORM_BAR_COUNT) { 0.08f }
            return
        }
        val phase = (System.currentTimeMillis() % 1000L) / 1000f * 6.283f
        _waveformBars.value = List(WAVEFORM_BAR_COUNT) { idx ->
            val centerWeight = 1f - abs((idx - WAVEFORM_BAR_COUNT / 2f) / (WAVEFORM_BAR_COUNT / 2f)) * 0.55f
            val wave = abs(sin(phase + idx * 0.45f)) * 0.6f + abs(cos(phase * 0.7f - idx * 0.3f)) * 0.4f
            (0.10f + clamped * centerWeight * wave).coerceIn(0.08f, 0.98f)
        }
    }

    private fun updateWaveformFromPcm(pcmBytes: ByteArray, rms: Float) {
        if (pcmBytes.size < WAVEFORM_BAR_COUNT * 2) {
            updateWaveformFromLevel(rms, rms > 0.02f)
            return
        }
        val shortCount = pcmBytes.size / 2
        val shortsPerBar = (shortCount / WAVEFORM_BAR_COUNT).coerceAtLeast(1)
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        val bars = MutableList(WAVEFORM_BAR_COUNT) { 0.08f }
        for (barIndex in 0 until WAVEFORM_BAR_COUNT) {
            var sumSq = 0.0
            val startIdx = barIndex * shortsPerBar
            val endIdx = (startIdx + shortsPerBar).coerceAtMost(shortCount)
            for (i in startIdx until endIdx) {
                val sample = buffer.get(i).toDouble() / 32768.0
                sumSq += sample * sample
            }
            val count = (endIdx - startIdx).coerceAtLeast(1)
            val barRms = sqrt(sumSq / count).toFloat()
            bars[barIndex] = (0.08f + (barRms * 3.8f)).coerceIn(0.08f, 0.98f)
        }
        _waveformBars.value = bars
    }

    fun computeNormalizedRms(pcmBytes: ByteArray): Float {
        if (pcmBytes.size < 2) return 0f
        val shortBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val count = shortBuffer.remaining()
        if (count == 0) return 0f
        var sumSquares = 0.0
        for (i in 0 until count) {
            val normalized = shortBuffer.get(i).toDouble() / 32768.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / count).toFloat()
        return (rms * 3.5f).coerceIn(0f, 1f)
    }

    fun stopFullDuplexStreaming() {
        isRecordingActive.set(false)
        isPlaybackActive.set(false)
        playbackQueue.clear()

        recordJob?.cancel()
        playbackJob?.cancel()
        engineScope?.cancel()
        recordJob = null
        playbackJob = null
        engineScope = null

        try {
            echoCanceler?.release()
        } catch (_: Exception) {
        }
        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {
        }
        echoCanceler = null
        noiseSuppressor = null

        try {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
                rec.release()
            }
        } catch (_: Exception) {
        }
        audioRecord = null

        try {
            audioTrack?.let { trk ->
                if (trk.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    trk.pause()
                    trk.flush()
                    trk.stop()
                }
                trk.release()
            }
        } catch (_: Exception) {
        }
        audioTrack = null

        _isModelPlayingAudio.value = false
        _isUserSpeakingVad.value = false
        _inputAmplitude.value = 0f
        _outputAmplitude.value = 0f
        _waveformBars.value = List(WAVEFORM_BAR_COUNT) { 0.08f }
    }
}
