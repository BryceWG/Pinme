package com.brycewg.pinme.vllm

import com.brycewg.pinme.Constants
import com.brycewg.pinme.Constants.LlmProvider
import com.brycewg.pinme.db.PinMeDao
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

const val DEFAULT_CUSTOM_CHANNEL_NAME = "自定义"
private const val LEGACY_CUSTOM_CHANNEL_ID = "custom"

private val LLM_SCOPED_PREF_KEYS =
    listOf(
        Constants.PREF_LLM_API_KEY,
        Constants.PREF_LLM_MODEL,
        Constants.PREF_LLM_TEMPERATURE,
        Constants.PREF_LLM_CUSTOM_BASE_URL,
        Constants.PREF_LLM_EXTRA_PARAMS,
    )

@Serializable
data class CustomLlmPreset(
    val id: String,
    val name: String,
)

data class LlmChannel(
    val id: String,
    val displayName: String,
    val provider: LlmProvider,
) {
    val isCustom: Boolean get() = provider.isCustom
    val baseUrl: String get() = provider.baseUrl
    val defaultModel: String get() = provider.defaultModel
}

data class LlmRuntimeConfig(
    val channel: LlmChannel,
    val baseUrl: String,
    val apiKey: String?,
    val model: String,
    val temperature: Double,
    val extraParams: String?,
)

private val customPresetJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

fun LlmProvider.toStoredValue(): String = name.lowercase()

private fun isBuiltinLlmChannelId(id: String): Boolean =
    LlmProvider.entries.any { !it.isCustom && it.name.equals(id.trim(), ignoreCase = true) }

private fun builtinLlmChannels(): List<LlmChannel> =
    LlmProvider.entries
        .filter { !it.isCustom }
        .map { LlmChannel(id = it.toStoredValue(), displayName = it.displayName, provider = it) }

fun allLlmChannels(customPresets: List<CustomLlmPreset>): List<LlmChannel> =
    builtinLlmChannels() +
        customPresets.map { preset ->
            LlmChannel(
                id = preset.id,
                displayName = preset.name.trim().ifBlank { DEFAULT_CUSTOM_CHANNEL_NAME },
                provider = LlmProvider.CUSTOM,
            )
        }

fun resolveLlmChannel(
    storedId: String?,
    customPresets: List<CustomLlmPreset>,
): LlmChannel {
    val channels = allLlmChannels(customPresets)
    val id = storedId?.trim()?.lowercase().orEmpty()
    return channels.firstOrNull { it.id.equals(id, ignoreCase = true) }
        ?: channels.first { it.provider == LlmProvider.ZHIPU }
}

private fun nextCustomPresetName(existing: List<CustomLlmPreset>): String {
    val used = existing.map { it.name.trim() }.toSet()
    if (DEFAULT_CUSTOM_CHANNEL_NAME !in used) return DEFAULT_CUSTOM_CHANNEL_NAME
    var index = 2
    while ("$DEFAULT_CUSTOM_CHANNEL_NAME $index" in used) {
        index++
    }
    return "$DEFAULT_CUSTOM_CHANNEL_NAME $index"
}

fun newCustomLlmPreset(existing: List<CustomLlmPreset>): CustomLlmPreset {
    val existingIds = existing.map { it.id.lowercase() }.toSet()
    var id: String
    do {
        id = "custom_" + UUID.randomUUID().toString().replace("-", "").take(12)
    } while (id.lowercase() in existingIds || isBuiltinLlmChannelId(id))
    return CustomLlmPreset(id = id, name = nextCustomPresetName(existing))
}

private fun llmScopedKey(
    baseKey: String,
    channelId: String,
): String = "${baseKey}_${channelId.trim().lowercase()}"

suspend fun PinMeDao.getLlmScopedPreference(
    baseKey: String,
    channelId: String,
): String? = getPreference(llmScopedKey(baseKey, channelId))

suspend fun PinMeDao.setLlmScopedPreference(
    baseKey: String,
    channelId: String,
    value: String,
) {
    setPreference(llmScopedKey(baseKey, channelId), value)
}

suspend fun PinMeDao.getLlmScopedPreferenceWithLegacyFallback(
    baseKey: String,
    channelId: String,
): String? {
    val scoped = getPreference(llmScopedKey(baseKey, channelId))
    if (scoped != null) return scoped
    val normalizedId = channelId.trim().lowercase()
    val allowUnscopedFallback =
        normalizedId == LEGACY_CUSTOM_CHANNEL_ID || isBuiltinLlmChannelId(normalizedId)
    return if (allowUnscopedFallback) getPreference(baseKey) else null
}

