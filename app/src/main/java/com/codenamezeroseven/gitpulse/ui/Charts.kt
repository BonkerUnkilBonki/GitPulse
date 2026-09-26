package com.codenamezeroseven.gitpulse.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var labels: List<String> = emptyList()
    var values: List<Int> = emptyList()
    var goal: Int = 0

    private val density = context.resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barDimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * density
    }

    fun setData(labels: List<String>, values: List<Int>, goal: Int) {
        this.labels = labels
        this.values = values
        this.goal = goal
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return
        barPaint.color = MaterialColors.getColor(context, MaterialR.attr.colorPrimary, "p")
        barDimPaint.color = MaterialColors.getColor(context, MaterialR.attr.colorPrimaryContainer, "p")
        todayPaint.color = MaterialColors.getColor(context, MaterialR.attr.colorTertiary, "t")
        goalPaint.color = MaterialColors.getColor(context, MaterialR.attr.colorOutline, "o")
        textPaint.color = MaterialColors.getColor(context, MaterialR.attr.colorOnSurfaceVariant, "v")
        labelPaint.color = MaterialColors.getColor(context, MaterialR.attr.colorOnSurfaceVariant, "v")

        val n = values.size
        val maxV = max((values.maxOrNull() ?: 0) + 1, goal + 1).coerceAtLeast(1)
        val labelH = 14f * density
        val topPad = 16f * density
        val chartH = height - labelH - topPad
        val slot = width.toFloat() / n
        val barW = slot * 0.52f
        val radius = barW / 2f

        for (i in values.indices) {
            val v = values[i]
            val h = chartH * (v.toFloat() / maxV)
            val left = i * slot + (slot - barW) / 2f
            val top = topPad + (chartH - h)
            val right = left + barW
            val bottom = topPad + chartH
            val paint = when {
                i == n - 1 -> todayPaint
                goal > 0 && v >= goal -> barPaint
                goal > 0 -> barDimPaint
                else -> barPaint
            }
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
            if (v > 0) canvas.drawText(v.toString(), left + barW / 2f, top - 4f * density, textPaint)
            canvas.drawText(labels.getOrElse(i) { "" }, left + barW / 2f, height - 2f * density, labelPaint)
        }

        if (goal > 0 && goal <= maxV) {
            val y = topPad + chartH * (1f - goal.toFloat() / maxV)
            canvas.drawLine(0f, y, width.toFloat(), y, goalPaint)
        }
    }
}

class HeatmapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var daily: Map<LocalDate, Int> = emptyMap()
        private set
    var weeks: Int = 26
        private set
    var threshold: Int = 0
        private set

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var cellSize = 12f
    private var gap = 2f
    private var start: LocalDate = LocalDate.now()

    fun setData(daily: Map<LocalDate, Int>, weeks: Int, threshold: Int = 0) {
        this.daily = daily
        this.weeks = weeks
        this.threshold = threshold
        this.start = LocalDate.now().minusDays((weeks * 7L) - 1)
        requestLayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSize = w.toFloat() / weeks
        gap = min(6f, cellSize * 0.18f)
    }

    override fun onDraw(canvas: Canvas) {
        val primary = MaterialColors.getColor(context, MaterialR.attr.colorPrimary, "p")
        val emptyColor = MaterialColors.getColor(context, MaterialR.attr.colorSurfaceVariant, "s")
        val maxV = (daily.values.maxOrNull() ?: 0).coerceAtLeast(1)
        val today = LocalDate.now()
        for (w in 0 until weeks) {
            for (r in 0 until 7) {
                val date = start.plusDays(w * 7L + r)
                if (date.isAfter(today)) continue
                val v = daily[date] ?: 0
                if (threshold > 0) {
                    paint.color = if (v >= threshold) primary else emptyColor
                } else {
                    when {
                        v == 0 -> {
                            paint.color = emptyColor
                            paint.alpha = 70
                        }
                        else -> {
                            paint.color = primary
                            val frac = (v.toFloat() / maxV).coerceIn(0.2f, 1f)
                            paint.alpha = (frac * 255).toInt().coerceAtLeast(90)
                        }
                    }
                }
                val cx = w * cellSize + gap / 2f
                val cy = r * cellSize + gap / 2f
                rect.set(cx, cy, cx + cellSize - gap, cy + cellSize - gap)
                canvas.drawRoundRect(rect, cellSize * 0.3f, cellSize * 0.3f, paint)
            }
        }
        paint.alpha = 255
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && cellSize > 0) {
            val w = (event.x / cellSize).toInt()
            val r = (event.y / cellSize).toInt()
            if (w in 0 until weeks && r in 0 until 7) {
                val date = start.plusDays(w * 7L + r)
                if (!date.isAfter(LocalDate.now())) {
                    val v = daily[date] ?: 0
                    val extra = if (threshold > 0) {
                        if (v >= threshold) " — goal met" else " — goal missed"
                    } else ""
                    Toast.makeText(
                        context,
                        date.format(DateTimeFormatter.ofPattern("d MMM")) + " · $v commit${if (v == 1) "" else "s"}$extra",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        return true
    }
}

class RingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress = 0f
    private var anim: ValueAnimator? = null
    private val density = context.resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun setProgress(p: Float, animate: Boolean = true) {
        val target = p.coerceIn(0f, 1f)
        anim?.cancel()
        if (!animate) {
            progress = target
            invalidate()
            return
        }
        anim = ValueAnimator.ofFloat(progress, target).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                progress = a.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = 10f * density
        trackPaint.strokeWidth = stroke
        sweepPaint.strokeWidth = stroke
        trackPaint.color = MaterialColors.getColor(context, MaterialR.attr.colorSurfaceVariant, "s")
        sweepPaint.color = MaterialColors.getColor(context, MaterialR.attr.colorPrimary, "p")
        val inset = stroke / 2f + 1f
        val d = min(width, height).toFloat() - inset * 2f
        val rect = RectF(inset, (height - d) / 2f, inset + d, (height + d) / 2f)
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        if (progress > 0.01f) canvas.drawArc(rect, -90f, 360f * progress, false, sweepPaint)
    }
}
