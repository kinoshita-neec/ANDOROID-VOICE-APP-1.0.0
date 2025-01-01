package com.example.voiceapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.voiceapp.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * アプリケーションのメインアクティビティ
 * 
 * このクラスは以下の主要な機能を提供します：
 * 1. 音声認識とAI対話のUIインターフェース
 * 2. 音声認識マネージャーと連携した音声入力処理
 * 3. AIマネージャーを使用した対話処理
 * 4. テキスト読み上げ機能の管理
 * 5. チャットスタイルのメッセージ表示
 * 
 * @property binding ViewBindingによるレイアウトの参照
 * @property speechRecognitionManager 音声認識の管理
 * @property aiManager AI対話の管理
 * @property textToSpeechManager 音声読み上げの管理
 * @property chatLogger 会話履歴の管理
 */
class MainActivity : AppCompatActivity(), SpeechRecognitionManager.SpeechRecognitionCallback {
    // プロパティの宣言を追加
    private lateinit var binding: ActivityMainBinding
    private lateinit var speechRecognitionManager: SpeechRecognitionManager
    private lateinit var aiManager: AIManager
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var textToSpeechManager: TextToSpeechManager
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private var currentState = UIState()
    private var isReturningFromSettings = false
    private var isSpeechRecognitionInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRecognitionStart = false
    private lateinit var chatLogger: ChatLogger

    /**
     * アクティビティの初期化
     * 
     * 以下の順序で初期化を行います：
     * 1. システムの音声設定
     * 2. レイアウトのバインディング
     * 3. TextToSpeechManagerの初期化
     * 4. SpeechRecognitionManagerの初期化
     * 5. チャットアダプターの設定
     * 6. AIManagerの初期化
     * 7. ボタンのセットアップ
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // システムの音声をオフにする
        volumeControlStream = AudioManager.STREAM_MUSIC
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).apply {
            // 通知音を無効化
            adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
        }
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ドロワーレイアウトとナビゲーションビューの設定
        drawerLayout = binding.drawerLayout
        navigationView = binding.navigationView
        setupNavigation()

        // TextToSpeechManagerの初期化のみ行い、初期化完了時にwelcomeを開始
        textToSpeechManager = TextToSpeechManager(this)
        textToSpeechManager.initialize {
            startWelcomeSequence()
        }

        // チャットアダプターの初期化
        chatAdapter = ChatAdapter()
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        aiManager = AIManager(this)

        binding.settingsButton.setOnClickListener {
            isReturningFromSettings = true
            // 音声認識を停止
            speechRecognitionManager.stopListening()
            // 設定画面に遷移
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // SpeechRecognitionManagerは初期化のみ行う
        initializeSpeechRecognition()

        chatLogger = ChatLogger(this)
        updatePromptPreview()
    }

    override fun onResume() {
        super.onResume()
        if (isReturningFromSettings) {
            isReturningFromSettings = false
            startWelcomeSequence()
        }
    }

    /**
     * アプリ起動時の挨拶メッセージを生成
     * システムプロンプトを使用してAIからウェルカムメッセージを取得
     */
    private suspend fun getWelcomeMessage(): String {
        return try {
            // AIManagerを使用して挨拶を要求（ログには保存しない）
            val response = withContext(Dispatchers.IO) {
                aiManager.getAIResponse("あいさつをして")
            }
            // 応答のみを会話履歴に保存し、チャット画面に表示
            chatLogger.saveMessage(ChatMessage(response, false))
            chatAdapter.addMessage(ChatMessage(response, false))
            binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            
            response
        } catch (e: Exception) {
            Log.e("MainActivity", "Welcome message generation failed", e)
            getString(R.string.welcome_message).also { defaultMessage ->
                // エラー時もチャット画面に表示
                chatAdapter.addMessage(ChatMessage(defaultMessage, false))
                binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }

    /**
     * 音声認識フローの管理
     * 
     * アプリ起動時やメニューからの復帰時に実行され、
     * AIによる挨拶メッセージの生成と音声認識の開始を行います。
     */
    private fun startWelcomeSequence() {
        CoroutineScope(Dispatchers.Main).launch {
            pendingRecognitionStart = false
            val welcomeMessage = getWelcomeMessage()
            
            Log.d("MainActivity", "Starting welcome sequence with AI message: $welcomeMessage")
            // UI更新後に読み上げを開始
            updatePromptPreview()

            // 読み上げ開始前にSpeechRecognitionManagerの準備を開始
            prepareForNextRecognition()

            textToSpeechManager.speak(welcomeMessage) {
                // 読み上げ完了時に即座に音声認識を開始
                startPreparedRecognition()
            }
        }
    }

    private fun ensureSpeechRecognitionStart() {
        if (pendingRecognitionStart) {
            Log.d("MainActivity", "Recognition start already pending")
            return
        }
        pendingRecognitionStart = true
        manageSpeechRecognitionFlow()
    }

    /**
     * 音声認識の開始フローを管理する共通ヘルパー関数
     * 
     * この関数は以下の責務を持ちます：
     * 1. 音声読み上げの完了確認
     * 2. 適切なタイミングでの音声認識開始
     * 3. 状態管理とエラーハンドリング
     */
    private fun manageSpeechRecognitionFlow() {
        fun checkAndStart() {
            if (!textToSpeechManager.isSpeaking()) {
                Log.d("MainActivity", "Speech completed, starting recognition immediately")
                // 待機時間なしで即時実行
                if (!isReturningFromSettings && !textToSpeechManager.isSpeaking()) {
                    pendingRecognitionStart = false
                    checkPermissionAndStartListening()
                }
            } else {
                Log.d("MainActivity", "Still speaking, waiting for completion")
                // 状態確認のみ短い間隔で実行
                mainHandler.postDelayed({ checkAndStart() }, 10)
            }
        }

        checkAndStart()
    }

    private fun waitForSpeechCompletion(onComplete: () -> Unit) {
        fun checkSpeechStatus() {
            if (!textToSpeechManager.isSpeaking()) {
                Log.d("MainActivity", "Speech completion confirmed")
                Handler(Looper.getMainLooper()).postDelayed({
                    onComplete()
                }, 1) // 読み上げ完了確認後の待機時間
            } else {
                Log.d("MainActivity", "Still speaking, waiting...")
                Handler(Looper.getMainLooper()).postDelayed({ checkSpeechStatus() }, 5) // 読み上げ状態確認の間隔
            }
        }
        checkSpeechStatus()
    }

    private fun initializeSpeechRecognition() {
        Log.d("MainActivity", "Initializing speech recognition")
        speechRecognitionManager = SpeechRecognitionManager(this, this)
        isSpeechRecognitionInitialized = true
    }

    /**
     * 音声認識の権限チェックと開始
     * 
     * 1. RECORD_AUDIO権限の確認
     * 2. 権限がない場合は要求
     * 3. 権限がある場合は音声認識を開始
     */
    private fun checkPermissionAndStartListening() {
        Log.d("MainActivity", "Checking permission before starting recognition")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
            return
        }
        
        // 読み上げが完全に終了していることを確認
        if (!textToSpeechManager.isSpeaking()) {
            Log.d("MainActivity", "Actually starting speech recognition")
            updateUIState(currentState.copy(isListening = true))
            speechRecognitionManager.startListening()
        } else {
            Log.d("MainActivity", "Still speaking, trying immediately")
            // 待機なしで再試行
            checkPermissionAndStartListening()
        }
    }

    /**
     * UIの状態を更新し、表示を反映
     * 
     * 更新される要素：
     * - 音声認識ボタンの状態とアイコン
     * - キャンセルボタンの有効/無効状態
     * - エラーメッセージの表示
     * - 処理中の表示状態
     */
    private fun updateUIState(newState: UIState) {
        currentState = newState
        
        // エラーメッセージの表示
        currentState.error?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        }
    }

