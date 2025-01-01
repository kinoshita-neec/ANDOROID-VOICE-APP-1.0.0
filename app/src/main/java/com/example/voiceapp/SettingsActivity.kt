package com.example.voiceapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.voiceapp.databinding.ActivitySettingsBinding
import com.google.android.material.tabs.TabLayoutMediator

/**
 * アプリケーションの設定画面を管理するアクティビティ
 * 
 * このクラスは以下の機能を提供します：
 * 1. ViewPager2を使用したタブ式の設定画面
 * 2. ユーザー設定の管理（プロフィール情報）
 * 3. エージェント設定の管理（性格、話し方）
 * 4. プロンプトプレビューの表示
 * 5. 会話ログの表示と管理
 * 
 * @property binding ViewBindingによるレイアウトの参照
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupTabs()

        binding.backButton.setOnClickListener {
            finish()
        }
    }

    /**
     * ViewPagerの設定
     * 各設定画面をフラグメントとして管理します
     */
    private fun setupViewPager() {
        binding.viewPager.adapter = SettingsPagerAdapter(this)
    }

    /**
     * タブの設定
     * 各設定画面のタイトルとナビゲーションを管理します
     */
    private fun setupTabs() {
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "ユーザー設定"
                1 -> "エージェント設定"
                2 -> "プロンプト"
                3 -> "会話ログ"
                else -> ""
            }
        }.attach()
    }

    /**
     * プロンプトプレビューを更新
     * 設定変更時に他のフラグメントから呼び出される
     */
    fun refreshPromptPreview() {
        // ViewPager2から直接プロンプトプレビューのフラグメントを取得
        val fragments = binding.viewPager.adapter?.let { adapter ->
            (0 until adapter.itemCount).mapNotNull { position ->
                supportFragmentManager.findFragmentByTag("f${position}")
            }
        } ?: emptyList()
        
        fragments.filterIsInstance<PromptPreviewFragment>()
            .firstOrNull()?.updatePromptPreview()
    }
}

private class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4  // タブを4つに変更

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> UserSettingsFragment()
            1 -> AgentSettingsFragment()
            2 -> PromptPreviewFragment()
            3 -> ConversationLogFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}
