package com.example.frigus_mobile.ui.conversation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.frigus_mobile.R

object AvatarUtils {

    private val PALETTE = intArrayOf(
        0xFF1E88E5.toInt(), // Blue
        0xFF43A047.toInt(), // Green
        0xFFFB8C00.toInt(), // Orange
        0xFF8E24AA.toInt(), // Purple
        0xFF00ACC1.toInt(), // Cyan
        0xFFE53935.toInt(), // Red
        0xFF3949AB.toInt(), // Indigo
        0xFF00897B.toInt()  // Teal
    )

    fun createAvatarDrawable(
        context: Context,
        name: String,
        sizeDp: Int = 56,
        isGroup: Boolean = false
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(24)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val colorIndex = Math.floorMod(name.hashCode(), PALETTE.size)
        val bgColor = PALETTE[colorIndex]

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }

        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, bgPaint)

        if (isGroup) {
            // Draw group icon centered
            val groupIcon = ContextCompat.getDrawable(context, R.drawable.ic_group)
            if (groupIcon != null) {
                val iconSize = (sizePx * 0.55f).toInt()
                val offset = (sizePx - iconSize) / 2
                groupIcon.setBounds(offset, offset, offset + iconSize, offset + iconSize)
                groupIcon.setTint(0xFFFFFFFF.toInt())
                groupIcon.draw(canvas)
                return BitmapDrawable(context.resources, bitmap)
            }
        }

        // Draw initials
        val initials = extractInitials(name)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = sizePx * 0.40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val fontMetrics = textPaint.fontMetrics
        val textY = radius - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(initials, radius, textY, textPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    private fun extractInitials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts[0].take(1) + parts[parts.size - 1].take(1)).uppercase()
        }
    }
}
