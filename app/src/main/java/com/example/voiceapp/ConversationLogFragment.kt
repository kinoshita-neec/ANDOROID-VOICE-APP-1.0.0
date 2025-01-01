package com.example.voiceapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.voiceapp.databinding.FragmentConversationLogBinding

/**
 * 会話ログを表示・管理するフラグメント
 * - 会話履歴の表示とソート機能
 * - ログの選択と削除
 * - 保持する会話ログ数の設定
 * を提供します。
 */
class ConversationLogFragment : Fragment() {
    private var _binding: FragmentConversationLogBinding? = null
    private val binding get() = _binding!!
    private lateinit var chatLogger: ChatLogger
    private lateinit var adapter: ConversationLogAdapter
    private var currentSortOrder = SortOrder.TIME_DESC

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationLogBinding.inflate(inflater, container, false)
        // ChatLoggerの初期化を追加
        chatLogger = ChatLogger(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 現在の設定値を読み込んで表示
        val appPrefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val currentLogCount = appPrefs.getInt("conversation_log_count", 3)
        binding.logCountInput.setText(currentLogCount.toString())

        // RecyclerViewの設定
        adapter = ConversationLogAdapter()
        chatLogger.getMessages().let { messages ->
            adapter.setMessages(messages)
        }
        binding.conversationRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.conversationRecyclerView.adapter = adapter

        // 更新ボタンの処理を追加
        binding.updateButton.setOnClickListener {
            val newCount = binding.logCountInput.text.toString().toIntOrNull()
            if (newCount != null && newCount > 0) {
                appPrefs.edit().putInt("conversation_log_count", newCount).apply()
                Toast.makeText(context, "会話ログ参照数を更新しました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "有効な数値を入力してください", Toast.LENGTH_SHORT).show()
            }
        }

        // ボタンとヘッダーの設定
        setupDeleteButton()
        setupSortHeaders()
        loadMessages()
    }

    private fun setupDeleteButton() {
        binding.deleteButton.setOnClickListener {
            val selectedMessages = adapter.getSelectedMessages()
            chatLogger.deleteMessages(selectedMessages)
            loadMessages()
        }
    }

    private fun setupSortHeaders() {
        binding.apply {
            headerCheckBox.setOnCheckedChangeListener { buttonView, isChecked ->
                adapter.selectAll(isChecked)
            }
            headerTimestamp.setOnClickListener {
                currentSortOrder = when (currentSortOrder) {
                    SortOrder.TIME_ASC -> SortOrder.TIME_DESC
                    else -> SortOrder.TIME_ASC
                }
                updateSortIndicators()
                loadMessages()
            }
            headerSender.setOnClickListener {
                currentSortOrder = when (currentSortOrder) {
                    SortOrder.SENDER_ASC -> SortOrder.SENDER_DESC
                    else -> SortOrder.SENDER_ASC
                }
                updateSortIndicators()
                loadMessages()
            }
            headerMessage.setOnClickListener {
                currentSortOrder = when (currentSortOrder) {
                    SortOrder.MESSAGE_ASC -> SortOrder.MESSAGE_DESC
                    else -> SortOrder.MESSAGE_ASC
                }
                updateSortIndicators()
                loadMessages()
            }
        }
    }

    private fun updateSortIndicators() {
        binding.apply {
            headerTimestamp.text = when (currentSortOrder) {
                SortOrder.TIME_ASC -> "日時 ▲"
                SortOrder.TIME_DESC -> "日時 ▼"
                else -> "日時"
            }
            headerSender.text = when (currentSortOrder) {
                SortOrder.SENDER_ASC -> "送信者 ▲"
                SortOrder.SENDER_DESC -> "送信者 ▼"
                else -> "送信者"
            }
            headerMessage.text = when (currentSortOrder) {
                SortOrder.MESSAGE_ASC -> "メッセージ ▲"
                SortOrder.MESSAGE_DESC -> "メッセージ ▼"
                else -> "メッセージ"
            }
        }
    }

    private fun loadMessages() {
        val messages = chatLogger.getMessages().let { messages ->
            when (currentSortOrder) {
                SortOrder.TIME_ASC -> messages.sortedBy { it.timestamp }
                SortOrder.TIME_DESC -> messages.sortedByDescending { it.timestamp }
                SortOrder.SENDER_ASC -> messages.sortedBy { it.isUser }
                SortOrder.SENDER_DESC -> messages.sortedByDescending { it.isUser }
                SortOrder.MESSAGE_ASC -> messages.sortedBy { it.message }
                SortOrder.MESSAGE_DESC -> messages.sortedByDescending { it.message }
            }
        }
        binding.conversationRecyclerView.adapter?.let { adapter ->
            (adapter as ConversationLogAdapter).setMessages(messages)
        }
    }

    private fun saveConversationLogCount(count: Int) {
        val sharedPreferences = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("conversation_log_count", count).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    enum class SortOrder {
        TIME_ASC, TIME_DESC,
        SENDER_ASC, SENDER_DESC,
        MESSAGE_ASC, MESSAGE_DESC
    }
}