    // SpeechRecognitionCallback の実装
    override fun onRecognitionStarted() {
        updateUIState(currentState.copy(
            isListening = true,
            error = null
        ))
    }

    override fun onRecognitionResult(text: String) {
        updateUIState(currentState.copy(
            isProcessing = true
        ))
        chatAdapter.addMessage(ChatMessage(text, true))
        binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
        getAIResponse(text)
        updatePromptPreview()  // プロンプトプレビューを更新
    }

    override fun onRecognitionError(errorMessage: String) {
        updateUIState(currentState.copy(
            isListening = false,
            error = errorMessage
        ))
    }

    override fun onPartialResult(text: String) {
        // 必要に応じて部分認識結果を処理
    }

    /**
     * AIからの応答を取得し表示
     * 
     * 処理の流れ：
     * 1. コルーチンでバックグラウンド処理を開始
     * 2. AIManagerを使用して応答を取得
     * 3. 応答をチャットに表示
     * 4. UI状態を更新
     * 5. エラー発生時の処理
     */
    private fun getAIResponse(text: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                chatLogger.saveMessage(ChatMessage(text, true))  // ユーザーのメッセージを保存
                
                val response = withContext(Dispatchers.IO) {
                    aiManager.getAIResponse(text)
                }
                
                chatLogger.saveMessage(ChatMessage(response, false))  // AIの応答を保存
                chatAdapter.addMessage(ChatMessage(response, false))
                binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                
                updateUIState(currentState.copy(isProcessing = false))
                pendingRecognitionStart = false

                textToSpeechManager.speak(response) {
                    startPreparedRecognition()
                }
                // 読み上げ開始前に次の認識の準備を開始
                prepareForNextRecognition()
                
                updatePromptPreview()
            } catch (e: Exception) {
                updateUIState(currentState.copy(
                    isProcessing = false,
                    error = e.message
                ))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            speechRecognitionManager.startListening()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 通知音を元に戻す
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).apply {
            adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
        }
        speechRecognitionManager.destroy()
        textToSpeechManager.shutdown()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
        // 基本待機時間を0に設定（即時開始）
        private const val SPEECH_START_DELAY = 0L
    }

    private fun setupNavigation() {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_conversation_log -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ConversationLogFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                else -> false
            }
        }
    }

    private fun updatePromptPreview() {
        val promptPreview = PromptPreviewFragment.getSystemPrompt(this)
        binding.promptPreviewContent.text = promptPreview
    }

    /**
     * 次の音声認識のための準備を行う
     * TextToSpeech実行中に裏で準備を完了させる
     */
    private fun prepareForNextRecognition() {
        if (!isSpeechRecognitionInitialized) {
            initializeSpeechRecognition()
        }
        // 音声認識の事前準備を開始
        speechRecognitionManager.prepareForNextRecognition()
    }

    /**
     * 準備済みの音声認識を開始
     */
    private fun startPreparedRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "Starting prepared recognition immediately")
            updateUIState(currentState.copy(isListening = true))
            speechRecognitionManager.startPreparedListening()
        } else {
            checkPermissionAndStartListening()
        }
    }
}
