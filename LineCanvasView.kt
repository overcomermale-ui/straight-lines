package com.example.straightline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

class LineCanvasView(c: Context) : View(c) {
    private class Ln(val x1: Float, val y1: Float, var x2: Float, var y2: Float)

    private val lines = ArrayList<Ln>()
    private var cur: Ln? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE24B4A.toInt()
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    fun clearAll() { lines.clear(); cur = null; invalidate() }
    fun undo() { if (lines.isNotEmpty()) lines.removeAt(lines.size - 1); invalidate() }

    override fun onDraw(canvas: Canvas) {
        for (l in lines) canvas.drawLine(l.x1, l.y1, l.x2, l.y2, paint)
        cur?.let { canvas.drawLine(it.x1, it.y1, it.x2, it.y2, paint) }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> cur = Ln(e.x, e.y, e.x, e.y)
            MotionEvent.ACTION_MOVE -> cur?.let { l ->
                var x = e.x; var y = e.y
                val dx = x - l.x1; val dy = y - l.y1
                val d = hypot(dx, dy)
                if (d > 1f) {
                    // snap to 0/45/90... degrees when within ~8 degrees
                    val a = atan2(dy, dx)
                    val step = (Math.PI / 4).toFloat()
                    val s = round(a / step) * step
                    if (abs(a - s) < 0.14f) { x = l.x1 + cos(s) * d; y = l.y1 + sin(s) * d }
                }
                l.x2 = x; l.y2 = y; invalidate()
            }
            MotionEvent.ACTION_UP -> {
                cur?.let { if (hypot(it.x2 - it.x1, it.y2 - it.y1) > 10f) lines.add(it) }
                cur = null; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { cur = null; invalidate() }
        }
        return true
    }
}
