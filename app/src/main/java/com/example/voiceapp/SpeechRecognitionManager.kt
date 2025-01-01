/**
 * SpeechRecognitionManager - 音声認識管理クラス
 * 
 * このクラスはAndroidの音声認識APIをラップし、
 * 継続的な音声認識とその結果のハンドリングを提供します。
 * 
 * 主な機能：
 * - 日本語音声認識の制御
 * - 連続音声認識モード
 * - 部分認識結果のリアルタイム取得
 * - エラー処理とリカバリー機能
 * 
 * 使用例：
 * ```
 * val manager = SpeechRecognitionManager(context, object : SpeechRecognitionCallback {
 *     override fun onRecognitionResult(text: String) {
 *         // 認識結果の処理
 *     }
 *     // 他のコールバックメソッド...
 * })
 * ```
 * 
 * @property context アプリケーションのContext
 * @property callback 音声認識結果を受け取るコールバック
 */
package com.example.voiceapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.*

/**
 * 音声認識を管理するクラス
 * 
 * 主な機能：
 * - 音声認識の初期化と管理
 * - 連続音声認識の制御
 * - 音声認識結果のコールバック処理
 * - エラーハンドリング
 * 
 * 技術的な注意点：
 * - ビープ音の無効化は完全には機能していない
 * - 音声認識の精度は端末やAndroidバージョンに依存
 * - メモリリークを防ぐため、適切なタイミングでdestroyを呼び出す必要がある
 */
