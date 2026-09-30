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
 * 浮窗工具栏：可拖拽标题栏 + 可折叠面板。
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

    private val header = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(24, 16, 16, 16)
        background = context.getDrawable(R.drawable.toolbar_header_bg)
    }
    private val title = TextView(context).apply {
        text = "足球标注 · 拖动我"
        setTextColor(Color.WHITE)
        textSize = 14f
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    private val toggleBtn = TextView(context).apply {
        text = "收起"
        setTextColor(Color.WHITE)
        setPadding(16, 8, 16, 8)
        background = context.getDrawable(R.drawable.button_bg)
        isClickable = true
        isFocusable = true
    }
    private val panel = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(12, 12, 12, 12)
        background = context.getDrawable(R.drawable.toolbar_panel_bg)
    }
    private var collapsed = false

    private val colorViews = mutableListOf<Pair<String, FrameLayout>>()
    private var selectedColorHex = "#FFFF00"

    init {
        orientation = VERTICAL
        header.addView(title)
        header.addView(toggleBtn)
        addView(header)
        addView(panel)
        buildPanel()
        setupToggle()
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
                layoutParams = FrameLayout.LayoutParams(36, 36, Gravity.CENTER)
            }
            val sw = FrameLayout(context).apply {
                background = context.getDrawable(R.drawable.color_swatch_bg)
                layoutParams = LinearLayout.LayoutParams(48, 48).apply { setMargins(6, 6, 6, 6) }
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
            setPadding(8, 0, 8, 0)
        }
        val seek = SeekBar(context).apply { max = 28; progress = 6 }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                drawView.paintWidth = (p + 2).toFloat()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        seek.layoutParams = LinearLayout.LayoutParams(300, LinearLayout.LayoutParams.WRAP_CONTENT)
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
            setTextColor(Color.WHITE)
            background = context.getDrawable(R.drawable.button_bg)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(4, 4, 4, 4) }
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
            // 选中色加一个白色高亮环
            v.foreground = if (selected) context.getDrawable(R.drawable.color_swatch_selected_bg) else null
        }
    }

    private fun setupToggle() {
        toggleBtn.setOnClickListener { togglePanel() }
        // 标题栏双击也能展开/收起
        title.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTap < 300) togglePanel()
            lastTap = now
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
        var startX = 0f
        var startY = 0f
        var paramX = 0
        var paramY = 0
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    paramX = service.toolbarParams.x
                    paramY = service.toolbarParams.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    service.toolbarParams.x = paramX + (event.rawX - startX).toInt()
                    service.toolbarParams.y = paramY + (event.rawY - startY).toInt()
                    wm.updateViewLayout(this@ToolbarView, service.toolbarParams)
                    true
                }
                else -> false
            }
        }
    }
}
