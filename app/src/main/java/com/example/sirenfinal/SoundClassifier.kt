package com.example.sirenfinal

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Ładuje model TFLite (bez resource variables).
 * Przyjmuje ShortArray (2 sekundy audio = 88200 próbek),
 * liczy cechy (60 floatów) i przekazuje bezpośrednio do TFLite.
 */
class SoundClassifier(context: Context) {

    private val interpreter: Interpreter
    private val inputShape: IntArray

    init {

        val modelStream = context.assets.open("siren_model_noscaler_final.tflite")
        val modelBytes = modelStream.readBytes()
        val modelBuffer = ByteBuffer
            .allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())
        modelBuffer.put(modelBytes)
        modelBuffer.rewind()

        interpreter = Interpreter(modelBuffer)


        val inputTensor = interpreter.getInputTensor(0)
        inputShape = inputTensor.shape()
        Log.d("MODEL_INFO", "Input tensor shape: ${inputShape.joinToString(",")}")
    }


    fun classify(audioData: ShortArray): Float {
        return try {

            val features = FeatureExtractor.extract(
                audioData,
                sampleRate = 44100,
                durationSec = 2.0f
            )


            Log.d(
                "DEBUG_FEATURES",
                "features[0..9]= ${features.slice(0 until 10).joinToString(", ")}"
            )


            val inputBuffer: ByteBuffer = when {

                inputShape.size == 1 && inputShape[0] == 60 -> {
                    ByteBuffer.allocateDirect(60 * 4).order(ByteOrder.nativeOrder()).apply {
                        for (f in features) putFloat(f)
                        rewind()
                    }
                }

                inputShape.size == 2 && inputShape[0] == 1 && inputShape[1] == 60 -> {
                    ByteBuffer.allocateDirect(1 * 60 * 4).order(ByteOrder.nativeOrder()).apply {
                        for (f in features) putFloat(f)
                        rewind()
                    }
                }
                else -> {
                    Log.e("SoundClassifier", "Nieobsługiwany kształt wejścia: ${inputShape.joinToString(",")}")
                    return 0f
                }
            }


            val outputBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())


            interpreter.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()

            val prediction = outputBuffer.float
            Log.d("DEBUG_PRED", "Prediction = $prediction")
            prediction
        } catch (e: Exception) {
            Log.e("SoundClassifier", "Błąd klasyfikacji: ${e.message}")
            0f
        }
    }
}
