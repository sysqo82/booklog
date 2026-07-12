package com.booklog.inventory

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor

class SideIndexView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var letters = listOf<Char>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    
    private var itemHeight: Float = 0f
    var onLetterSelected: ((Char) -> Unit)? = null

    fun setLetters(newLetters: List<Char>) {
        this.letters = newLetters
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (letters.isEmpty()) return

        // Draw background
        canvas.drawColor(Color.parseColor("#EEEEEE"))

        itemHeight = height.toFloat() / letters.size
        for (i in letters.indices) {
            val x = width / 2f
            val y = (i * itemHeight) + (itemHeight / 2f) - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText(letters[i].toString(), x, y, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val index = floor(event.y / itemHeight).toInt()
                if (index in letters.indices) {
                    onLetterSelected?.invoke(letters[index])
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