class SpeechRecognitionManager(
    private val context: Context,
    private val callback: SpeechRecognitionCallback
) {
    companion object {
        /** 無音検出のタイムアウト時間 (12秒) */
        private const val SILENCE_TIMEOUT = 12000L
        /** 最小の発話時間 (0.5秒に短縮) */
        private const val MIN_SPEECH_LENGTH = 500L  // 2000Lから500Lに変更
        /** 部分認識結果の更新遅延時間 (1秒に短縮) */
        private const val PARTIAL_RESULTS_DELAY = 1000L  // 2000Lから1000Lに変更
        private const val NO_BEEP_SETTING = "android.speech.extra.BEEP_ENABLED"
        private const val NEXT_RECOGNITION_DELAY = 1000L // 3000Lから1000Lに変更
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldContinue = true // 連続認識を制御するフラグ
    private var speechStartTime: Long = 0L
    private var lastPartialResult: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private val partialResultRunnable = Runnable {
        if (lastPartialResult.isNotEmpty()) {
            callback.onPartialResult(lastPartialResult)
        }
    }

    @Volatile
    private var isRecognitionPending = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isStoppingInProgress = false
    private val recognitionLock = Object()
    private var isPrepared = false

    interface SpeechRecognitionCallback {
        fun onRecognitionStarted()
        fun onRecognitionResult(text: String)
        fun onRecognitionError(errorMessage: String)
        fun onPartialResult(text: String)
    }

    init {
        initializeSpeechRecognizer()
    }

    /**
     * 音声認識の初期化
     * 
     * - 既存のインスタンスを破棄
     * - 新しいSpeechRecognizerを作成
     * - リスナーを設定
     * - エラーハンドリング
     */
    @Synchronized
    private fun initializeSpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            System.gc()  // ガベージコレクションを促す
            
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }
        } catch (e: Exception) {
            Log.e("SpeechRecognitionManager", "Error in initialization: ${e.message}")
            callback.onRecognitionError("音声認識の初期化に失敗しました")
        }
    }

    /**
     * 音声認識リスナーの作成
     * - 音声認識の各段階でのコールバック処理を定義
     */
    private fun createRecognitionListener() = object : RecognitionListener {
        // 音声認識の準備完了時
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("SpeechRecognitionManager", "音声認識の準備完了")
            callback.onRecognitionStarted()
            speechStartTime = System.currentTimeMillis()
        }

        // 音声入力開始時
        override fun onBeginningOfSpeech() {
            Log.d("SpeechRecognitionManager", "音声入力開始")
            speechStartTime = System.currentTimeMillis()
        }

        // 音声レベルの変更時
        override fun onRmsChanged(rmsdB: Float) {
            // 音声入力レベルに応じてUIを更新
        }

        // 音声認識結果の取得時
        override fun onResults(results: Bundle?) {
            Log.d("SpeechRecognitionManager", "音声認識結果を受信")
            handler.removeCallbacks(partialResultRunnable)
            
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let {
                if (it.isNotEmpty()) {
                    callback.onRecognitionResult(it[0])
                    
                    // 連続認識モードの場合、次の認識を開始
                    if (shouldContinue && isListening) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            startListeningInternal()
                        }, NEXT_RECOGNITION_DELAY)
                    }
                }
            }
        }

        // エラー発生時
        override fun onError(error: Int) {
            // TODO: エラーコード5（クライアントエラー）の原因を調査し、適切な対処を実装する必要がある
            // 現状では音声認識の停止時に必ず発生するため、一時的に通知を抑制
            if (error != SpeechRecognizer.ERROR_CLIENT) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ネットワークタイムアウト"
                    SpeechRecognizer.ERROR_NETWORK -> "ネットワークエラー"
                    SpeechRecognizer.ERROR_AUDIO -> "音声入力エラー"
                    SpeechRecognizer.ERROR_SERVER -> "サーバーエラー"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "音声入力タイムアウト"
                    SpeechRecognizer.ERROR_NO_MATCH -> "音声認識失敗"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "認識エンジンがビジー"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "権限不足"
                    else -> "不明なエラー"
                }
                Log.e("SpeechRecognitionManager", "音声認識エラー: $error - $errorMessage")
                callback.onRecognitionError(errorMessage)
            } else {
                // クライアントエラーのログは残すが、コールバックは呼び出さない
                Log.d("SpeechRecognitionManager", "Ignoring client error during recognition cleanup")
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let {
                if (it.isNotEmpty()) {
                    lastPartialResult = it[0]
                    // 発話開始から一定時間経過後のみ部分結果を表示
                    val elapsedTime = System.currentTimeMillis() - speechStartTime
                    if (elapsedTime >= MIN_SPEECH_LENGTH) {
                        handler.removeCallbacks(partialResultRunnable)
                        handler.postDelayed(partialResultRunnable, PARTIAL_RESULTS_DELAY)
                    }
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d("SpeechRecognitionManager", "Event: $eventType")
        }
    }

    @Synchronized
    fun startListening() {
        Log.d("SpeechRecognitionManager", "Start listening requested. Current state: pending=$isRecognitionPending, listening=$isListening, stopping=$isStoppingInProgress")
        
        synchronized(recognitionLock) {
            if (isStoppingInProgress) {
                Log.d("SpeechRecognitionManager", "Stop in progress, delaying start")
                mainHandler.postDelayed({ startListening() }, 500)
                return
            }

            if (isRecognitionPending || isListening) {
                Log.d("SpeechRecognitionManager", "Need to stop previous recognition")
                stopListening()
                mainHandler.postDelayed({ startListening() }, 1000)
                return
            }

            prepareAndStartListening()
        }
    }

    private fun prepareAndStartListening() {
        synchronized(recognitionLock) {
            if (isStoppingInProgress || isListening) {
                Log.d("SpeechRecognitionManager", "Cannot start while stopping or already listening")
                return
            }

            isRecognitionPending = true
            shouldContinue = true
        }

        // 新しいSpeechRecognizerインスタンスを作成
        initializeSpeechRecognizer()

        mainHandler.postDelayed({
            synchronized(recognitionLock) {
                if (shouldContinue && !isStoppingInProgress) {
                    isRecognitionPending = false
                    Log.d("SpeechRecognitionManager", "Starting delayed recognition")
                    startListeningInternal()
                }
            }
        }, 1000)
    }

    /**
     * 音声認識を開始
     * 
     * 処理の流れ：
     * 1. 権限と利用可能性のチェック
     * 2. 認識パラメータの設定
     * 3. 音声認識の開始
     * 4. エラーハンドリング
     * 
     * 注意：ビープ音の無効化は端末依存で完全には機能しない
     */
    private fun startListeningInternal() {
        if (!shouldContinue) {
            Log.d("SpeechRecognitionManager", "Recognition cancelled")
            return
        }

        if (isListening) {
            Log.d("SpeechRecognitionManager", "Already listening")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onRecognitionError("音声認識が利用できません")
            return
        }

        try {
            if (!shouldContinue || isListening) {
                Log.d("SpeechRecognitionManager", "Recognition cancelled or already listening")
                return
            }

            if (speechRecognizer == null) {
                Log.d("SpeechRecognitionManager", "Reinitializing recognizer")
                initializeSpeechRecognizer()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                if (!shouldContinue) return@postDelayed

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3) // 認識結果の数を増やす
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    // 音声認識のタイミング設定
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MIN_SPEECH_LENGTH)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT)
                    // ビープ音を無効化
                    putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", MIN_SPEECH_LENGTH)
                    // 開始音と終了音を無効化
                    putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", SILENCE_TIMEOUT)
                    putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", SILENCE_TIMEOUT)
                    // サウンドを無効化
                    putExtra("android.speech.extra.NO_BEEP", true)
                    // ビープ音を完全に無効化するための複数の設定
                    putExtra(NO_BEEP_SETTING, false)  // 明示的にビープ音を無効化
                    putExtra("android.speech.extra.NO_BEEP", true)  // 古い方式でも無効化
                    putExtra("android.speech.extra.BEEP_ENABLED", false)  // 別の方式でも無効化
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)  // オフライン優先（ビープ音が鳴りにくい）
                    putExtra("android.speech.extra.DICTATION_MODE", true) // 長文認識モードを有効化
                }

                isListening = true
                speechStartTime = System.currentTimeMillis()
                lastPartialResult = ""
                
                Log.d("SpeechRecognitionManager", "Starting recognition with fresh state")
                speechRecognizer?.startListening(intent)
            }, 500)
        } catch (e: Exception) {
            Log.e("SpeechRecognitionManager", "Error starting recognition: ${e.message}")
            isListening = false
            isRecognitionPending = false
            callback.onRecognitionError("音声認識の開始に失敗しました")
            // エラー時は再初期化を試みる
            mainHandler.postDelayed({
                reinitialize()
            }, 1000)
        }
    }

    @Synchronized
    fun stopListening() {
        synchronized(recognitionLock) {
            if (isStoppingInProgress) {
                Log.d("SpeechRecognitionManager", "Stop already in progress")
                return
            }

            isStoppingInProgress = true
            Log.d("SpeechRecognitionManager", "Stopping recognition")
            isRecognitionPending = false
            shouldContinue = false
            isListening = false
        }
        
        try {
            speechRecognizer?.let { recognizer ->
                recognizer.stopListening()
                mainHandler.postDelayed({
                    try {
                        recognizer.cancel()
                        recognizer.destroy()
                        speechRecognizer = null
                        synchronized(recognitionLock) {
                            isStoppingInProgress = false
                        }
                        Log.d("SpeechRecognitionManager", "Recognition cleanup completed")
                    } catch (e: Exception) {
                        Log.e("SpeechRecognitionManager", "Error in cleanup: ${e.message}")
                        synchronized(recognitionLock) {
                            isStoppingInProgress = false
                        }
                    }
                }, 1000)
            } ?: run {
                synchronized(recognitionLock) {
                    isStoppingInProgress = false
                }
            }
        } catch (e: Exception) {
            Log.e("SpeechRecognitionManager", "Error stopping recognition: ${e.message}")
            synchronized(recognitionLock) {
                isStoppingInProgress = false
            }
        }
    }

    fun reinitialize() {
        Log.d("SpeechRecognitionManager", "Reinitializing speech recognition")
        stopListening()
        mainHandler.postDelayed({
            initializeSpeechRecognizer()
        }, 500)
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun isListening() = isListening

    fun prepareForNextRecognition() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }
        }
        isPrepared = true
        Log.d("SpeechRecognitionManager", "Recognition prepared and ready")
    }

    fun startPreparedListening() {
        if (!isPrepared) {
            prepareForNextRecognition()
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
        }
        speechRecognizer?.startListening(intent)
        Log.d("SpeechRecognitionManager", "Starting prepared recognition")
    }
}
