package com.brycewg.pinme.widget

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider

private const val DEFAULT_CAPSULE_COLOR = "#FF9800"

internal object WidgetColors {
    val rootBackground: GlanceColorProvider =
        ColorProvider(day = Color(0xFFF5F5F5), night = Color(0xFF1C1C1E))

    val cardBackground: GlanceColorProvider =
        ColorProvider(day = Color.White, night = Color(0xFF2C2C2E))

    val title: GlanceColorProvider =
        ColorProvider(day = Color(0xFF333333), night = Color(0xFFF2F2F2))

    val subtitle: GlanceColorProvider =
        ColorProvider(day = Color(0xFF666666), night = Color(0xFFB0B0B0))

    val tertiary: GlanceColorProvider =
        ColorProvider(day = Color(0xFF888888), night = Color(0xFF8E8E93))

    val hint: GlanceColorProvider =
        ColorProvider(day = Color(0xFF999999), night = Color(0xFF8E8E93))

    fun pinButton(capsuleHex: String?): GlanceColorProvider {
        val base =
            try {
                Color((capsuleHex ?: DEFAULT_CAPSULE_COLOR).toColorInt())
            } catch (_: Exception) {
                Color(0xFFFF9800)
            }
        return ColorProvider(
            day = mix(base, Color.White, 0.6f),
            night = mix(base, Color(0xFF2C2C2E), 0.45f),
        )
    }

    private fun mix(
        from: Color,
        to: Color,
        amount: Float,
    ): Color =
        Color(
            red = from.red * (1f - amount) + to.red * amount,
            green = from.green * (1f - amount) + to.green * amount,
            blue = from.blue * (1f - amount) + to.blue * amount,
            alpha = 1f,
        )
}
