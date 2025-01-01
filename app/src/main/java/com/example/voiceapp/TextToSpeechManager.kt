package com.example.voiceapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Bundle
import android.util.Log
import java.util.Locale

/**
 * テキスト読み上げを管理するクラス
 * - 初期化と終了処理
 * - テキストの読み上げと中断
 * - 読み上げ状態の監視
 * を担当します。
 * 
 * 並行処理に対応し、読み上げの開始・終了を正確に追跡します。
 */
class TextToSpeechManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onInitializedCallback: (() -> Unit)? = null
    private var onUtteranceComplete: (() -> Unit)? = null

    @Volatile
    private var isSpeakingInProgress = false
    private val lock = Object()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentUtteranceId: String? = null

    fun initialize(callback: (() -> Unit)? = null) {
        onInitializedCallback = callback
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.JAPAN)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TextToSpeechManager", "Language not supported")
                } else {
                    isInitialized = true
                    onInitializedCallback?.invoke()
                }
            } else {
                Log.e("TextToSpeechManager", "Initialization failed")
            }
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.w("TextToSpeechManager", "TextToSpeech not initialized yet")
            onComplete?.invoke()
            return
        }

        synchronized(lock) {
            isSpeakingInProgress = true
            currentUtteranceId = "utterance_${System.currentTimeMillis()}"
        }

        Log.d("TextToSpeechManager", "Preparing to speak: $currentUtteranceId")

        tts?.let { ttsInstance ->
            if (ttsInstance.isSpeaking) {
                Log.d("TextToSpeechManager", "Stopping current speech")
                ttsInstance.stop()
                mainHandler.postDelayed({
                    startSpeaking(ttsInstance, text, onComplete)
                }, 200)
            } else {
                startSpeaking(ttsInstance, text, onComplete)
            }
        } ?: run {
            markSpeakingComplete()
            onComplete?.invoke()
        }
    }

    private fun startSpeaking(tts: TextToSpeech, text: String, onComplete: (() -> Unit)?) {
        val utteranceId = currentUtteranceId ?: return
        
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                Log.d("TextToSpeechManager", "Started speaking: $id")
            }
            
            override fun onDone(id: String?) {
                Log.d("TextToSpeechManager", "Finished speaking: $id")
                if (id == utteranceId) {
                    ensureSpeakingComplete(onComplete)
                }
            }
            
            override fun onError(id: String?) {
                Log.e("TextToSpeechManager", "Error speaking: $id")
                if (id == utteranceId) {
                    markSpeakingComplete()
                    mainHandler.post { onComplete?.invoke() }
                }
            }
        })

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun ensureSpeakingComplete(onComplete: (() -> Unit)?) {
        mainHandler.postDelayed({
            synchronized(lock) {
                if (currentUtteranceId != null && !tts?.isSpeaking!!) {
                    markSpeakingComplete()
                    Log.d("TextToSpeechManager", "Speech completion confirmed")
                    onComplete?.invoke()
                }
            }
        }, 500)
    }

    private fun markSpeakingComplete() {
        synchronized(lock) {
            isSpeakingInProgress = false
            currentUtteranceId = null
        }
    }

    fun isSpeaking(): Boolean {
        synchronized(lock) {
            return isSpeakingInProgress || (tts?.isSpeaking ?: false)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
