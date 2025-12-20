package com.brycewg.pinme.usage

import android.content.Context
import com.brycewg.pinme.Constants
import com.brycewg.pinme.capture.AccessibilityCaptureService
import com.brycewg.pinme.db.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SourceAppTracker {
    suspend fun isEnabled(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!DatabaseProvider.isInitialized()) {
            DatabaseProvider.init(context)
        }
        DatabaseProvider.dao().getPreference(Constants.PREF_SOURCE_APP_JUMP_ENABLED) == "true"
    }

    fun resolveForegroundPackage(context: Context): String? {
        return AccessibilityCaptureService.getActiveWindowPackageName(context)
    }
}
