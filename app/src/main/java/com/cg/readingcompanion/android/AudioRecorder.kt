package com.cg.readingcompanion.android

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class AudioRecorder {
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    private val output = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        stopInternal()
        val min = AudioRecord.getMinBufferSize(
            16_000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (min <= 0) return false
        val bufferSize = maxOf(min * 2, 4096)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                16_000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (_: Throwable) {
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }
        output.reset()
        record = recorder
        running = true
        recorder.startRecording()
        worker = thread(name = "reading-companion-recorder") {
            val buf = ByteArray(bufferSize)
            val started = System.currentTimeMillis()
            while (running && System.currentTimeMillis() - started < 60_000) {
                val n = recorder.read(buf, 0, buf.size)
                if (n > 0) output.write(buf, 0, n)
            }
            if (running) running = false
        }
        return true
    }

    fun stop(): ByteArray {
        running = false
        worker?.join(800)
        worker = null
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
        return output.toByteArray()
    }

    private fun stopInternal() {
        running = false
        worker?.join(300)
        worker = null
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
    }
}
