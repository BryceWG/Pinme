package com.brycewg.pinme.extract

import android.content.Context
import com.brycewg.pinme.Constants
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.db.PinMeDao

object ExtractErrorStore {
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
        val message = error.message?.trim().orEmpty()
        return message.ifBlank { error::class.java.simpleName }
    }

    private fun dao(context: Context): PinMeDao {
        if (!DatabaseProvider.isInitialized()) {
            DatabaseProvider.init(context.applicationContext)
        }
        return DatabaseProvider.dao()
    }
}
