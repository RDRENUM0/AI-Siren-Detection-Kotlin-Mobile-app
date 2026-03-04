package com.example.sirenfinal

import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.mfcc.MFCC
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import kotlin.math.floor


object FeatureExtractor {
    fun extract(
        audioData: ShortArray,
        sampleRate: Int = 44100,
        durationSec: Float = 2.0f
    ): FloatArray {

        val floatSignal = audioData.map { it / 32768.0f }.toFloatArray()

        val nMfcc = 20
        val bufferSize = 2048
        val hopSize = 1024
        val numSamplesNeeded = (sampleRate * durationSec).toInt() // 88200

        val signal: FloatArray = if (floatSignal.size >= numSamplesNeeded) {
            floatSignal.copyOf(numSamplesNeeded)
        } else {
            FloatArray(numSamplesNeeded).apply {
                System.arraycopy(floatSignal, 0, this, 0, floatSignal.size)
            }
        }

        val audioFormat = TarsosDSPAudioFormat(
            sampleRate.toFloat(),
            16,
            1,
            true,
            false
        )

        val mfccProcessor = MFCC(
            bufferSize,
            sampleRate.toFloat(),
            nMfcc,
            40,
            300f,
            (sampleRate / 2).toFloat()
        )

        val numFrames = floor(((signal.size - bufferSize).toDouble() / hopSize) + 1)
            .toInt().coerceAtLeast(1)

        val allMfccs = Array(numFrames) { FloatArray(nMfcc) }
        val frameBuffer = FloatArray(bufferSize)

        for (frameIdx in 0 until numFrames) {
            val start = frameIdx * hopSize
            if (start + bufferSize <= signal.size) {
                System.arraycopy(signal, start, frameBuffer, 0, bufferSize)
            } else {
                val validLen = signal.size - start
                System.arraycopy(signal, start, frameBuffer, 0, validLen)
                for (i in validLen until bufferSize) frameBuffer[i] = 0f
            }
            val audioEvent = AudioEvent(audioFormat)
            audioEvent.setFloatBuffer(frameBuffer)
            mfccProcessor.process(audioEvent)
            for (j in 0 until nMfcc) {
                allMfccs[frameIdx][j] = mfccProcessor.mfcc[j]
            }
        }

        val delta = Array(numFrames) { FloatArray(nMfcc) }
        for (i in 0 until numFrames - 1) {
            for (j in 0 until nMfcc) {
                delta[i][j] = allMfccs[i + 1][j] - allMfccs[i][j]
            }
        }
        for (j in 0 until nMfcc) {
            delta[numFrames - 1][j] = 0f
        }

        val delta2 = Array(numFrames) { FloatArray(nMfcc) }
        for (i in 0 until numFrames - 1) {
            for (j in 0 until nMfcc) {
                delta2[i][j] = delta[i + 1][j] - delta[i][j]
            }
        }
        for (j in 0 until nMfcc) {
            delta2[numFrames - 1][j] = 0f
        }

        val meanMfcc = FloatArray(nMfcc)
        val meanDelta = FloatArray(nMfcc)
        val meanDelta2 = FloatArray(nMfcc)
        for (j in 0 until nMfcc) {
            var sumM = 0f
            var sumD = 0f
            var sumD2 = 0f
            for (i in 0 until numFrames) {
                sumM += allMfccs[i][j]
                sumD += delta[i][j]
                sumD2 += delta2[i][j]
            }
            meanMfcc[j] = sumM / numFrames
            meanDelta[j] = sumD / numFrames
            meanDelta2[j] = sumD2 / numFrames
        }

        val featureVector = FloatArray(nMfcc * 3)
        for (j in 0 until nMfcc) {
            featureVector[j] = meanMfcc[j]
            featureVector[nMfcc + j] = meanDelta[j]
            featureVector[2 * nMfcc + j] = meanDelta2[j]
        }

        return featureVector
    }
}
