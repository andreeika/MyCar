package com.example.mycar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class QrOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#99000000")
    }

    private val cornerPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val scanRect = RectF()
    private val cornerLen = 48f  // длина уголка в px

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val size = minOf(w, h) * 0.65f
        val cx = w / 2f
        val cy = h / 2f

        scanRect.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)

        // Затемнение вокруг квадрата
        canvas.drawRect(0f, 0f, w, scanRect.top, dimPaint)
        canvas.drawRect(0f, scanRect.top, scanRect.left, scanRect.bottom, dimPaint)
        canvas.drawRect(scanRect.right, scanRect.top, w, scanRect.bottom, dimPaint)
        canvas.drawRect(0f, scanRect.bottom, w, h, dimPaint)

        // Четыре угловых уголка
        val l = scanRect.left
        val t = scanRect.top
        val r = scanRect.right
        val b = scanRect.bottom
        val cl = cornerLen

        // Верхний левый
        canvas.drawLine(l, t, l + cl, t, cornerPaint)
        canvas.drawLine(l, t, l, t + cl, cornerPaint)
        // Верхний правый
        canvas.drawLine(r - cl, t, r, t, cornerPaint)
        canvas.drawLine(r, t, r, t + cl, cornerPaint)
        // Нижний левый
        canvas.drawLine(l, b - cl, l, b, cornerPaint)
        canvas.drawLine(l, b, l + cl, b, cornerPaint)
        // Нижний правый
        canvas.drawLine(r - cl, b, r, b, cornerPaint)
        canvas.drawLine(r, b - cl, r, b, cornerPaint)
    }
}
