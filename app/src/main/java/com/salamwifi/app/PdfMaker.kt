package com.salamwifi.app

import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

object PdfMaker {
    private const val W = 595
    private const val H = 842
    private val TEAL = 0xFF0B6E75.toInt()
    private val ORANGE = 0xFFFF9F1C.toInt()
    private val DARK = 0xFF16262E.toInt()
    private val ORANGE_D = 0xFFC76A00.toInt()

    private fun paint(col: Int, st: Paint.Style = Paint.Style.FILL, sw: Float = 1f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = st; strokeWidth = sw }

    fun make(file: File, vouchers: List<Voucher>, dur: String, host: String) {
        val doc = PdfDocument()
        val cols = 4; val rows = 10
        val mx = 18f; val my = 21f
        val cw = (W - 2 * mx) / cols; val ch = (H - 2 * my) / rows
        vouchers.chunked(cols * rows).forEachIndexed { pi, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pi + 1).create())
            chunk.forEachIndexed { i, v -> draw(page.canvas, mx + (i % cols) * cw, my + (i / cols) * ch, cw, ch, v, dur, host) }
            doc.finishPage(page)
        }
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    private fun draw(c: Canvas, x: Float, y: Float, w: Float, h: Float, v: Voucher, dur: String, host: String) {
        c.drawRect(x, y, x + w, y + h, paint(0xFFB0BEC5.toInt(), Paint.Style.STROKE, 0.5f).apply {
            pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f) })
        val r = RectF(x + 4, y + 4, x + w - 4, y + h - 4)
        val shape = Path().apply { addRoundRect(r, 7f, 7f, Path.Direction.CW) }
        val hh = 17f
        c.save(); c.clipPath(shape)
        c.drawRect(r, paint(Color.WHITE))
        c.drawRect(r.left, r.top, r.right, r.top + hh, paint(TEAL))
        c.drawRect(r.left, r.top + hh, r.right, r.top + hh + 2f, paint(ORANGE))
        c.restore()
        c.drawPath(shape, paint(TEAL, Paint.Style.STROKE, 1f))

        val cx = r.left + 11f; val cy = r.top + 12.5f
        val wp = paint(Color.WHITE, Paint.Style.STROKE, 1.3f).apply { strokeCap = Paint.Cap.ROUND }
        listOf(3f, 6f, 9f).forEach { rad -> c.drawArc(RectF(cx - rad, cy - rad, cx + rad, cy + rad), 225f, 90f, false, wp) }
        c.drawCircle(cx, cy, 1.2f, paint(Color.WHITE))
        c.drawText("SALAM-WIFI", r.left + 24f, r.top + 11.5f, paint(Color.WHITE).apply {
            textSize = 8.5f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) })
        c.drawText(dur, r.right - 6f, r.top + 11.5f, paint(Color.WHITE).apply {
            textSize = 7f; textAlign = Paint.Align.RIGHT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) })

        val qs = 40f
        val qx = r.right - 6f - qs
        val base = r.top + hh + 2f
        val qy = base + (r.bottom - base - qs) / 2f
        val url = "http://$host/login?username=${v.user}&password=${v.pass}"
        val m = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L))
        val cell = qs / m.width
        val dark = paint(DARK)
        for (i in 0 until m.width) for (j in 0 until m.height) if (m.get(i, j))
            c.drawRect(qx + i * cell, qy + j * cell, qx + (i + 1) * cell + 0.15f, qy + (j + 1) * cell + 0.15f, dark)

        val px = r.left + 7f
        val avail = qx - px - 4f
        val lab = paint(0xFF78909C.toInt()).apply { textSize = 5.5f }
        fun value(t: String, col: Int) = paint(col).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = minOf(10.5f, avail / (t.length * 0.6f))
        }
        c.drawText("UTILISATEUR", px, base + 10f, lab)
        c.drawText(v.user, px, base + 21f, value(v.user, DARK))
        c.drawText("MOT DE PASSE", px, base + 32f, lab)
        c.drawText(v.pass, px, base + 43f, value(v.pass, ORANGE_D))
    }
}
