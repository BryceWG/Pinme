package com.brycewg.pinme.extract

import android.content.Context
import com.brycewg.pinme.Constants
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.db.PinMeDao

object ExtractErrorStore {
    const val FAILURE_TOAST = "识别失败，详情见首页"

    suspend fun record(
        context: Context,
        error: Throwable,
    ) {
        record(context, format(error))
    }

    suspend fun record(
        context: Context,
        message: String,
    ) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return
        dao(context).setPreference(Constants.PREF_LAST_EXTRACT_ERROR, trimmed)
    }

    suspend fun clear(context: Context) {
        dao(context).deletePreference(Constants.PREF_LAST_EXTRACT_ERROR)
    }

    fun format(error: Throwable): String {
        fun Throwable.headline(): String {
            val name = javaClass.simpleName.ifBlank { javaClass.name }
            val message = message?.trim().orEmpty()
            return if (message.isBlank()) name else "$name: $message"
        }

        return buildString {
            var current: Throwable? = error
            val seen = mutableSetOf<Throwable>()
            var first = true
            while (current != null && seen.add(current)) {
                if (first) {
                    append(current.headline())
                    first = false
                } else {
                    append("\nCaused by: ")
                    append(current.headline())
                }
                current = current.cause
            }
            val frames = error.stackTrace.take(16)
            if (frames.isNotEmpty()) {
                frames.forEach { frame ->
                    append("\n  at ")
                    append(frame.toString())
                }
            }
        }
    }

    private fun dao(context: Context): PinMeDao {
        if (!DatabaseProvider.isInitialized()) {
            DatabaseProvider.init(context.applicationContext)
        }
        return DatabaseProvider.dao()
    }
}
