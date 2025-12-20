package com.brycewg.pinme.notification

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

class OpenSourceAppReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME)?.trim()
        if (packageName.isNullOrEmpty()) {
            Toast.makeText(context, "无法打开来源应用", Toast.LENGTH_SHORT).show()
            return
        }
        val packageManager = context.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return
        }

        val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        val resolvedActivities = packageManager.queryIntentActivities(
            launcherQuery,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        if (resolvedActivities.isNotEmpty()) {
            val activityInfo = resolvedActivities.first().activityInfo
            val component = ComponentName(activityInfo.packageName, activityInfo.name)
            val explicitIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(explicitIntent)
            return
        }

        val isInstalled = runCatching { packageManager.getPackageInfo(packageName, 0) }.isSuccess
        if (isInstalled) {
            val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            Toast.makeText(context, "应用无启动入口，已打开应用信息", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "目标应用已卸载或包名无效", Toast.LENGTH_SHORT).show()
        }
    }
}
