package com.brycewg.pinme.usage

import android.content.Context
import com.brycewg.pinme.Constants
import com.brycewg.pinme.capture.AccessibilityCaptureService
import com.brycewg.pinme.db.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

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

    suspend fun resolveForegroundPackageWithRootFallback(context: Context): String? = withContext(Dispatchers.IO) {
        val fromAccessibility = resolveForegroundPackage(context)
        if (!fromAccessibility.isNullOrBlank()) {
            return@withContext fromAccessibility
        }
        resolveForegroundPackageViaRoot(context)
    }

    private suspend fun resolveForegroundPackageViaRoot(context: Context): String? {
        val activityOutput = runSuCommand("dumpsys activity activities")
        val activityPackage = activityOutput?.let { extractPackageFromOutput(it, context) }
        if (!activityPackage.isNullOrBlank()) {
            return activityPackage
        }
        val windowOutput = runSuCommand("dumpsys window windows")
        return windowOutput?.let { extractPackageFromOutput(it, context) }
    }

    private fun extractPackageFromOutput(output: String, context: Context): String? {
        val patterns = listOf(
            Regex("mResumedActivity:.*?\\s([\\w.]+)/([\\w.]+|\\.[\\w.]+)"),
            Regex("ResumedActivity:.*?\\s([\\w.]+)/([\\w.]+|\\.[\\w.]+)"),
            Regex("mFocusedApp=.*?\\s([\\w.]+)/([\\w.]+|\\.[\\w.]+)"),
            Regex("mCurrentFocus=.*?\\s([\\w.]+)/([\\w.]+|\\.[\\w.]+)")
        )
        for (line in output.lineSequence()) {
            for (pattern in patterns) {
                val match = pattern.find(line) ?: continue
                val packageName = match.groupValues.getOrNull(1)?.trim().orEmpty()
                if (packageName.isNotBlank() && packageName != context.packageName) {
                    return packageName
                }
            }
        }
        return null
    }

    private suspend fun runSuCommand(command: String, timeoutSeconds: Long = 2): String? = coroutineScope {
        val process = try {
            ProcessBuilder("su", "-c", command).start()
        } catch (_: Exception) {
            return@coroutineScope null
        }

        try {
            process.outputStream.close()
            val stdoutDeferred = async { process.inputStream.bufferedReader().use { it.readText() } }
            val stderrDeferred = async { process.errorStream.bufferedReader().use { it.readText() } }
            val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!exited) {
                process.destroy()
                process.waitFor(200, TimeUnit.MILLISECONDS)
                if (process.isAlive) {
                    process.destroyForcibly()
                }
                return@coroutineScope null
            }
            val stdout = runCatching { stdoutDeferred.await() }.getOrDefault("")
            runCatching { stderrDeferred.await() }
            if (process.exitValue() != 0) {
                return@coroutineScope null
            }
            if (stdout.isBlank()) {
                return@coroutineScope null
            }
            stdout
        } finally {
            if (process.isAlive) {
                process.destroy()
            }
        }
    }
}
