package com.example.frigus_mobile.ui.profile.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.frigus_mobile.R

class MoneySavingDonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    data class DonutSlice(val color: Int, val percentage: Float)

    var slices: List<DonutSlice> = listOf(
        DonutSlice(ContextCompat.getColor(context, R.color.color_category_meat), 36f),
        DonutSlice(ContextCompat.getColor(context, R.color.color_category_dairy), 26f),
        DonutSlice(ContextCompat.getColor(context, R.color.color_category_produce), 19f),
        DonutSlice(ContextCompat.getColor(context, R.color.color_category_beverage), 12f),
        DonutSlice(ContextCompat.getColor(context, R.color.color_category_other), 7f)
    )
        set(value) {
            field = value
            invalidate()
        }

    private val strokeWidthDp = 20f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthDp
    }

    private val rectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val size = minOf(w, h)
        val halfStroke = strokeWidthDp / 2f
        val left = (w - size) / 2f + halfStroke + 4f * density
        val top = (h - size) / 2f + halfStroke + 4f * density
        val right = left + size - strokeWidthDp - 8f * density
        val bottom = top + size - strokeWidthDp - 8f * density
        rectF.set(left, top, right, bottom)

        val totalPercentage = slices.sumOf { it.percentage.toDouble() }.toFloat().coerceAtLeast(1f)
        var startAngle = -90f

        for (slice in slices) {
            val sweepAngle = (slice.percentage / totalPercentage) * 360f
            paint.color = slice.color
            canvas.drawArc(rectF, startAngle, sweepAngle, false, paint)
            startAngle += sweepAngle
        }
    }
}
