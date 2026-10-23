package com.tonio.libre2clock.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import java.util.Locale

class GlucoseVoiceManager private constructor(context: Context) : TextToSpeech.OnInitListener {

    private val appContext: Context = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        try {
            tts = TextToSpeech(appContext, this)
        } catch (e: Exception) {
            Log.e("GlucoseVoiceManager", "Failed to instantiate TextToSpeech", e)
        }
    }

    companion object {
        @Volatile
        private var instance: GlucoseVoiceManager? = null

        fun getInstance(context: Context): GlucoseVoiceManager {
            return instance ?: synchronized(this) {
                instance ?: GlucoseVoiceManager(context).also { instance = it }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val systemLocale = Locale.getDefault()
            val result = tts?.setLanguage(systemLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            isInitialized = true
        } else {
            Log.e("GlucoseVoiceManager", "TextToSpeech initialization failed with status $status")
            isInitialized = false
        }
    }

    fun speakGlucose(
        measurement: GlucoseMeasurement,
        rapidIob: Double = 0.0,
        slowIob: Double = 0.0,
        isDetailed: Boolean = false
    ) {
        val textToSpeak = formatGlucosePhrase(measurement, rapidIob, slowIob, isDetailed)
        speakText(textToSpeak)
    }

    fun speakCustom(phrase: String) {
        speakText(phrase)
    }

    private fun speakText(phrase: String) {
        if (!isInitialized || tts == null) {
            Log.w("GlucoseVoiceManager", "TTS not ready yet. Retrying initialization...")
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.setLanguage(Locale.getDefault())
                    isInitialized = true
                    tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "GlucoseVoiceMsg")
                }
            }
            return
        }
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "GlucoseVoiceMsg")
    }

    fun formatGlucosePhrase(
        measurement: GlucoseMeasurement,
        rapidIob: Double = 0.0,
        slowIob: Double = 0.0,
        isDetailed: Boolean = false
    ): String {
        val isSpanish = Locale.getDefault().language.equals("es", ignoreCase = true)
        val rawVal = measurement.value
        val calVal = if (measurement.calibratedValue > 0) measurement.calibratedValue else rawVal

        val trendText = when (measurement.trendArrow) {
            1 -> if (isSpanish) "bajando rápidamente" else "falling rapidly"
            2 -> if (isSpanish) "bajando" else "falling"
            3 -> if (isSpanish) "estable" else "stable"
            4 -> if (isSpanish) "subiendo" else "rising"
            5 -> if (isSpanish) "subiendo rápidamente" else "rising rapidly"
            else -> if (isSpanish) "estable" else "stable"
        }

        val glucosePart = if (rawVal != calVal) {
            if (isSpanish) "Glucosa $calVal, original $rawVal, $trendText"
            else "Glucose $calVal, raw $rawVal, $trendText"
        } else {
            if (isSpanish) "Glucosa $calVal, $trendText"
            else "Glucose $calVal, $trendText"
        }

        return if (isDetailed && (rapidIob > 0.0 || slowIob > 0.0)) {
            val totalIob = rapidIob + slowIob
            val iobPart = if (isSpanish) {
                String.format(Locale.getDefault(), ". Insulina activa %.1f unidades.", totalIob)
            } else {
                String.format(Locale.getDefault(), ". Active insulin %.1f units.", totalIob)
            }
            "$glucosePart$iobPart"
        } else {
            glucosePart
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e("GlucoseVoiceManager", "Error shutting down TTS", e)
        }
    }
}
