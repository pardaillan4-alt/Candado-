package com.candado.app.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object Qr {
    fun bitmap(text: String, label: String, size: Int = 900): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 2)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val extra = 110
        val bmp = Bitmap.createBitmap(size, size + extra, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        for (x in 0 until size) for (y in 0 until size) if (m[x, y]) bmp.setPixel(x, y, Color.BLACK)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 44f; textAlign = Paint.Align.CENTER }
        Canvas(bmp).drawText("Candado: $label", size / 2f, size + 70f, p)
        return bmp
    }

    /** Guarda en Imágenes/Candado. Devuelve el Uri o null (Android 9 o menor: usar captura de pantalla). */
    fun saveToGallery(ctx: Context, bmp: Bitmap): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "candado_qr_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Candado")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return uri
    }
}
