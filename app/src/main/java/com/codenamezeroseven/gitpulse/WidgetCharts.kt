package com.codenamezeroseven.gitpulse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.time.LocalDate

/** Draws the widget charts into bitmaps (RemoteViews can't host custom views). */
object WidgetCharts {

    private const val ACCENT = 0xFF4DD9C4.toInt()
    private const val ACCENT_DIM = 0xFF2A6B63.toInt()
    private const val EMPTY = 0xFF2A2F33.toInt()
    private const val TEXT = 0xFF9AA4AB.toInt()

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
    }

    /** Mini bar chart of the last n days. */
    fun barChart(daily: Map<LocalDate, Int>, days: Int, showLabels: Boolean): Bitmap {
        val w = 720
        val h = 220
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val maxV = (1..days).maxOf { daily[LocalDate.now().minusDays((days - it).toLong())] ?: 0 }
            .coerceAtLeast(1)
        val values = (0 until days).map {
            daily[LocalDate.now().minusDays((days - 1 - it).toLong())] ?: 0
        }
        val slot = w.toFloat() / days
        val barW = slot * 0.6f
        val labelH = if (showLabels) 34f else 0f
        val chartH = h - labelH - 8f
        val barPaint = paint(ACCENT)
        val dimPaint = paint(ACCENT_DIM)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        values.forEachIndexed { i, v ->
            val bh = chartH * (v.toFloat() / maxV).coerceIn(0.03f, 1f)
            val left = i * slot + (slot - barW) / 2f
            c.drawRoundRect(left, 8f + chartH - bh, left + barW, 8f + chartH, barW / 2f, barW / 2f,
                if (v > 0) barPaint else dimPaint)
            if (showLabels) {
                val d = LocalDate.now().minusDays((days - 1 - i).toLong())
                val label = d.dayOfWeek.name.take(1)
                c.drawText(label, left + barW / 2f, h - 6f, textPaint)
            }
        }
        return bmp
    }

    /** Goal history grid: 5 weeks x 7 days, tinted by daily goal. */
    fun goalHeatmap(daily: Map<LocalDate, Int>, threshold: Int): Bitmap {
        val w = 720
        val h = 300
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val weeks = 5
        val start = LocalDate.now().minusDays((weeks * 7L) - 1)
        val cell = w / weeks.toFloat()
        val cellH = h / 7f
        val gap = cell * 0.14f
        val today = LocalDate.now()
        for (wk in 0 until weeks) {
            for (r in 0 until 7) {
                val date = start.plusDays(wk * 7L + r)
                if (date.isAfter(today)) continue
                val v = daily[date] ?: 0
                val p = when {
                    v >= threshold -> paint(ACCENT)
                    v > 0 -> paint(ACCENT_DIM)
                    else -> paint(EMPTY)
                }
                val cx = wk * cell + gap / 2f
                val cy = r * cellH + gap / 2f
                val rect = RectF(cx, cy, cx + cell - gap, cy + cellH - gap)
                c.drawRoundRect(rect, (cell - gap) * 0.35f, (cell - gap) * 0.35f, p)
            }
        }
        return bmp
    }
}
