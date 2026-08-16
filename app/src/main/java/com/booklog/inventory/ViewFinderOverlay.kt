package com.booklog.inventory

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class ViewFinderOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    
    private val framePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val shadowPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val clearXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val frameRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // Calculate viewfinder size (60% of smallest dimension)
        val viewfinderSize = minOf(width, height) * 0.6f
        val left = (width - viewfinderSize) / 2
        val top = (height - viewfinderSize) / 2
        val right = left + viewfinderSize
        val bottom = top + viewfinderSize
        
        // Draw semi-transparent black overlay
        canvas.drawRect(0f, 0f, width, height, shadowPaint)
        
        // Clear the viewfinder area
        shadowPaint.xfermode = clearXfermode
        canvas.drawRect(left, top, right, bottom, shadowPaint)
        shadowPaint.xfermode = null
        
        // Draw viewfinder frame
        frameRect.set(left, top, right, bottom)
        canvas.drawRect(frameRect, framePaint)
        
        // Draw corner brackets
        val cornerLength = viewfinderSize * 0.15f
        
        // Top-left
        canvas.drawLine(left, top, left + cornerLength, top, cornerPaint)
        canvas.drawLine(left, top, left, top + cornerLength, cornerPaint)
        
        // Top-right
        canvas.drawLine(right, top, right - cornerLength, top, cornerPaint)
        canvas.drawLine(right, top, right, top + cornerLength, cornerPaint)
        
        // Bottom-left
        canvas.drawLine(left, bottom, left + cornerLength, bottom, cornerPaint)
        canvas.drawLine(left, bottom, left, bottom - cornerLength, cornerPaint)
        
        // Bottom-right
        canvas.drawLine(right, bottom, right - cornerLength, bottom, cornerPaint)
        canvas.drawLine(right, bottom, right, bottom - cornerLength, cornerPaint)
    }
}
