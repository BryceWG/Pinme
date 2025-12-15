package com.brycewg.pinme.vllm

import com.brycewg.pinme.Constants.LlmProvider
import com.brycewg.pinme.db.PinMeDao

private fun llmScopedKey(baseKey: String, provider: LlmProvider): String {
    return "${baseKey}_${provider.name.lowercase()}"
}

suspend fun PinMeDao.getLlmScopedPreference(baseKey: String, provider: LlmProvider): String? {
    return getPreference(llmScopedKey(baseKey, provider))
}

suspend fun PinMeDao.setLlmScopedPreference(baseKey: String, provider: LlmProvider, value: String) {
    setPreference(llmScopedKey(baseKey, provider), value)
}

suspend fun PinMeDao.getLlmScopedPreferenceWithLegacyFallback(
    baseKey: String,
    provider: LlmProvider
): String? {
    return getPreference(llmScopedKey(baseKey, provider)) ?: getPreference(baseKey)
}

suspend fun PinMeDao.migrateLegacyLlmPreferencesToScoped(provider: LlmProvider, baseKeys: List<String>) {
    for (baseKey in baseKeys) {
        val scopedKey = llmScopedKey(baseKey, provider)
        val existingScoped = getPreference(scopedKey)
        if (existingScoped != null) continue

        val legacy = getPreference(baseKey) ?: continue
        setPreference(scopedKey, legacy)
    }
}

fun LlmProvider.toStoredValue(): String = name.lowercase()
