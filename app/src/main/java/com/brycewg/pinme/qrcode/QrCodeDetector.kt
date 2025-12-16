package com.brycewg.pinme.qrcode

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 二维码检测结果
 * @param boundingBox 二维码在原图中的边界框
 * @param croppedBitmap 裁剪后的二维码图片
 */
data class QrCodeResult(
    val boundingBox: Rect,
    val croppedBitmap: Bitmap
)

object QrCodeDetector {

    private const val TAG = "QrCodeDetector"

    /** 裁剪时的边距比例（避免裁剪太紧） */
    private const val CROP_PADDING_RATIO = 0.1f

    /** 最大裁剪尺寸（用于 RemoteViews 的 Bitmap 限制） */
    private const val MAX_CROP_SIZE = 400

    /**
     * 检测截图中的二维码
     * @param bitmap 原始截图
     * @return 检测到的第一个二维码结果，未检测到返回 null
     */
    suspend fun detect(bitmap: Bitmap): QrCodeResult? = withContext(Dispatchers.Default) {
        val scanner = BarcodeScanning.getClient()
        val image = InputImage.fromBitmap(bitmap, 0)

        try {
            val barcodes = suspendCancellableCoroutine { cont ->
                scanner.process(image)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
                    .addOnCanceledListener { cont.cancel() }
            }

            // 仅处理二维码类型（排除条形码等）
            val qrCode = barcodes.firstOrNull {
                it.format == Barcode.FORMAT_QR_CODE ||
                    it.format == Barcode.FORMAT_DATA_MATRIX ||
                    it.format == Barcode.FORMAT_AZTEC
            }

            if (qrCode == null || qrCode.boundingBox == null) {
                return@withContext null
            }

            val boundingBox = qrCode.boundingBox!!
            val croppedBitmap = cropQrCode(bitmap, boundingBox)

            QrCodeResult(
                boundingBox = boundingBox,
                croppedBitmap = croppedBitmap
            )
        } catch (e: Exception) {
            Log.e(TAG, "QR code detection failed", e)
            null
        } finally {
            scanner.close()
        }
    }

    /**
     * 裁剪二维码区域，带边距
     */
    private fun cropQrCode(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        // 计算带边距的裁剪区域
        val padding = (minOf(boundingBox.width(), boundingBox.height()) * CROP_PADDING_RATIO).toInt()
        val left = maxOf(0, boundingBox.left - padding)
        val top = maxOf(0, boundingBox.top - padding)
        val right = minOf(bitmap.width, boundingBox.right + padding)
        val bottom = minOf(bitmap.height, boundingBox.bottom + padding)

        val cropped = Bitmap.createBitmap(
            bitmap,
            left,
            top,
            right - left,
            bottom - top
        )

        // 如果尺寸过大，缩放以满足 RemoteViews 限制
        return if (cropped.width > MAX_CROP_SIZE || cropped.height > MAX_CROP_SIZE) {
            val scale = MAX_CROP_SIZE.toFloat() / maxOf(cropped.width, cropped.height)
            val scaledWidth = (cropped.width * scale).toInt()
            val scaledHeight = (cropped.height * scale).toInt()
            Bitmap.createScaledBitmap(cropped, scaledWidth, scaledHeight, true).also {
                if (it !== cropped) cropped.recycle()
            }
        } else {
            cropped
        }
    }
}
