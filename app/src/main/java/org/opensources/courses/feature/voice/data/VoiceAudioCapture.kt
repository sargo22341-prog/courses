package org.opensources.courses.feature.voice.data

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** One bounded, in-memory recording. Published samples are immutable for the decoder. */
internal class VoiceAudioCapture {
    val samples = ShortArray(SAMPLE_RATE * MAX_SECONDS)
    val count = AtomicInteger(0)
    val capturing = AtomicBoolean(true)
    val finished = AtomicBoolean(false)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record(onFinished: () -> Unit) {
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) throw IOException("Microphone format unavailable")
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum, SAMPLE_RATE / 2))
            .build()
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED)
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            capture(recorder)
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } finally {
                recorder.release()
                finished.set(true)
                onFinished()
            }
        }
    }

    private suspend fun capture(recorder: AudioRecord) {
        val deadline = SystemClock.elapsedRealtime() + MAX_SECONDS * 1_000
        while (capturing.get() && count.get() < samples.size && SystemClock.elapsedRealtime() < deadline) {
            currentCoroutineContext().ensureActive()
            val offset = count.get()
            val read = recorder.read(samples, offset, minOf(1_600, samples.size - offset), AudioRecord.READ_NON_BLOCKING)
            if (read < 0) throw IOException("Microphone read failed")
            if (read > 0) count.addAndGet(read) else delay(10)
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 60
    }
}
