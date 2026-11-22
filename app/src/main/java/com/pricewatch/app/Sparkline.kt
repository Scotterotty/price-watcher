package com.pricewatch.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/** Draws a small glowing price-trend line, used by both the app and the widget. */
object Sparkline {

    fun render(prices: List<Double>, widthPx: Int, heightPx: Int, atLow: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        if (prices.size < 2) return bmp
        val c = Canvas(bmp)
        val pts = prices.takeLast(60)
        val min = pts.min()
        val max = pts.max()
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val pad = heightPx * 0.15f
        val stroke = (heightPx / 14f).coerceAtLeast(2f)

        fun x(i: Int) = stroke + i * (widthPx - 2 * stroke) / (pts.size - 1)
        fun y(v: Double) = (pad + (1 - (v - min) / range) * (heightPx - 2 * pad)).toFloat()

        val line = Path().apply {
            moveTo(x(0), y(pts[0]))
            for (i in 1 until pts.size) lineTo(x(i), y(pts[i]))
        }
        val fill = Path(line).apply {
            lineTo(x(pts.size - 1), heightPx.toFloat())
            lineTo(x(0), heightPx.toFloat())
            close()
        }

        val start = if (atLow) Theme.GREEN else Theme.CYAN
        val end = if (atLow) Theme.GREEN else Theme.VIOLET
        c.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, heightPx.toFloat(), (start and 0x00FFFFFF) or 0x55000000, 0, Shader.TileMode.CLAMP)
        })
        // Soft glow underneath, then the crisp line.
        c.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke * 3
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = (start and 0x00FFFFFF) or 0x33000000
        })
        c.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            shader = LinearGradient(0f, 0f, widthPx.toFloat(), 0f, start, end, Shader.TileMode.CLAMP)
        })
        // Dot on the latest price.
        c.drawCircle(x(pts.size - 1), y(pts.last()), stroke * 1.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = end })
        return bmp
    }
}

object Theme {
    const val BG = 0xFF0B0F1A.toInt()
    const val CARD = 0xFF151B2B.toInt()
    const val CARD_LOW = 0xFF0F2A1F.toInt()
    const val TEXT = 0xFFF2F5FF.toInt()
    const val MUTED = 0xFF8A93AD.toInt()
    const val CYAN = 0xFF4DE1FF.toInt()
    const val VIOLET = 0xFF8B5CF6.toInt()
    const val GREEN = 0xFF39FF88.toInt()
    const val RED = 0xFFFF6B81.toInt()
}
