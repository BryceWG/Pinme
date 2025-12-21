package com.brycewg.pinme

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import com.brycewg.pinme.share.ShareProcessorService
import com.brycewg.pinme.usage.SourceAppTracker
import kotlinx.coroutines.runBlocking

/**
 * 透明 Activity，用于接收系统分享
 * 立即启动 Service 处理，然后关闭自身
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_SEND) {
            handleShareIntent(intent)
        }
        finish()
    }

    private fun handleShareIntent(intent: Intent) {
        val type = intent.type ?: return
        val sourcePackage = resolveShareSourcePackage(intent)
        val resolvedSourcePackage = runBlocking {
            if (SourceAppTracker.isEnabled(this@ShareReceiverActivity)) sourcePackage else null
        }

        when {
            type.startsWith("image/") -> {
                val sharedUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.getItemAt(0)?.uri
                if (sharedUri != null) {
                    Toast.makeText(this, "正在后台识别图片...", Toast.LENGTH_SHORT).show()
                    ShareProcessorService.startWithImage(this, sharedUri, resolvedSourcePackage)
                } else {
                    Toast.makeText(this, "未检测到可分享的图片", Toast.LENGTH_SHORT).show()
                }
            }
            type.startsWith("text/") -> {
                val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                    ?.toString()
                    ?.trim()
                if (!sharedText.isNullOrBlank()) {
                    Toast.makeText(this, "正在后台识别文本...", Toast.LENGTH_SHORT).show()
                    ShareProcessorService.startWithText(this, sharedText, resolvedSourcePackage)
                } else {
                    Toast.makeText(this, "未检测到可分享的文本", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun resolveShareSourcePackage(intent: Intent?): String? {
        val referrerUri = intent?.let {
            IntentCompat.getParcelableExtra(it, Intent.EXTRA_REFERRER, Uri::class.java)
        }
            ?: intent?.getStringExtra(Intent.EXTRA_REFERRER_NAME)?.let { Uri.parse(it) }
            ?: referrer
        return parseAndroidAppReferrer(referrerUri)
    }

    private fun parseAndroidAppReferrer(referrer: Uri?): String? {
        if (referrer == null || referrer.scheme != "android-app") return null
        val host = referrer.host
        if (!host.isNullOrBlank()) {
            return host
        }
        val schemeSpecific = referrer.schemeSpecificPart?.removePrefix("//")
        return schemeSpecific?.substringBefore("/")?.takeIf { it.isNotBlank() }
    }
}
