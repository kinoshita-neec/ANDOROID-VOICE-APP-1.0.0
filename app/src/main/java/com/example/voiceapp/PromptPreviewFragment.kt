package com.example.voiceapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.voiceapp.databinding.FragmentPromptPreviewBinding

/**
 * プロンプトプレビューを表示するフラグメント
 * 
 * このクラスは以下の機能を提供します：
 * 1. システムプロンプトの生成と表示
 * 2. エージェントとユーザーの設定の読み取り
 * 3. 会話ログの管理
 * 4. プロンプトのリアルタイム更新
 * 
 * @property binding ViewBindingによるレイアウトの参照
 * @property chatLogger 会話履歴の管理
 */
class PromptPreviewFragment : Fragment() {
    private var _binding: FragmentPromptPreviewBinding? = null
    private val binding get() = _binding!!
    private lateinit var chatLogger: ChatLogger

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPromptPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chatLogger = ChatLogger(requireContext())
        updatePromptPreview()
    }

    override fun onResume() {
        super.onResume()
        updatePromptPreview()
    }

    fun updatePromptPreview() {
        val promptPreview = getSystemPrompt(requireContext())
        binding.promptPreviewContent.text = promptPreview
    }

    companion object {
        /**
         * システムプロンプトを生成
         * 
         * 以下の情報を含むプロンプトを生成します：
         * 1. エージェントの基本情報（名前、性別、年齢）
         * 2. 性格と特徴（基本性格、話し方）
         * 3. 応答スタイル（長さ、一貫性、共感性）
         * 4. ユーザー情報
         * 5. 最近の会話履歴
         * 
         * @param context アプリケーションコンテキスト
         * @return 生成されたシステムプロンプト
         */
        fun getSystemPrompt(context: Context): String {
            val agentPrefs = context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
            val userPrefs = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)
            val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

            // エージェント設定の読み取り
            val name = agentPrefs.getString("agent_name", "あすか") ?: "あすか"
            val age = agentPrefs.getString("agent_age", "") ?: ""
            val gender = agentPrefs.getString("agent_gender", "女性") ?: "女性"
            val personalityBase = agentPrefs.getString("personality_base", "明るく優しい") ?: "明るく優しい"
            val personalityDetail = agentPrefs.getString("personality_detail", "") ?: ""
            val speechStyleBase = agentPrefs.getString("speech_style_base", "丁寧") ?: "丁寧"
            val speechStyleDetail = agentPrefs.getString("speech_style_detail", "") ?: ""
            val responseLength = agentPrefs.getFloat("response_length", 3f)
            val consistentStyle = agentPrefs.getBoolean("consistent_style", true)
            val empathy = agentPrefs.getBoolean("empathy", true)
            val explain = agentPrefs.getBoolean("explain", true)

            // ユーザー設定の読み取り
            val userName = userPrefs.getString("name", "") ?: ""
            val userAge = userPrefs.getString("age", "") ?: ""
            val userGender = userPrefs.getString("gender", "") ?: ""
            val userHobbies = userPrefs.getString("hobbies", "") ?: ""

            // 応答スタイルの特徴リストを作成
            val characteristics = mutableListOf<String>()
            if (consistentStyle) characteristics.add("いつも同じことを言わないなど、一貫した性格と応答スタイルを保つ")
            if (empathy) characteristics.add("ユーザーの気持ちに寄り添った対話を心がける")
            if (explain) characteristics.add("孤独な老人であるユーザを癒すような対話を心がける")

            // 会話ログの数を取得
            val conversationLogCount = appPrefs.getInt("conversation_log_count", 3)
            val recentMessages = ChatLogger(context).getMessages().takeLast(conversationLogCount)
            val recentMessagesContent = StringBuilder("以下はユーザーとAIの最近の会話です。\n")
            recentMessages.forEach { message ->
                val sender = if (message.isUser) "ユーザー" else "AI"
                recentMessagesContent.append("$sender: ${message.message}\n")
            }
            recentMessagesContent.append("AI: ")

            return """
システムプロンプト:
あなたは「${name}」という名前の植物で、ユーザのペットでありパートナーです。

# あなたの基本情報
性別: ${gender}
${if (age.isNotEmpty()) "年齢: ${age}歳" else ""}

# あなたの性格と特徴
基本性格: ${personalityBase}
${if (personalityDetail.isNotEmpty()) "性格の特徴: $personalityDetail" else ""}
基本的な話し方: ${speechStyleBase}
${if (speechStyleDetail.isNotEmpty()) "話し方の特徴: $speechStyleDetail" else ""}

# 応答スタイル
${getResponseLengthPrompt(responseLength)}
${characteristics.joinToString("\n")}

# ユーザーについて
名前: ${if (userName.isNotEmpty()) userName else "指定なし"}
年齢: ${if (userAge.isNotEmpty()) "${userAge}歳" else "指定なし"}
性別: ${if (userGender.isNotEmpty()) userGender else "指定なし"}
趣味: ${if (userHobbies.isNotEmpty()) userHobbies else "指定なし"}

# 最近の会話
$recentMessagesContent

以上の設定に基づいて、自然な会話を行ってください。""".trimIndent()
        }

        /**
         * 応答の長さに関するプロンプトを生成
         * 
         * @param value 応答の長さ（1-5のFloat値）
         * @return 応答長設定のプロンプト文字列
         */
        private fun getResponseLengthPrompt(value: Float): String {
            return when (value.toInt()) {
                1 -> "できるだけ簡潔に、5文字以下で応答してください。"
                2 -> "簡潔に、5-10文字程度で応答してください。"
                3 -> "10-15文字程度の標準的な長さで応答してください。"
                4 -> "詳しく、15-25文字程度で応答してください。"
                5 -> "とても詳しく、25文字以上で応答してください。"
                else -> "10-15文字程度の標準的な長さで応答してください。"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}