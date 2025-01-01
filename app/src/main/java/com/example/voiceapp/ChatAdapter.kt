/**
 * チャットメッセージ表示用のRecyclerViewアダプター
 * 
 * 主な機能：
 * - チャットメッセージのリスト管理
 * - メッセージ表示用のViewHolder管理
 * - ユーザーとAIのメッセージを区別して表示
 * - メッセージ追加時のアニメーション効果
 */
package com.example.voiceapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.constraintlayout.widget.ConstraintLayout

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
    private var messages = listOf<ChatMessage>()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageTextView: TextView = itemView.findViewById<TextView>(R.id.messageTextView)
            ?: throw IllegalStateException("TextView with id 'messageTextView' not found")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_message_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.messageTextView.apply {
            text = message.message
            
            val params = layoutParams as ConstraintLayout.LayoutParams
            if (message.isUser) {
                setBackgroundResource(R.drawable.chat_bubble)
                isActivated = true  // ユーザーメッセージ用のスタイル
                params.horizontalBias = 1f
            } else {
                setBackgroundResource(R.drawable.chat_bubble)
                isActivated = false  // AIメッセージ用のスタイル
                params.horizontalBias = 0f
            }
            layoutParams = params
            
            alpha = 0f
            animate().alpha(1f).setDuration(200).start()
        }
    }

    override fun getItemCount() = messages.size

    fun addMessage(message: ChatMessage) {
        messages = messages + message
        notifyItemInserted(messages.size - 1)
    }

    fun setMessages(newMessages: List<ChatMessage>) {
        // 表示可能なメッセージのみをフィルタリング
        messages = newMessages.filter { it.isVisible }
        notifyDataSetChanged()
    }

    fun clear() {
        messages = emptyList()
        notifyDataSetChanged()
    }
}
