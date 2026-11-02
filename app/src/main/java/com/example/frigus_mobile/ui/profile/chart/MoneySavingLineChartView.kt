package com.example.frigus_mobile.ui.profile.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.frigus_mobile.R

class MoneySavingLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    var chartColor: Int = ContextCompat.getColor(context, R.color.blue_primary)
        set(value) {
            field = value
            linePaint.color = value
            pointPaint.color = value
            invalidate()
        }

    var yLabels: List<String> = listOf("R$ 120", "R$ 80", "R$ 40", "R$ 0")
        set(value) {
            field = value
            invalidate()
        }

    var xLabels: List<String> = listOf("Dez", "Jan", "Fev", "Mar", "Abr", "Mai")
        set(value) {
            field = value
            invalidate()
        }

    // Normalized points between 0.0 (bottom) and 1.0 (top)
    var points: List<Float> = listOf(0.25f, 0.40f, 0.55f, 0.45f, 0.65f, 0.85f)
        set(value) {
            field = value
            invalidate()
        }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_chart_grid)
        strokeWidth = 1f * density
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_text_secondary)
        textSize = 10f * density
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = chartColor
        strokeWidth = 2.5f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = chartColor
        strokeWidth = 2f * density
        style = Paint.Style.STROKE
    }

    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pure_white)
        style = Paint.Style.FILL
    }

    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val paddingLeft = 46f * density
        val paddingRight = 16f * density
        val paddingTop = 14f * density
        val paddingBottom = 24f * density

        val chartWidth = w - paddingLeft - paddingRight
        val chartHeight = h - paddingTop - paddingBottom
        if (chartWidth <= 0 || chartHeight <= 0) return

        // 1. Draw Grid Lines and Y Labels
        val yCount = yLabels.size
        for (i in 0 until yCount) {
            val y = paddingTop + (chartHeight * i / (yCount - 1).coerceAtLeast(1))
            canvas.drawLine(paddingLeft, y, w - paddingRight, y, gridPaint)
            val label = yLabels[i]
            canvas.drawText(label, 6f * density, y + 3.5f * density, textPaint)
        }

        // 2. Draw X Labels
        val xCount = xLabels.size
        val xStep = chartWidth / (xCount - 1).coerceAtLeast(1)
        for (i in 0 until xCount) {
            val x = paddingLeft + i * xStep
            val label = xLabels[i]
            val textWidth = textPaint.measureText(label)
            canvas.drawText(label, x - textWidth / 2f, h - 6f * density, textPaint)
        }

        // 3. Draw Line and Points
        if (points.isNotEmpty()) {
            val count = points.size
            val step = chartWidth / (count - 1).coerceAtLeast(1)
            path.reset()

            val coords = mutableListOf<Pair<Float, Float>>()
            for (i in 0 until count) {
                val x = paddingLeft + i * step
                val norm = points[i].coerceIn(0f, 1f)
                val y = paddingTop + chartHeight * (1f - norm)
                coords.add(Pair(x, y))
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, linePaint)

            // Draw circular points with white center
            val radius = 3.5f * density
            for ((x, y) in coords) {
                canvas.drawCircle(x, y, radius, whitePaint)
                canvas.drawCircle(x, y, radius, pointPaint)
            }
        }
    }
}
