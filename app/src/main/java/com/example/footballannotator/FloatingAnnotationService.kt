package com.example.footballannotator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

/**
 * 前台 Service，挂载两个浮窗窗口：
 *  - drawView  ：全屏透明绘制窗（观看模式穿透，绘制模式拦截）
 *  - toolbar    ：小浮窗工具栏（常驻可点、可拖拽）
 */
class FloatingAnnotationService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var state: AnnotationState
    private lateinit var drawView: DrawingView
    private lateinit var toolbar: ToolbarView

    lateinit var toolbarParams: WindowManager.LayoutParams
    private lateinit var drawParams: WindowManager.LayoutParams

    private val overlayType =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        state = AnnotationState()

        // 绘制窗：默认「观看模式」= 不接收触摸，事件穿透到下方视频
        drawParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        drawParams.gravity = Gravity.TOP or Gravity.START

        // 工具栏窗：常驻可点
        toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        toolbarParams.gravity = Gravity.TOP or Gravity.START
        toolbarParams.x = 0
        toolbarParams.y = 200

        drawView = DrawingView(this, state)
        toolbar = ToolbarView(this, state, drawView, this)

        wm.addView(drawView, drawParams)
        wm.addView(toolbar, toolbarParams)

        // 前台服务：包在 try/catch 中，任何权限/类型问题都不应导致闪退
        try {
            startForeground(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 切换绘制窗是否拦截触摸：true=绘制/选择模式，false=观看模式（穿透） */
    fun setDrawMode(on: Boolean) {
        drawParams.flags = if (on)
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        else
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        wm.updateViewLayout(drawView, drawParams)
    }

    override fun onDestroy() {
        if (::drawView.isInitialized) wm.removeView(drawView)
        if (::toolbar.isInitialized) wm.removeView(toolbar)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val chanId = "float_chan"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                chanId,
                "浮窗标注",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(chan)
        }
        return NotificationCompat.Builder(this, chanId)
            .setContentTitle("足球浮窗标注运行中")
            .setContentText("浮窗正在视频上标注，可返回应用关闭")
            .setSmallIcon(R.drawable.ic_stat)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val NOTIF_ID = 1001
    }
}
