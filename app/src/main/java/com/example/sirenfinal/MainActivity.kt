package com.example.sirenfinal

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView
    private lateinit var statusCard: CardView
    private lateinit var mainLayout: ConstraintLayout
    private lateinit var volumeMeter: VolumeMeterView
    private lateinit var spectrumView: SpectrumView
    private lateinit var thresholdEdit: EditText

    private var audioRecorder: AudioRecorder? = null
    private lateinit var classifier: SoundClassifier

    // Bufor 2s audio przy 44100 Hz = 88200 próbek
    private val SAMPLE_RATE = 44100
    private val DURATION_SEC = 2.0f
    private val WINDOW_SIZE = (SAMPLE_RATE * DURATION_SEC).toInt() // 88200

    private lateinit var recordingBuffer: ShortArray
    private var bufferWritePos = 0
    private var isListening = false

    // Domyślny próg klasyfikacji
    private var threshold = 0.7f

    // Kolory do animacji przejścia tła
    private val colorIdle by lazy { ContextCompat.getColor(this, R.color.background_idle) }
    private val colorListening by lazy { ContextCompat.getColor(this, R.color.background_listening) }
    private val colorDetected by lazy { ContextCompat.getColor(this, R.color.background_detected) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Mapowanie widoków
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusText = findViewById(R.id.statusText)
        statusCard = findViewById(R.id.statusCard)
        mainLayout = findViewById(R.id.mainLayout)
        volumeMeter = findViewById(R.id.volumeMeter)
        spectrumView = findViewById(R.id.spectrumView)
        thresholdEdit = findViewById(R.id.thresholdEdit)

        classifier = SoundClassifier(this)

        // Obsługa zmiany progu
        thresholdEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val txt = s?.toString()?.replace(",", ".") ?: ""
                threshold = try {
                    txt.toFloat().coerceIn(0f, 1f)
                } catch (e: Exception) {
                    0.7f
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Przyciski
        startButton.setOnClickListener {
            if (checkPermission()) startListening()
            else requestPermission()
        }
        stopButton.setOnClickListener {
            stopListening()
        }

        // Początkowy stan: idle
        setIdleState()
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            200
        )
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startListening() {
        if (isListening) return
        isListening = true

        // Inicjalizacja bufora 2s
        recordingBuffer = ShortArray(WINDOW_SIZE)
        bufferWritePos = 0

        // Ustawiamy stan „nasłuchiwania”
        animateBackground(colorListening)
        statusText.text = "Nasłuchiwanie..."
        statusText.setTextColor(ContextCompat.getColor(this, R.color.status_text_listening))
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.background_listening))
        volumeMeter.alpha = 1f
        spectrumView.alpha = 1f

        audioRecorder = AudioRecorder { audioChunk ->
            // 1) Doklej fragment do bufora 2s
            val toCopy = minOf(audioChunk.size, WINDOW_SIZE - bufferWritePos)
            System.arraycopy(audioChunk, 0, recordingBuffer, bufferWritePos, toCopy)
            bufferWritePos += toCopy

            // 2) Oblicz poziom głośności (VolumeMeter)
            var maxAmp = 0
            for (i in 0 until toCopy) {
                val amp = abs(audioChunk[i].toInt())
                if (amp > maxAmp) maxAmp = amp
            }
            val level = maxAmp.toFloat() / Short.MAX_VALUE
            runOnUiThread { volumeMeter.updateLevel(level) }

            // 3) Aktualizuj widok FFT
            runOnUiThread { spectrumView.updateAudioData(audioChunk) }

            // 4) Gdy bufor pełny (88200 próbek) – klasyfikuj
            if (bufferWritePos >= WINDOW_SIZE) {
                val windowCopy = recordingBuffer.copyOf()

                Thread {
                    val prediction = classifier.classify(windowCopy)

                    if (prediction > threshold) {

                        runOnUiThread {
                            stopListening()
                            AudioManagerHelper.muteMusic(this@MainActivity)
                            animateBackground(colorDetected)
                            animateStatusPulse()
                            statusText.text = "🚨 SYRENA WYKRYTA!"
                            statusText.setTextColor(ContextCompat.getColor(this, R.color.status_text_detected))
                            statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.background_detected))
                        }
                    } else {

                        runOnUiThread {
                            animateBackground(colorListening)
                            statusText.text = "Nasłuchiwanie..."
                            statusText.setTextColor(ContextCompat.getColor(this, R.color.status_text_listening))
                            statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.background_listening))
                        }
                    }
                }.start()


                bufferWritePos = 0
            }
        }
        audioRecorder?.start()
    }

    private fun stopListening() {
        if (!isListening) return
        isListening = false

        audioRecorder?.stop()
        audioRecorder = null


        animateBackground(colorIdle)
        statusText.text = "Monitoring zatrzymany"
        statusText.setTextColor(ContextCompat.getColor(this, R.color.status_text_idle))
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_bg))
        volumeMeter.alpha = 0.3f
        spectrumView.alpha = 0.3f
    }


    private fun animateBackground(targetColor: Int) {
        val currentColor = (mainLayout.background as? ColorDrawable)?.color
            ?: colorIdle
        val animator = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor)
        animator.duration = 500
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { valueAnimator ->
            val animated = valueAnimator.animatedValue as Int
            mainLayout.setBackgroundColor(animated)
            statusCard.setCardBackgroundColor(animated)
        }
        animator.start()
    }

    private fun animateStatusPulse() {
        statusCard.scaleX = 1f
        statusCard.scaleY = 1f
        val pulse = ValueAnimator.ofFloat(1f, 1.05f, 1f).apply {
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                statusCard.scaleX = scale
                statusCard.scaleY = scale
            }
        }
        pulse.start()
    }


    private fun setIdleState() {
        statusText.text = "Monitoring zatrzymany"
        statusText.setTextColor(ContextCompat.getColor(this, R.color.status_text_idle))
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_bg))

        mainLayout.setBackgroundColor(colorIdle)

        volumeMeter.alpha = 0.3f
        spectrumView.alpha = 0.3f
    }


    private fun setListeningState() {
        statusText.text = "Nasłuchiwanie..."
        statusText.setTextColor(ContextCompat.getColor(this, R.color.status_text_listening))
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.background_listening))

        mainLayout.setBackgroundColor(colorListening)

        volumeMeter.alpha = 1f
        spectrumView.alpha = 1f
    }


    private fun setDetectedState() {
        statusText.text = "🚨 SYRENA WYKRYTA!"
        statusText.setTextColor(ContextCompat.getColor(this, R.color.status_text_detected))
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.background_detected))

        mainLayout.setBackgroundColor(colorDetected)

        volumeMeter.alpha = 1f
        spectrumView.alpha = 1f
    }
}
