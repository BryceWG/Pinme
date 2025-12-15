package com.brycewg.pinme

object Constants {
    // 偏好设置键
    const val PREF_LIVE_CAPSULE_BG_COLOR = "live_capsule_bg_color"

    // LLM 配置
    const val PREF_LLM_PROVIDER = "llm_provider"           // 供应商类型: zhipu / siliconflow / custom
    const val PREF_LLM_API_KEY = "llm_api_key"             // API Key
    const val PREF_LLM_MODEL = "llm_model"                 // 模型 ID
    const val PREF_LLM_TEMPERATURE = "llm_temperature"     // 温度 (0.0 - 2.0)
    const val PREF_LLM_CUSTOM_BASE_URL = "llm_custom_base_url"  // 自定义 Base URL (到 /v1 即可)
    const val PREF_LLM_SYSTEM_PROMPT = "llm_system_prompt"

    // 预置供应商
    enum class LlmProvider(val displayName: String, val baseUrl: String, val defaultModel: String) {
        ZHIPU("智谱 AI", "https://open.bigmodel.cn/api/paas/v4", "glm-4v-flash"),
        SILICONFLOW("硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-VL-72B-Instruct"),
        CUSTOM("自定义", "", "");

        companion object {
            fun fromStoredValue(value: String?): LlmProvider {
                val normalized = value?.trim()?.lowercase() ?: return ZHIPU
                return entries.firstOrNull { it.name.lowercase() == normalized } ?: ZHIPU
            }
        }
    }
}
