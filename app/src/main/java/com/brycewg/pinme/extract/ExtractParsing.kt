package com.brycewg.pinme.extract

import org.json.JSONArray
import org.json.JSONObject

data class ExtractParsed(
    val title: String,
    val content: String,
    val emoji: String? = null
)

data class MultiExtractResult(
    val items: List<ExtractParsed>,
    val isMultiMode: Boolean = false
)

object ExtractParsing {
    fun parseModelOutput(modelOutput: String, isMultiMode: Boolean = false): MultiExtractResult {
        val trimmed = modelOutput.trim()
        
        // 尝试解析为多信息数组格式
        if (isMultiMode) {
            val jsonArray = tryParseJsonArray(trimmed) ?: tryParseJsonArray(extractFirstJsonArray(trimmed))
            if (jsonArray != null) {
                val items = mutableListOf<ExtractParsed>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(i)
                    if (item != null) {
                        val title = item.optString("title", "").trim()
                        val content = item.optString("content", "").trim()
                        val emoji = item.optString("emoji", "").trim().takeIf { it.isNotBlank() }
                        if (title.isNotBlank() && content.isNotBlank()) {
                            items.add(ExtractParsed(title = title, content = content, emoji = emoji))
                        }
                    }
                }
                if (items.isNotEmpty()) {
                    return MultiExtractResult(items = items, isMultiMode = true)
                }
            }
        }
        
        // 回退到单信息解析（向后兼容）
        val json = tryParseJsonObject(trimmed) ?: tryParseJsonObject(extractFirstJsonObject(trimmed))
        if (json != null) {
            val title = json.optString("title", "").trim()
            val content = json.optString("content", "").trim()
            val emoji = json.optString("emoji", "").trim().takeIf { it.isNotBlank() }
            if (title.isNotBlank() && content.isNotBlank()) {
                return MultiExtractResult(items = listOf(ExtractParsed(title = title, content = content, emoji = emoji)))
            }
        }

        return MultiExtractResult(
            items = listOf(ExtractParsed(
                title = "识别结果",
                content = trimmed
            ))
        )
    }

    private fun tryParseJsonObject(input: String?): JSONObject? {
        if (input.isNullOrBlank()) return null
        return try {
            JSONObject(input)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun tryParseJsonArray(input: String?): JSONArray? {
        if (input.isNullOrBlank()) return null
        return try {
            JSONArray(input)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFirstJsonArray(text: String): String? {
        val start = text.indexOf('[')
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
