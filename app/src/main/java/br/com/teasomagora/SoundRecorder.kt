package br.com.teasomagora

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

internal enum class CaptureProblem { UNAVAILABLE, INPUT_CHANGED, CLIPPED }

internal class CaptureException(val problem: CaptureProblem) : Exception()

internal class SoundRecorder(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val source =
        if (audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }

    @SuppressLint("MissingPermission") // The activity checks permission before starting.
    suspend fun measure(onSecondsLeft: suspend (Int) -> Unit): SoundReading =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(13_000) { capture(onSecondsLeft) }
                ?: throw CaptureException(CaptureProblem.UNAVAILABLE)
        }

    @SuppressLint("MissingPermission")
    private suspend fun capture(onSecondsLeft: suspend (Int) -> Unit): SoundReading {
        val sampleRate = 48_000
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
        if (bufferSize <= 0) throw CaptureException(CaptureProblem.UNAVAILABLE)

        val windowSize = sampleRate / 10
        val recorder = AudioRecord(source, sampleRate, channel, encoding, maxOf(bufferSize, windowSize * 4))
        val buffer = ShortArray(windowSize)
        val summary = SoundWindows(windowSize)
        val targetSamples = sampleRate * MEASUREMENT_SECONDS
        var inputId: Int? = null
        var lastSeconds = MEASUREMENT_SECONDS

        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw CaptureException(CaptureProblem.UNAVAILABLE)
            }
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw CaptureException(CaptureProblem.UNAVAILABLE)
            }

            while (summary.samples < targetSamples) {
                coroutineContext.ensureActive()
                val count = recorder.read(
                    buffer, 0, minOf(buffer.size, targetSamples - summary.samples), AudioRecord.READ_NON_BLOCKING,
                )
                if (count < 0) throw CaptureException(CaptureProblem.UNAVAILABLE)
                if (count == 0) {
                    delay(20)
                    continue
                }
                if (Build.VERSION.SDK_INT >= 29 &&
                    recorder.activeRecordingConfiguration?.isClientSilenced == true
                ) {
                    throw CaptureException(CaptureProblem.UNAVAILABLE)
                }
                val routedId = recorder.routedDevice?.id
                if (inputId != null && routedId != inputId) {
                    throw CaptureException(CaptureProblem.INPUT_CHANGED)
                }
                inputId = routedId

                summary.add(buffer, count)
                buffer.fill(0, 0, count)
                val remaining = (targetSamples - summary.samples + sampleRate - 1) / sampleRate
                if (remaining in 1 until lastSeconds) {
                    lastSeconds = remaining
                    onSecondsLeft(remaining)
                }
            }
            if (summary.isClipped) throw CaptureException(CaptureProblem.CLIPPED)
            val level = summary.level() ?: throw CaptureException(CaptureProblem.UNAVAILABLE)
            return SoundReading(level, inputId)
        } finally {
            buffer.fill(0)
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } finally {
                recorder.release()
            }
        }
    }
}
