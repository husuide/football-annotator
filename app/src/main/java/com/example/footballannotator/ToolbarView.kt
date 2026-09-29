package com.example.footballannotator

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.toColorInt

/**
 * 浮窗工具栏：可拖拽标题栏 + 面板。
 * 面板含：工具选择 / 撤销 / 删除选中 / 清空 / 收起 / 关闭 / 颜色 / 粗细。
 */
class ToolbarView @JvmOverloads constructor(
    context: Context,
    private val state: AnnotationState,
    private val drawView: DrawingView,
    private val service: FloatingAnnotationService
) : LinearLayout(context) {

    private val header = TextView(context).apply {
        text = "足球标注 · 拖动我"
        setPadding(24, 16, 24, 16)
        setBackgroundColor(0xFF333333.toInt())
        setTextColor(Color.WHITE)
    }
    private val panel = LinearLayout(context).apply {
        orientation = HORIZONTAL
        setPadding(12, 12, 12, 12)
        setBackgroundColor(0xCC222222.toInt())
    }
    private var collapsed = false

    init {
        orientation = VERTICAL
        addView(header)
        addView(panel)
        buildPanel()
        setupDrag()
    }

    private fun buildPanel() {
        val toolSpecs = listOf(
            "圆圈" to Tool.CIRCLE,
            "箭头" to Tool.ARROW,
            "线条" to Tool.LINE,
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
        actionRow.addView(makeButton("收起") { togglePanel() })
        actionRow.addView(makeButton("关闭") { service.stopSelf() })

        val colorRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val colors = listOf("#FFFF00", "#FF3B30", "#34C759", "#007AFF", "#FFFFFF", "#000000")
        colors.forEach { hex ->
            val sw = View(context).apply {
                setBackgroundColor(hex.toColorInt())
                layoutParams = LinearLayout.LayoutParams(48, 48).apply { setMargins(6, 6, 6, 6) }
            }
            sw.setOnClickListener { drawView.paintColor = hex.toColorInt() }
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

        panel.orientation = VERTICAL
        panel.addView(toolRow)
        panel.addView(actionRow)
        panel.addView(colorRow)
        panel.addView(thickRow)
    }

    private fun makeButton(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF555555.toInt())
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(4, 4, 4, 4) }
        }
    }

    private fun togglePanel() {
        collapsed = !collapsed
        panel.visibility = if (collapsed) GONE else VISIBLE
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
