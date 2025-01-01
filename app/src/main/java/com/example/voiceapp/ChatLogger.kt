package com.example.voiceapp

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * チャットメッセージのアーカイブ化を担当するクラス
 * - SharedPreferencesを使用したメッセージの保存
 * - JSONシリアライズ/デシリアライズ
 * - メッセージの追加、取得、削除操作
 * を提供します。
 */
class ChatLogger(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveMessage(message: ChatMessage) {
        try {
            val messages = getMessages().toMutableList()
            messages.add(message)
            prefs.edit().putString("messages", gson.toJson(messages)).apply()
        } catch (e: Exception) {
            Log.e("ChatLogger", "Error saving message", e)
        }
    }

    fun saveMessages(messages: List<ChatMessage>) {
        try {
            prefs.edit().putString("messages", gson.toJson(messages)).apply()
        } catch (e: Exception) {
            Log.e("ChatLogger", "Error saving messages", e)
        }
    }

    fun getMessages(): List<ChatMessage> {
        return try {
            val json = prefs.getString("messages", "[]")
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("ChatLogger", "Error getting messages", e)
            emptyList()
        }
    }

    fun clearMessages() {
        prefs.edit().clear().apply()
    }

    fun deleteMessages(messagesToDelete: List<ChatMessage>) {
        try {
            val messages = getMessages().toMutableList()
            messages.removeAll(messagesToDelete)
            prefs.edit().putString("messages", gson.toJson(messages)).apply()
        } catch (e: Exception) {
            Log.e("ChatLogger", "Error deleting messages", e)
        }
    }
}
