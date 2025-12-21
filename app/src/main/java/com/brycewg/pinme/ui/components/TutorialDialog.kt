package com.brycewg.pinme.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun TutorialDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("使用教程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. 去智谱官网注册账号，申请自己的大模型 API Key。")
                Text("2. 在设置中填入智谱 AI 的 API Key，点击测试链接，应显示连接成功。")
                Text("3. 可以选择使用无障碍或者 Root 权限（adb 模板即可）实现静默截图处理；若不启用，每次截图会申请录制屏幕权限，无需其他特殊权限。")
                Text("4. 可将快捷方式发送到桌面或添加控制中心磁贴来截图；也可通过系统分享图片/文本到 PinMe 提取信息；还可在记录页右下角手动导入图片或文字内容。")
                Text("5. 更多功能请自行探索。")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://bigmodel.cn/usercenter/proj-mgmt/apikeys")
                    )
                    context.startActivity(intent)
                }
            ) {
                Text("智谱官网")
            }
        }
    )
}
