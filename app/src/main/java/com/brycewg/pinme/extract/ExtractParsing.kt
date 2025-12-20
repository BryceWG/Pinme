package com.brycewg.pinme.extract

import org.json.JSONObject

data class ExtractParsed(
    val title: String,
    val content: String,
    val emoji: String? = null
)

data class ExtractParseResult(
    val parsed: ExtractParsed,
    val parsedFromJson: Boolean
)

object ExtractParsing {
    fun parseModelOutput(modelOutput: String): ExtractParsed {
        return parseModelOutputWithStatus(modelOutput).parsed
    }

    fun parseModelOutputWithStatus(modelOutput: String): ExtractParseResult {
        val trimmed = modelOutput.trim()
        val json = tryParseJsonObject(trimmed) ?: tryParseJsonObject(extractFirstJsonObject(trimmed))
        if (json != null) {
            val title = json.optString("title", "").trim()
            val content = json.optString("content", "").trim()
            val emoji = json.optString("emoji", "").trim().takeIf { it.isNotBlank() }
            if (title.isNotBlank() && content.isNotBlank()) {
                return ExtractParseResult(
                    parsed = ExtractParsed(title = title, content = content, emoji = emoji),
                    parsedFromJson = true
                )
            }
        }

        return ExtractParseResult(
            parsed = ExtractParsed(
                title = "识别结果",
                content = trimmed
            ),
            parsedFromJson = false
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
