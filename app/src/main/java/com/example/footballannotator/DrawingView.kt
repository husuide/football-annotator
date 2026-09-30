package com.example.footballannotator

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.example.footballannotator.model.Annotation
import com.example.footballannotator.model.ArrowStyle
import com.example.footballannotator.model.LineStyle
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin

/**
 * 自定义绘制层：圆圈 / 实线&虚线&波浪箭头 / 实线&虚线线条 + 选择(单删+端点微调) / 观看。
 * 每条标注独立保存创建时的颜色、粗细与样式，重绘时不变。
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
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val handleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val endpointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00BFFF")
        style = Paint.Style.FILL
    }
    private val endpointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val path = Path()

    private var handleCenter: PointF? = null
    private val endpoints = mutableListOf<PointF>()
    private var draggingEndpoint = -1   // 0=起点 1=终点
    private var draggingAnnotationIndex = -1

    private val HANDLE_R = 22f
    private val ENDPOINT_R = 18f
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
                    Tool.ARROW -> Annotation.Arrow(x, y, x, y, ArrowStyle.SOLID, paintColor, paintWidth)
                    Tool.DASHED_ARROW -> Annotation.Arrow(x, y, x, y, ArrowStyle.DASHED, paintColor, paintWidth)
                    Tool.WAVY_ARROW -> Annotation.Arrow(x, y, x, y, ArrowStyle.WAVY, paintColor, paintWidth)
                    Tool.LINE -> Annotation.Line(x, y, x, y, LineStyle.SOLID, paintColor, paintWidth)
                    Tool.DASHED_LINE -> Annotation.Line(x, y, x, y, LineStyle.DASHED, paintColor, paintWidth)
                    else -> null
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (val d = drawing) {
                    is Annotation.Circle -> d.radius = hypot(x - d.cx, y - d.cy).toFloat()
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
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 优先判断删除手柄
                val h = handleCenter
                if (selectedIndex >= 0 && h != null && dist(x, y, h.x, h.y) <= HANDLE_R) {
                    deleteSelected()
                    return true
                }
                // 其次判断端点手柄
                val epIdx = findEndpointHandle(x, y)
                if (epIdx >= 0) {
                    draggingEndpoint = epIdx
                    draggingAnnotationIndex = selectedIndex
                    return true
                }
                // 否则做命中测试
                selectedIndex = hitTest(x, y)
                recomputeHandle()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingEndpoint >= 0 && draggingAnnotationIndex >= 0) {
                    val a = state.annotations.getOrNull(draggingAnnotationIndex)
                    if (a is Annotation.Arrow) {
                        if (draggingEndpoint == 0) { a.x1 = x; a.y1 = y }
                        else { a.x2 = x; a.y2 = y }
                    } else if (a is Annotation.Line) {
                        if (draggingEndpoint == 0) { a.x1 = x; a.y1 = y }
                        else { a.x2 = x; a.y2 = y }
                    }
                    recomputeHandle()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingEndpoint = -1
                draggingAnnotationIndex = -1
            }
        }
        return true
    }

    private fun findEndpointHandle(x: Float, y: Float): Int {
        if (selectedIndex < 0) return -1
        endpoints.forEachIndexed { idx, p ->
            if (dist(x, y, p.x, p.y) <= ENDPOINT_R + 6f) return idx
        }
        return -1
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
        endpoints.clear()
        handleCenter = if (selectedIndex >= 0) {
            val a = state.annotations[selectedIndex]
            when (a) {
                is Annotation.Arrow -> {
                    val b = bounds(a)
                    endpoints.add(PointF(a.x1, a.y1))
                    endpoints.add(PointF(a.x2, a.y2))
                    PointF(b.maxX + HANDLE_R + 4f, b.minY - HANDLE_R - 4f)
                }
                is Annotation.Line -> {
                    val b = bounds(a)
                    endpoints.add(PointF(a.x1, a.y1))
                    endpoints.add(PointF(a.x2, a.y2))
                    PointF(b.maxX + HANDLE_R + 4f, b.minY - HANDLE_R - 4f)
                }
                is Annotation.Circle -> {
                    val b = bounds(a)
                    PointF(b.maxX + HANDLE_R + 4f, b.minY - HANDLE_R - 4f)
                }
            }
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
            endpoints.clear()
            invalidate()
            return true
        }
        return false
    }

    fun clearAll() {
        state.clearAll()
        selectedIndex = -1
        handleCenter = null
        endpoints.clear()
        invalidate()
    }

    fun undo() {
        state.undo()
        selectedIndex = -1
        handleCenter = null
        endpoints.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        for ((i, a) in state.annotations.withIndex()) {
            paint.color = a.color
            paint.strokeWidth = a.strokeWidth
            paint.pathEffect = null
            drawAnnotation(canvas, a)
            if (i == selectedIndex) drawSelection(canvas, a)
        }
        drawing?.let {
            paint.color = it.color
            paint.strokeWidth = it.strokeWidth
            paint.pathEffect = null
            drawAnnotation(canvas, it)
        }
    }

    private fun drawAnnotation(c: Canvas, a: Annotation) {
        when (a) {
            is Annotation.Circle -> c.drawCircle(a.cx, a.cy, a.radius, paint)
            is Annotation.Line -> {
                applyLineStyle(a.style)
                c.drawLine(a.x1, a.y1, a.x2, a.y2, paint)
                paint.pathEffect = null
            }
            is Annotation.Arrow -> {
                applyArrowStyle(a.style)
                when (a.style) {
                    ArrowStyle.WAVY -> drawWavyLine(c, a.x1, a.y1, a.x2, a.y2, paint)
                    else -> c.drawLine(a.x1, a.y1, a.x2, a.y2, paint)
                }
                paint.pathEffect = null
                drawArrowHead(c, a.x1, a.y1, a.x2, a.y2, paint)
            }
        }
    }

    private fun applyLineStyle(style: LineStyle) {
        paint.pathEffect = when (style) {
            LineStyle.SOLID -> null
            LineStyle.DASHED -> DashPathEffect(floatArrayOf(18f, 12f), 0f)
        }
    }

    private fun applyArrowStyle(style: ArrowStyle) {
        paint.pathEffect = when (style) {
            ArrowStyle.SOLID -> null
            ArrowStyle.DASHED -> DashPathEffect(floatArrayOf(18f, 12f), 0f)
            ArrowStyle.WAVY -> null
        }
    }

    private fun drawWavyLine(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, p: Paint) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = hypot(dx, dy).toFloat()
        if (len < 1f) return
        val steps = max(2, (len / 12f).toInt())
        path.reset()
        path.moveTo(x1, y1)
        val nx = -dy / len
        val ny = dx / len
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val bx = x1 + dx * t
            val by = y1 + dy * t
            val wave = if (i % 2 == 0) p.strokeWidth * 1.5f else -p.strokeWidth * 1.5f
            path.lineTo(bx + nx * wave, by + ny * wave)
        }
        c.drawPath(path, p)
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
            c.drawCircle(h.x, h.y, HANDLE_R, handleStrokePaint)
            c.drawText("X", h.x, h.y + 9f, handleTextPaint)
        }
        // 箭头/线条的端点微调手柄
        if (a is Annotation.Arrow || a is Annotation.Line) {
            endpoints.forEach { p ->
                c.drawCircle(p.x, p.y, ENDPOINT_R, endpointPaint)
                c.drawCircle(p.x, p.y, ENDPOINT_R, endpointStrokePaint)
            }
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

    // 颜色块点击动画：放大 + 白环高亮
    fun animateColorSelection(view: View) {
        view.animate().cancel()
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.35f, 1.2f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.35f, 1.2f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 250
            interpolator = OvershootInterpolator(1.5f)
            start()
        }
    }

    private data class Bounds(
        val minX: Float, val minY: Float, val maxX: Float, val maxY: Float
    )
}
