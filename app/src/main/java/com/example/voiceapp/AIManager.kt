package com.example.voiceapp

import android.content.Context
import android.util.Log
import com.example.voiceapp.BuildConfig
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI APIとの通信を管理するクラス
 * 
 * 機能：
 * - OpenAI ChatGPT APIとの通信
 * - 応答の解析とエラーハンドリング
 * - デバッグログの出力
 * 
 * 技術的な注意点：
 * - APIキーの管理に注意
 * - レート制限への対応
 * - ネットワークエラーのハンドリング
 * - JSONレスポンスの適切な解析
 */
class AIManager(private val context: Context) {
    companion object {
        private const val BASE_URL = "https://api.openai.com/v1"
        private const val CHAT_ENDPOINT = "$BASE_URL/chat/completions"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        .build()

    private val apiKey = BuildConfig.OPENAI_API_KEY

    init {
        // APIキーのデバッグ情報を詳細に出力
        Log.d("AIManager", "API Key length: ${apiKey.length}")
        Log.d("AIManager", "API Key first chars: ${apiKey.take(5)}")
        Log.d("AIManager", "API Key last chars: ${apiKey.takeLast(5)}")
        // APIキーに余分な文字が含まれていないか確認
        Log.d("AIManager", "API Key contains whitespace: ${apiKey.contains(" ")}")
        Log.d("AIManager", "Raw API Key bytes: ${apiKey.toByteArray().joinToString()}")
    }

    private fun logRequestDetails(request: Request, requestBody: String) {
        Log.d("AIManager", """
            Request details:
            - URL: ${request.url}
            - Method: ${request.method}
            - Headers: ${request.headers}
            - Body: $requestBody
            - API Key: ${apiKey.take(5)}...${apiKey.takeLast(5)}
        """.trimIndent())
    }

    private fun logResponseDetails(response: Response, responseBody: String?) {
        Log.d("AIManager", """
            Response details:
            - Code: ${response.code}
            - Message: ${response.message}
            - Headers: ${response.headers}
            - Body: $responseBody
        """.trimIndent())
    }


    private fun getSystemPrompt(): String {
        return PromptPreviewFragment.getSystemPrompt(context).trimIndent().replace("\n", " ")
    }


    suspend fun getAIResponse(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AIManager", "API URL: $BASE_URL")
                Log.d("AIManager", "API呼び出し開始: $prompt")
                
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = """
                    {
                        "model": "gpt-4o-mini",
                        "messages": [
                            {
                                "role": "system",
                                "content": "${getSystemPrompt().replace("\"", "\\\"").replace("\n", " ")}"
                            },
                            {
                                "role": "user",
                                "content": "${prompt.replace("\"", "\\\"").replace("\n", " ")}"
                            }
                        ],
                        "temperature": 0.7
                    }
                """.trimIndent()

                Log.d("AIManager", "Request Body: ${requestBody.trim()}")  // トリムしてログに出力
                Log.d("AIManager", "Using API Key: ${apiKey.take(5)}...")

                val request = Request.Builder()
                    .url(CHAT_ENDPOINT)
                    .post(requestBody.toRequestBody(mediaType))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer ${apiKey.trim()}")
                    .build()

                logRequestDetails(request, requestBody)

                try {
                    Log.d("AIManager", "APIリクエスト送信")
                    client.newCall(request).execute().use { response: Response -> 
                        val responseBody = response.body?.string()
                        logResponseDetails(response, responseBody)
                        
                        // when式の戻り値の型を明示的に指定
                        val result: String = when (response.code) {
                            200 -> {
                                if (responseBody != null) {
                                    try {
                                        val jsonObject = JSONObject(responseBody)
                                        val choices = jsonObject.getJSONArray("choices")
                                        val firstChoice = choices.getJSONObject(0)
                                        val message = firstChoice.getJSONObject("message")
                                        message.getString("content")
                                    } catch (e: JSONException) {
                                        Log.e("AIManager", "JSON parsing error: ${e.message}")
                                        "応答の解析に失敗しました: ${e.message}"
                                    } catch (e: Exception) {
                                        Log.e("AIManager", "Unexpected parsing error: ${e.message}")
                                        "応答の解析中に予期せぬエラーが発生しました: ${e.message}"
                                    }
                                } else {
                                    "応答が空でした"
                                }
                            }
                            429 -> {
                                Log.w("AIManager", "レート制限に達しました")
                                "申し訳ありません。APIの利用制限に達しました。しばらく待ってから再度お試しください。"
                            }
                            401 -> "APIキーが無効です"
                            403 -> "APIアクセスが禁止されています"
                            404 -> "APIエンドポイントが見つかりません"
                            500 -> "OpenAIサーバーでエラーが発生しました"
                            else -> "エラーが発生しました: ${response.code}"
                        }
                        result // when式の結果を返す
                    }
                } catch (e: Exception) {
                    Log.e("AIManager", "APIリクエストエラー", e)
                    "エラーが発生しました: ${e.localizedMessage}"
                }
            } catch (e: Exception) {
                "エラーが発生しました: ${e.message}"
            }
        }
    }
}
