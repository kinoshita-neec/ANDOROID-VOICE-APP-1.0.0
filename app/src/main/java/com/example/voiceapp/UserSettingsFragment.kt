/**
 * ユーザー設定画面のフラグメント
 * 
 * 主な機能：
 * - ユーザーの名前、年齢、性別、趣味の設定
 * - 設定の保存と読み込み
 */
package com.example.voiceapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.voiceapp.databinding.ActivityUserSettingsBinding

class UserSettingsFragment : Fragment() {
    private var _binding: ActivityUserSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityUserSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings() // 既存の設定を読み込む
        binding.saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        context?.getSharedPreferences("user_settings", Context.MODE_PRIVATE)?.apply {
            binding.nameInput.setText(getString("name", ""))
            binding.ageInput.setText(getString("age", ""))
            when(getString("gender", "")) {
                "男性" -> binding.maleButton.isChecked = true
                "女性" -> binding.femaleButton.isChecked = true
            }
            binding.hobbiesInput.setText(getString("hobbies", ""))
        }
    }

    private fun saveSettings() {
        val name = binding.nameInput.text.toString()
        val age = binding.ageInput.text.toString()
        val gender = when (binding.genderGroup.checkedRadioButtonId) {
            R.id.maleButton -> "男性"
            R.id.femaleButton -> "女性"
            else -> ""
        }
        val hobbies = binding.hobbiesInput.text.toString()

        context?.getSharedPreferences("user_settings", Context.MODE_PRIVATE)?.edit()?.apply {
            putString("name", name)
            putString("age", age)
            putString("gender", gender)
            putString("hobbies", hobbies)
            apply()
        }
        
        Toast.makeText(context, "設定を保存しました", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
