package com.example.frigus_mobile.ui.profile.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.frigus_mobile.R

class MoneySavingBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    var yLabels: List<String> = listOf("R$ 600", "R$ 400", "R$ 200", "R$ 0")
        set(value) {
            field = value
            invalidate()
        }

    var xLabels: List<String> = listOf("Dez", "Jan", "Fev", "Mar", "Abr", "Mai")
        set(value) {
            field = value
            invalidate()
        }

    // Normalized values between 0.0 and 1.0 for each bar
    var barValues: List<Float> = listOf(0.55f, 0.65f, 0.78f, 0.76f, 0.68f, 0.60f)
        set(value) {
            field = value
            invalidate()
        }

    var highlightLastBar: Boolean = true
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

    private val barPaintLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_chart_bar_light)
        style = Paint.Style.FILL
    }

    private val barPaintActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.blue_primary)
        style = Paint.Style.FILL
    }

    private val barRect = RectF()

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

        // 2. Draw Bars and X Labels
        val count = xLabels.size
        if (count == 0) return

        val slotWidth = chartWidth / count
        val barWidth = 16f * density
        val barRadius = 4f * density
        val baselineY = paddingTop + chartHeight

        for (i in 0 until count) {
            val slotCenterX = paddingLeft + i * slotWidth + slotWidth / 2f
            val label = xLabels[i]
            val textWidth = textPaint.measureText(label)
            canvas.drawText(label, slotCenterX - textWidth / 2f, h - 6f * density, textPaint)

            val norm = barValues.getOrElse(i) { 0.5f }.coerceIn(0f, 1f)
            val barHeight = chartHeight * norm
            val barTop = baselineY - barHeight
            val barLeft = slotCenterX - barWidth / 2f
            val barRight = slotCenterX + barWidth / 2f

            barRect.set(barLeft, barTop, barRight, baselineY)
            val isHighlighted = highlightLastBar && (i == count - 1)
            val paint = if (isHighlighted) barPaintActive else barPaintLight
            canvas.drawRoundRect(barRect, barRadius, barRadius, paint)
        }
    }
}
