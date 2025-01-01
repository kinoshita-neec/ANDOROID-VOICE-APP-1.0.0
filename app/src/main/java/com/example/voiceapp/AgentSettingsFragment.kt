/**
 * エージェント設定画面のフラグメント
 * 
 * 主な機能：
 * - エージェントの名前、年齢、性別、性格、話し方の設定
 * - 設定の保存と読み込み
 */
package com.example.voiceapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.voiceapp.databinding.ActivityAgentSettingsBinding

class AgentSettingsFragment : Fragment() {
    private var _binding: ActivityAgentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityAgentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDropdowns()
        loadSettings()
        setupResponseLengthSlider()
        binding.saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun setupDropdowns() {
        val genders = arrayOf("女性", "男性", "その他")
        val personalities = arrayOf(
            "明るく優しい",
            "クール",
            "知的",
            "元気いっぱい",
            "おっとり"
        )
        val speechStyles = arrayOf(
            "丁寧",
            "フレンドリー",
            "カジュアル",
            "かわいい",
            "クール"
        )

        setupDropdown(binding.agentGenderInput, genders)
        setupDropdown(binding.personalityBaseInput, personalities)
        setupDropdown(binding.speechStyleBaseInput, speechStyles)
    }

    private fun setupDropdown(autoCompleteTextView: AutoCompleteTextView, items: Array<String>) {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            items
        )
        autoCompleteTextView.setAdapter(adapter)
    }

    private fun setupResponseLengthSlider() {
        binding.responseLength.addOnChangeListener { slider, value, fromUser -> 
            val description = when (value.toInt()) {
                1 -> "とても短め（5文字以下）"
                2 -> "短め（5-10文字程度）"
                3 -> "標準的な長さ（10-15文字程度）"
                4 -> "長め（15-25文字程度）"
                5 -> "とても長め（25文字以上）"
                else -> "標準的な長さ（10-15文字程度）"
            }
            binding.responseLengthDescription.text = description
        }
    }

    private fun loadSettings() {
        context?.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)?.apply {
            binding.agentNameInput.setText(getString("agent_name", "あすか"))
            binding.agentAgeInput.setText(getString("agent_age", ""))
            binding.agentGenderInput.setText(getString("agent_gender", "女性"), false)
            binding.personalityBaseInput.setText(getString("personality_base", "明るく優しい"), false)
            binding.personalityDetailInput.setText(getString("personality_detail", ""))
            binding.speechStyleBaseInput.setText(getString("speech_style_base", "丁寧"), false)
            binding.speechStyleDetailInput.setText(getString("speech_style_detail", ""))
            binding.responseLength.value = getFloat("response_length", 3f)
            binding.consistentStyleCheck.isChecked = getBoolean("consistent_style", true)
            binding.empathyCheck.isChecked = getBoolean("empathy", true)
            binding.explainCheck.isChecked = getBoolean("explain", true)
        }
    }

    private fun saveSettings() {
        with(binding) {
            val sharedPreferences = requireContext().getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
            sharedPreferences.edit().apply {
                putString("agent_name", agentNameInput.text.toString())
                putString("agent_age", agentAgeInput.text.toString())
                putString("agent_gender", agentGenderInput.text.toString())
                putString("personality_base", personalityBaseInput.text.toString())
                putString("personality_detail", personalityDetailInput.text.toString())
                putString("speech_style_base", speechStyleBaseInput.text.toString())
                putString("speech_style_detail", speechStyleDetailInput.text.toString())
                putFloat("response_length", responseLength.value)
                putBoolean("consistent_style", consistentStyleCheck.isChecked)
                putBoolean("empathy", empathyCheck.isChecked)
                putBoolean("explain", explainCheck.isChecked)
                apply()  // 設定を保存
            }
        }

        // 設定保存後、必ずプロンプトプレビューを更新
        (requireActivity() as? SettingsActivity)?.refreshPromptPreview()
        // 更新完了メッセージを表示
        Toast.makeText(context, "設定を保存しました", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