suspend fun PinMeDao.migrateLegacyLlmPreferencesToScoped(
    channelId: String,
    baseKeys: List<String> = LLM_SCOPED_PREF_KEYS,
) {
    for (baseKey in baseKeys) {
        val scopedKey = llmScopedKey(baseKey, channelId)
        val existingScoped = getPreference(scopedKey)
        if (existingScoped != null) continue

        val legacy = getPreference(baseKey) ?: continue
        setPreference(scopedKey, legacy)
    }
}

suspend fun PinMeDao.deleteLlmScopedPreferences(channelId: String) {
    LLM_SCOPED_PREF_KEYS.forEach { baseKey ->
        deletePreference(llmScopedKey(baseKey, channelId))
    }
}

suspend fun PinMeDao.loadCustomLlmPresets(): List<CustomLlmPreset> {
    val parsed = parseCustomLlmPresets(getPreference(Constants.PREF_LLM_CUSTOM_PRESETS))
    if (parsed.isNotEmpty()) return parsed

    val storedProvider = getPreference(Constants.PREF_LLM_PROVIDER)?.trim()?.lowercase()
    val hasLegacyCustom =
        storedProvider == LEGACY_CUSTOM_CHANNEL_ID ||
            getPreference(llmScopedKey(Constants.PREF_LLM_API_KEY, LEGACY_CUSTOM_CHANNEL_ID)) != null ||
            getPreference(llmScopedKey(Constants.PREF_LLM_MODEL, LEGACY_CUSTOM_CHANNEL_ID)) != null ||
            getPreference(llmScopedKey(Constants.PREF_LLM_CUSTOM_BASE_URL, LEGACY_CUSTOM_CHANNEL_ID)) != null
    if (!hasLegacyCustom) return emptyList()

    val migrated = listOf(CustomLlmPreset(id = LEGACY_CUSTOM_CHANNEL_ID, name = DEFAULT_CUSTOM_CHANNEL_NAME))
    saveCustomLlmPresets(migrated)
    return migrated
}

suspend fun PinMeDao.saveCustomLlmPresets(presets: List<CustomLlmPreset>) {
    val normalized = normalizeCustomLlmPresets(presets)
    setPreference(
        Constants.PREF_LLM_CUSTOM_PRESETS,
        customPresetJson.encodeToString(ListSerializer(CustomLlmPreset.serializer()), normalized),
    )
}

private fun parseCustomLlmPresets(raw: String?): List<CustomLlmPreset> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        normalizeCustomLlmPresets(
            customPresetJson.decodeFromString(ListSerializer(CustomLlmPreset.serializer()), raw),
        )
    }.getOrDefault(emptyList())
}

private fun normalizeCustomLlmPresets(presets: List<CustomLlmPreset>): List<CustomLlmPreset> =
    presets
        .map { it.copy(id = it.id.trim(), name = it.name.trim()) }
        .filter { it.id.isNotBlank() && !isBuiltinLlmChannelId(it.id) }
        .distinctBy { it.id.lowercase() }
        .map { it.copy(name = it.name.ifBlank { DEFAULT_CUSTOM_CHANNEL_NAME }) }

suspend fun PinMeDao.loadLlmRuntimeConfig(): LlmRuntimeConfig {
    val presets = loadCustomLlmPresets()
    val channel = resolveLlmChannel(getPreference(Constants.PREF_LLM_PROVIDER), presets)
    migrateLegacyLlmPreferencesToScoped(channel.id)

    val baseUrl =
        if (channel.isCustom) {
            getLlmScopedPreferenceWithLegacyFallback(Constants.PREF_LLM_CUSTOM_BASE_URL, channel.id)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("自定义模式下必须设置 Base URL")
        } else {
            channel.baseUrl
        }
    val apiKey =
        getLlmScopedPreferenceWithLegacyFallback(Constants.PREF_LLM_API_KEY, channel.id)
            ?.takeIf { it.isNotBlank() }
    val model =
        getLlmScopedPreferenceWithLegacyFallback(Constants.PREF_LLM_MODEL, channel.id)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: channel.defaultModel.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("必须设置模型 ID")
    val temperature =
        getLlmScopedPreferenceWithLegacyFallback(Constants.PREF_LLM_TEMPERATURE, channel.id)
            ?.toDoubleOrNull()
            ?: 0.1
    val extraParams =
        getLlmScopedPreferenceWithLegacyFallback(Constants.PREF_LLM_EXTRA_PARAMS, channel.id)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    return LlmRuntimeConfig(
        channel = channel,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        temperature = temperature,
        extraParams = extraParams,
    )
}
