package com.brycewg.pinme.extract

import org.json.JSONObject

data class ExtractParsed(
    val title: String,
    val content: String
)

object ExtractParsing {
    fun parseModelOutput(modelOutput: String): ExtractParsed {
        val trimmed = modelOutput.trim()
        val json = tryParseJsonObject(trimmed) ?: tryParseJsonObject(extractFirstJsonObject(trimmed))
        if (json != null) {
            val title = json.optString("title", "").trim()
            val content = json.optString("content", "").trim()
            if (title.isNotBlank() && content.isNotBlank()) {
                return ExtractParsed(title = title, content = content)
            }
        }

        return ExtractParsed(
            title = "识别结果",
            content = trimmed
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
}
