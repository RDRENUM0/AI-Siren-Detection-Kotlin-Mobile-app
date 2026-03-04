package com.example.sirenfinal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import be.tarsos.dsp.util.fft.FFT
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sqrt

/**
 * SpectrumView – rysuje prosty spektrum w postaci pasków.
 * Wejście: ShortArray (surowe próbki PCM), np. ~2048 próbek.
 */
class SpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paintBar = Paint().apply {
        color = 0xFF00FF00.toInt() // jasnozielony
        style = Paint.Style.FILL
    }

    private var spectrum: FloatArray? = null

    /**
     * Przekazujemy fragment audio jako ShortArray.
     * Robimy FFT i liczymy spektrum (magnituda w dB).
     */
    fun updateAudioData(audioData: ShortArray) {
        if (audioData.isEmpty()) {
            spectrum = null
            invalidate()
            return
        }


        val floatData = FloatArray(audioData.size) { i ->
            audioData[i] / 32768.0f
        }


        var fftSize = 1
        while (fftSize < floatData.size) fftSize = fftSize shl 1
        if (fftSize > 4096) fftSize = 4096


        val buffer = FloatArray(fftSize)
        for (i in floatData.indices) {
            if (i < fftSize) buffer[i] = floatData[i]
        }
        if (floatData.size < fftSize) {
            for (i in floatData.size until fftSize) buffer[i] = 0f
        }


        val complexBuffer = FloatArray(fftSize * 2)
        for (i in 0 until fftSize) {
            complexBuffer[2 * i] = buffer[i]     // real
            complexBuffer[2 * i + 1] = 0f        // imag
        }

        val fft = FFT(fftSize)
        fft.forwardTransform(complexBuffer)


        val half = fftSize / 2
        val mags = FloatArray(half)
        for (i in 0 until half) {
            val re = complexBuffer[2 * i]
            val im = complexBuffer[2 * i + 1]
            val mag = sqrt(re * re + im * im)
            mags[i] = 20 * log10(mag + 1e-12f) // w dB
        }


        spectrum = mags
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val spec = spectrum ?: return

        val w = width.toFloat()
        val h = height.toFloat()
        val barCount = spec.size
        if (barCount == 0) return

        val barWidth = w / barCount
        val maxVal = spec.maxOrNull() ?: 1f

        for (i in 0 until barCount) {
            val norm = (spec[i] / maxVal).coerceIn(0f, 1f)
            val barHeight = norm * h
            val left = i * barWidth
            val top = h - barHeight
            canvas.drawRect(left, top, left + barWidth, h, paintBar)
        }
    }
}
