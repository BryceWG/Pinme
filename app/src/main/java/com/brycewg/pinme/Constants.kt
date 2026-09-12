package com.brycewg.pinme

object Constants {
    // 偏好设置键
    const val PREF_LIVE_CAPSULE_BG_COLOR = "live_capsule_bg_color"
    const val PREF_TUTORIAL_SEEN = "tutorial_seen"

    // 历史记录配置
    const val PREF_MAX_HISTORY_COUNT = "max_history_count" // 最大历史记录数量 (1-100)
    const val MIN_HISTORY_COUNT = 1
    const val MAX_HISTORY_COUNT = 100
    const val DEFAULT_MAX_HISTORY_COUNT = 100 // 默认最大历史记录数量

    // 截图压缩配置
    const val SCREENSHOT_MAX_WIDTH = 1080 // 截图最大宽度（按比例缩放）

    // 截图模式配置
    const val PREF_USE_ACCESSIBILITY_CAPTURE = "use_accessibility_capture" // 是否使用无障碍截图模式
    const val PREF_USE_ROOT_CAPTURE = "use_root_capture" // 是否使用 Root 截图模式（与无障碍互斥）

    // 隐私配置
    const val PREF_EXCLUDE_FROM_RECENTS = "exclude_from_recents" // 是否从多任务管理中隐藏
    const val PREF_CAPTURE_TOAST_ENABLED = "capture_toast_enabled" // 截图触发时 Toast 提醒
    const val PREF_SOURCE_APP_JUMP_ENABLED = "source_app_jump_enabled" // 实况通知标题跳转来源应用

    // LLM 配置
    const val PREF_LLM_PROVIDER = "llm_provider" // 当前渠道 ID: zhipu / siliconflow / custom / custom_<id>
    const val PREF_LLM_CUSTOM_PRESETS = "llm_custom_presets" // 自定义渠道预设列表（JSON）
    const val PREF_LLM_API_KEY = "llm_api_key" // API Key
    const val PREF_LLM_MODEL = "llm_model" // 模型 ID
    const val PREF_LLM_TEMPERATURE = "llm_temperature" // 温度 (0.0 - 2.0)
    const val PREF_LLM_CUSTOM_BASE_URL = "llm_custom_base_url" // 自定义 Base URL (到 /v1 即可)
    const val PREF_LLM_EXTRA_PARAMS = "llm_extra_params" // 自定义推理参数（JSON 对象，追加到请求体）
    const val PREF_CUSTOM_SYSTEM_INSTRUCTION = "custom_system_instruction" // 自定义系统指令（角色描述）

    // LLM 请求超时配置（全局生效，适用于测试连接、截图提取、文本提取）
    const val PREF_LLM_TIMEOUT_SECONDS = "llm_timeout_seconds" // 请求超时时间（秒）
    const val MIN_LLM_TIMEOUT_SECONDS = 5 // 最小超时（秒）
    const val MAX_LLM_TIMEOUT_SECONDS = 600 // 最大超时（秒）
    const val DEFAULT_LLM_TIMEOUT_SECONDS = 60 // 默认超时（秒）

    // 默认系统指令
    const val DEFAULT_SYSTEM_INSTRUCTION = "你是手机截图信息提取助手。从截图中识别用户最可能需要反复查看或复制的关键信息。"

    // 解析异常通知样式
    const val PARSE_ERROR_TITLE = "解析异常"
    const val PARSE_ERROR_CONTENT = "无法解析模型输出"
    const val PARSE_ERROR_EMOJI = "❗"
    const val PARSE_ERROR_CAPSULE_COLOR = "#D32F2F"
    const val MODEL_ERROR_TITLE = "模型出错"
    const val MODEL_ERROR_CONTENT = "模型调用失败"
    const val MODEL_ERROR_EMOJI = "❗"
    const val MODEL_ERROR_CAPSULE_COLOR = "#D32F2F"

    // 预置供应商
    enum class LlmProvider(
        val displayName: String,
        val baseUrl: String,
        val defaultModel: String,
    ) {
        ZHIPU("智谱 AI", "https://open.bigmodel.cn/api/paas/v4", "glm-4v-flash"),
        SILICONFLOW("硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-VL-72B-Instruct"),
        CUSTOM("自定义", "", ""),
        ;

        val isCustom: Boolean get() = this == CUSTOM

        companion object {
            fun fromStoredValue(value: String?): LlmProvider {
                val normalized = value?.trim()?.lowercase() ?: return ZHIPU
                return entries.firstOrNull { it.name.lowercase() == normalized } ?: ZHIPU
            }
        }
    }
}
