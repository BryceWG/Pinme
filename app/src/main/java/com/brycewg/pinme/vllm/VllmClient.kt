package com.brycewg.pinme.vllm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VllmClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()
) {
    suspend fun chatCompletionWithImage(
        baseUrl: String,
        apiKey: String?,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imageBase64: String,
        temperature: Double = 0.1,
        maxTokens: Int = 256
    ): String = withContext(Dispatchers.IO) {
        val url = buildChatCompletionsUrl(baseUrl)
        val bodyJson = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        }
                    )
                    .put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "content",
                                JSONArray()
                                    .put(
                                        JSONObject().apply {
                                            put("type", "text")
                                            put("text", userPrompt)
                                        }
                                    )
                                    .put(
                                        JSONObject().apply {
                                            put("type", "image_url")
                                            put(
                                                "image_url",
                                                JSONObject().apply {
                                                    put("url", "data:image/png;base64,$imageBase64")
                                                }
                                            )
                                        }
                                    )
                            )
                        }
                    )
            )
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }

        val requestBody = bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        response.use { resp ->
            val responseBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("vLLM请求失败: HTTP ${resp.code} ${resp.message}\n$responseBody")
            }

            val json = JSONObject(responseBody)
            val content = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "")
            if (content.isBlank()) {
                throw IllegalStateException("vLLM返回空内容")
            }
            content
        }
    }

    suspend fun testConnection(
        baseUrl: String,
        apiKey: String?,
        model: String
    ): String = withContext(Dispatchers.IO) {
        val url = buildChatCompletionsUrl(baseUrl)
        val bodyJson = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", "Hi")
                        }
                    )
            )
            put("max_tokens", 16)
        }

        val requestBody = bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        response.use { resp ->
            val responseBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: $responseBody")
            }

            val json = JSONObject(responseBody)
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "")
        }
    }

    private fun buildChatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        // 检查是否已包含版本号 (如 /v1, /v4 等)
        return if (Regex("/v\\d+$").containsMatchIn(trimmed)) {
            "$trimmed/chat/completions"
        } else {
            "$trimmed/v1/chat/completions"
        }
    }
}
