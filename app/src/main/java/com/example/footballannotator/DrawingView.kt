package com.example.footballannotator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PathEffect
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import com.example.footballannotator.model.Annotation
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin

/**
 * 自定义绘制层：圆圈 / 箭头 / 线条 + 选择(单删) / 观看。
 * 每条标注独立保存创建时的颜色与粗细，重绘时不变。
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    private val state: AnnotationState
) : View(context) {

    var currentTool: Tool = Tool.VIEW
    var paintColor: Int = Color.YELLOW
    var paintWidth: Float = 8f

    private var drawing: Annotation? = null
    var selectedIndex: Int = -1
        private set

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val selectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val handleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }

    private var handleCenter: PointF? = null
    private val HANDLE_R = 22f
    private val TOL = 24f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (currentTool) {
            Tool.VIEW -> false
            Tool.SELECT -> handleSelect(event)
            else -> handleDraw(event)
        }
    }

    private fun handleDraw(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                drawing = when (currentTool) {
                    Tool.CIRCLE -> Annotation.Circle(x, y, 0f, paintColor, paintWidth)
                    Tool.ARROW -> Annotation.Arrow(x, y, x, y, paintColor, paintWidth)
                    Tool.LINE -> Annotation.Line(x, y, x, y, paintColor, paintWidth)
                    else -> null
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (val d = drawing) {
                    is Annotation.Circle -> d.radius = hypot(x - d.cx, y - d.cy)
                    is Annotation.Arrow -> { d.x2 = x; d.y2 = y }
                    is Annotation.Line -> { d.x2 = x; d.y2 = y }
                    null -> {}
                }
            }
            MotionEvent.ACTION_UP -> {
                val d = drawing
                if (d != null && isValid(d)) state.add(d)
                drawing = null
            }
        }
        invalidate()
        return true
    }

    private fun isValid(a: Annotation): Boolean = when (a) {
        is Annotation.Circle -> a.radius >= 6f
        is Annotation.Arrow -> hypot(a.x2 - a.x1, a.y2 - a.y1) >= 6f
        is Annotation.Line -> hypot(a.x2 - a.x1, a.y2 - a.y1) >= 6f
    }

    private fun handleSelect(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y
            // 先判断是否点中已选中标注的删除手柄
            val h = handleCenter
            if (selectedIndex >= 0 && h != null && dist(x, y, h.x, h.y) <= HANDLE_R) {
                deleteSelected()
                return true
            }
            selectedIndex = hitTest(x, y)
            recomputeHandle()
            invalidate()
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): Int {
        for (i in state.annotations.lastIndex downTo 0) {
            val a = state.annotations[i]
            val hit = when (a) {
                is Annotation.Circle -> dist(x, y, a.cx, a.cy) <= a.radius + TOL
                is Annotation.Arrow -> distToSeg(x, y, a.x1, a.y1, a.x2, a.y2) <= TOL
                is Annotation.Line -> distToSeg(x, y, a.x1, a.y1, a.x2, a.y2) <= TOL
            }
            if (hit) return i
        }
        return -1
    }

    private fun recomputeHandle() {
        handleCenter = if (selectedIndex >= 0) {
            val b = bounds(state.annotations[selectedIndex])
            PointF(b.maxX + HANDLE_R + 4f, b.minY - HANDLE_R - 4f)
        } else null
    }

    private fun bounds(a: Annotation): Bounds = when (a) {
        is Annotation.Circle ->
            Bounds(a.cx - a.radius, a.cy - a.radius, a.cx + a.radius, a.cy + a.radius)
        is Annotation.Arrow ->
            Bounds(min(a.x1, a.x2), min(a.y1, a.y2), max(a.x1, a.x2), max(a.y1, a.y2))
        is Annotation.Line ->
            Bounds(min(a.x1, a.x2), min(a.y1, a.y2), max(a.x1, a.x2), max(a.y1, a.y2))
    }

    fun deleteSelected(): Boolean {
        if (selectedIndex >= 0) {
            state.removeAt(selectedIndex)
            selectedIndex = -1
            handleCenter = null
            invalidate()
            return true
        }
        return false
    }

    fun clearAll() {
        state.clearAll()
        selectedIndex = -1
        handleCenter = null
        invalidate()
    }

    fun undo() {
        state.undo()
        selectedIndex = -1
        handleCenter = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        for ((i, a) in state.annotations.withIndex()) {
            paint.color = a.color
            paint.strokeWidth = a.strokeWidth
            drawAnnotation(canvas, a)
            if (i == selectedIndex) drawSelection(canvas, a)
        }
        drawing?.let {
            paint.color = it.color
            paint.strokeWidth = it.strokeWidth
            drawAnnotation(canvas, it)
        }
    }

    private fun drawAnnotation(c: Canvas, a: Annotation) {
        when (a) {
            is Annotation.Circle -> c.drawCircle(a.cx, a.cy, a.radius, paint)
            is Annotation.Line -> c.drawLine(a.x1, a.y1, a.x2, a.y2, paint)
            is Annotation.Arrow -> {
                c.drawLine(a.x1, a.y1, a.x2, a.y2, paint)
                drawArrowHead(c, a.x1, a.y1, a.x2, a.y2, paint)
            }
        }
    }

    private fun drawArrowHead(
        c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, p: Paint
    ) {
        val angle = kotlin.math.atan2(y2 - y1, x2 - x1)
        val len = 18f + p.strokeWidth * 2f
        val a1 = angle + Math.toRadians(25.0)
        val a2 = angle - Math.toRadians(25.0)
        c.drawLine(x2, y2, (x2 - len * cos(a1)).toFloat(), (y2 - len * sin(a1)).toFloat(), p)
        c.drawLine(x2, y2, (x2 - len * cos(a2)).toFloat(), (y2 - len * sin(a2)).toFloat(), p)
    }

    private fun drawSelection(c: Canvas, a: Annotation) {
        val b = bounds(a)
        c.drawRect(b.minX - 8, b.minY - 8, b.maxX + 8, b.maxY + 8, selectPaint)
        handleCenter?.let { h ->
            c.drawCircle(h.x, h.y, HANDLE_R, handlePaint)
            c.drawText("X", h.x, h.y + 9f, handleTextPaint)
        }
    }

    private fun dist(x: Float, y: Float, cx: Float, cy: Float) = hypot(x - cx, y - cy).toFloat()

    private fun distToSeg(
        px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return dist(px, py, x1, y1)
        var t = ((px - x1) * dx + (py - y1) * dy) / len2
        t = t.coerceIn(0f, 1f)
        return dist(px, py, x1 + t * dx, y1 + t * dy)
    }

    private data class Bounds(
        val minX: Float, val minY: Float, val maxX: Float, val maxY: Float
    )
}
