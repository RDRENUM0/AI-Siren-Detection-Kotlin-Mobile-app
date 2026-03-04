package com.example.sirenfinal

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlin.concurrent.thread

/**
 * Prosty AudioRecorder, który co chwilę zwraca ShortArray z Mikrofonu.
 */
class AudioRecorder(private val onDataReady: (ShortArray) -> Unit) {
    private val sampleRate = 44100
    private val bufferSize: Int =
        AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
    private var isRecording = false
    private lateinit var recorder: AudioRecord

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        recorder.startRecording()
        isRecording = true

        thread(start = true) {
            val audioBuffer = ShortArray(bufferSize)
            while (isRecording) {
                val readCount = recorder.read(audioBuffer, 0, bufferSize)
                if (readCount > 0) {
                    onDataReady(audioBuffer.copyOf(readCount))
                }
            }
        }
    }

    fun stop() {
        isRecording = false
        recorder.stop()
        recorder.release()
    }
}
