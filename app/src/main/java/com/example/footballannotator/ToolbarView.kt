package com.example.footballannotator

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.toColorInt

/**
 * 浮窗工具栏：可拖拽标题栏 + 可折叠面板（整体已缩小，更适合手机屏幕）。
 * 标题栏右侧常驻「展开/收起」按钮，因此面板收起后仍可重新打开。
 * 按钮均使用 selector/圆角背景，提供按压质感。
 * 颜色块点击时有放大动画 + 白色高亮环，直观显示当前选中色。
 */
class ToolbarView @JvmOverloads constructor(
    context: Context,
    private val state: AnnotationState,
    private val drawView: DrawingView,
    private val service: FloatingAnnotationService
) : LinearLayout(context) {

    private lateinit var header: DragHeader
    private val title = TextView(context).apply {
        text = "足球标注 · 拖动我"
        setTextColor(Color.WHITE)
        textSize = 12f
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        isClickable = false
        isFocusable = false
    }
    private val toggleBtn = TextView(context).apply {
        text = "收起"
        setTextColor(Color.WHITE)
        textSize = 11f
        setPadding(10, 5, 10, 5)
        background = context.getDrawable(R.drawable.button_bg)
        isClickable = false
        isFocusable = false
    }
    private val panel = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(8, 8, 8, 8)
        background = context.getDrawable(R.drawable.toolbar_panel_bg)
    }
    private var collapsed = false

    private val colorViews = mutableListOf<Pair<String, FrameLayout>>()
    private var selectedColorHex = "#FFFF00"

    init {
        orientation = VERTICAL
        header = DragHeader(context, title, toggleBtn, onToggle = { togglePanel() }, onDoubleTapTitle = { togglePanel() })
        header.addView(title)
        header.addView(toggleBtn)
        addView(header)
        addView(panel)
        buildPanel()
        setupDrag()
        highlightColor("#FFFF00")
    }

    private fun buildPanel() {
        val toolSpecs = listOf(
            "圆圈" to Tool.CIRCLE,
            "实箭" to Tool.ARROW,
            "虚箭" to Tool.DASHED_ARROW,
            "波浪" to Tool.WAVY_ARROW,
            "实线" to Tool.LINE,
            "虚线" to Tool.DASHED_LINE,
            "选择" to Tool.SELECT,
            "观看" to Tool.VIEW
        )
        val toolRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        toolSpecs.forEach { (label, tool) ->
            toolRow.addView(makeButton(label) {
                drawView.currentTool = tool
                service.setDrawMode(tool != Tool.VIEW)
            })
        }

        val actionRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        actionRow.addView(makeButton("撤销") { drawView.undo() })
        actionRow.addView(makeButton("删除") { drawView.deleteSelected() })
        actionRow.addView(makeButton("清空") { drawView.clearAll() })
        actionRow.addView(makeButton("关闭") { service.stopSelf() })

        val colorRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val colors = listOf("#FFFF00", "#FF3B30", "#34C759", "#007AFF", "#FFFFFF", "#000000")
        colors.forEach { hex ->
            val inner = View(context).apply {
                setBackgroundColor(hex.toColorInt())
                layoutParams = FrameLayout.LayoutParams(20, 20, Gravity.CENTER)
            }
            val sw = FrameLayout(context).apply {
                background = context.getDrawable(R.drawable.color_swatch_bg)
                layoutParams = LinearLayout.LayoutParams(28, 28).apply { setMargins(3, 3, 3, 3) }
                addView(inner)
            }
            sw.setOnClickListener {
                drawView.paintColor = hex.toColorInt()
                drawView.animateColorSelection(sw)
                highlightColor(hex)
            }
            colorViews.add(hex to sw)
            colorRow.addView(sw)
        }

        val thickRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val label = TextView(context).apply {
            text = "粗细"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(4, 0, 4, 0)
        }
        val seek = SeekBar(context).apply { max = 28; progress = 6 }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                drawView.paintWidth = (p + 2).toFloat()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        seek.layoutParams = LinearLayout.LayoutParams(160, LinearLayout.LayoutParams.WRAP_CONTENT)
        thickRow.addView(label)
        thickRow.addView(seek)

        panel.addView(toolRow)
        panel.addView(actionRow)
        panel.addView(colorRow)
        panel.addView(thickRow)
    }

    private fun makeButton(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            background = context.getDrawable(R.drawable.button_bg)
            setOnClickListener { onClick() }
            minHeight = 0
            minimumHeight = 0
            setPadding(8, 6, 8, 6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(2, 2, 2, 2) }
        }
    }

    private fun highlightColor(hex: String) {
        selectedColorHex = hex
        colorViews.forEach { (h, v) ->
            val selected = h == hex
            v.animate().cancel()
            v.animate()
                .scaleX(if (selected) 1.25f else 1f)
                .scaleY(if (selected) 1.25f else 1f)
                .setDuration(200)
                .start()
            v.foreground = if (selected) context.getDrawable(R.drawable.color_swatch_selected_bg) else null
        }
    }

    private var lastTap = 0L

    private fun togglePanel() {
        collapsed = !collapsed
        panel.visibility = if (collapsed) GONE else VISIBLE
        toggleBtn.text = if (collapsed) "展开" else "收起"
    }

    private fun setupDrag() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        header.onDrag = { dx, dy ->
            service.toolbarParams.x = dx.toInt()
            service.toolbarParams.y = dy.toInt()
            wm.updateViewLayout(this@ToolbarView, service.toolbarParams)
        }
    }

    /**
     * 自定义标题栏：拦截所有子控件触摸事件，确保拖动灵敏；
     * 同时根据落点识别「单击 toggle」和「双击 title」。
     */
    private class DragHeader(
        context: Context,
        private val titleView: View,
        private val toggleView: View,
        private val onToggle: () -> Unit,
        private val onDoubleTapTitle: () -> Unit
    ) : LinearLayout(context) {

        var onDrag: ((Float, Float) -> Unit)? = null

        private var startRawX = 0f
        private var startRawY = 0f
        private var startWinX = 0
        private var startWinY = 0
        private var downTime = 0L
        private var lastTitleTap = 0L

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 8, 12, 8)
            background = context.getDrawable(R.drawable.toolbar_header_bg)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            // 拦截所有触摸事件，子控件不再处理
            return true
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    val p = (parent as? ToolbarView)?.service?.toolbarParams
                    startWinX = p?.x ?: 0
                    startWinY = p?.y ?: 0
                    downTime = System.currentTimeMillis()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    onDrag?.invoke(
                        startWinX + (event.rawX - startRawX),
                        startWinY + (event.rawY - startRawY)
                    )
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (kotlin.math.hypot(dx.toDouble(), dy.toDouble()) < 12.0) {
                        // 判定为点击而非拖动
                        val x = event.x
                        val y = event.y
                        if (hitView(toggleView, x, y)) {
                            onToggle()
                        } else if (hitView(titleView, x, y)) {
                            val now = System.currentTimeMillis()
                            if (now - lastTitleTap < 300) onDoubleTapTitle()
                            lastTitleTap = now
                        }
                    }
                    return true
                }
            }
            return true
        }

        private fun hitView(v: View, x: Float, y: Float): Boolean {
            return x >= v.left && x <= v.right && y >= v.top && y <= v.bottom
        }
    }
}
