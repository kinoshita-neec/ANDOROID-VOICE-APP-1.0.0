package com.example.voiceapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.voiceapp.databinding.ItemConversationLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会話ログのリサイクラービューを管理するアダプタ
 * - メッセージの表示とフォーマット
 * - 項目の選択状態の管理
 * - 一括選択・削除機能
 * を実装しています。
 */
class ConversationLogAdapter : RecyclerView.Adapter<ConversationLogAdapter.ViewHolder>() {
    private var messages: MutableList<ChatMessage> = mutableListOf()
    private val selectedItems = mutableSetOf<Int>()

    class ViewHolder(private val binding: ItemConversationLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage, isSelected: Boolean, onCheckChanged: (Boolean) -> Unit) {
            binding.apply {
                checkBox.isChecked = isSelected
                checkBox.setOnCheckedChangeListener { _, isChecked -> onCheckChanged(isChecked) }
                senderText.text = if (message.isUser) "user" else "AI"
                messageText.text = message.message
                timestampText.text = DATE_FORMAT.format(Date(message.timestamp))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position], selectedItems.contains(position)) { isChecked ->
            if (isChecked) {
                selectedItems.add(position)
            } else {
                selectedItems.remove(position)
            }
        }
    }

    override fun getItemCount() = messages.size

    fun setMessages(messages: List<ChatMessage>) {
        this.messages = messages.toMutableList()
        notifyDataSetChanged()
    }

    fun getSelectedMessages(): List<ChatMessage> {
        // 選択されたメッセージを返すロジックを実装
        return listOf()
    }

    fun updateMessages(messages: List<ChatMessage>) {
        setMessages(messages)
    }

    fun removeMessageAt(position: Int) {
        messages.removeAt(position)
        notifyItemRemoved(position)
    }

    fun removeSelectedMessages() {
        selectedItems.sortedDescending().forEach { position ->
            removeMessageAt(position)
        }
        selectedItems.clear()
    }

    fun selectAll(isChecked: Boolean) {
        selectedItems.clear()
        if (isChecked) {
            selectedItems.addAll(messages.indices)
        }
        notifyDataSetChanged()
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
    }
}
