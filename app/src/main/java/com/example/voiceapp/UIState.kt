/**
 * アプリケーションのUI状態を管理するデータクラス
 * 
 * 管理する状態：
 * - 音声認識の実行状態
 * - 処理中の状態
 * - エラーメッセージの有無
 */
package com.example.voiceapp

data class UIState(
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null
)
