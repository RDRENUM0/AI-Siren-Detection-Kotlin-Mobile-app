package com.example.sirenfinal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View


class VolumeMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.GREEN
    }
    private var level: Float = 0f

    fun updateLevel(newLevel: Float) {
        level = newLevel.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val barHeight = h * level
        canvas.drawRect(0f, h - barHeight, w, h, paint)
    }
}
