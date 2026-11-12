package com.example.straightline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var canvas: LineCanvasView
    private lateinit var bubble: ImageView
    private lateinit var canvasLp: WindowManager.LayoutParams
    private val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private var drawing = false
    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        // Full-screen canvas. Touch-through by default so the app underneath stays usable.
        canvas = LineCanvasView(this)
        canvasLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type, baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT
        )
        wm.addView(canvas, canvasLp)

        // Floating round bubble, added last so it sits on top.
        val d = resources.displayMetrics.density
        val size = (56 * d).toInt()
        bg.setColor(0xCC555555.toInt())
        bubble = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setColorFilter(0xFFFFFFFF.toInt())
            val p = (14 * d).toInt(); setPadding(p, p, p, p)
            background = bg
            elevation = 8 * d
        }
        val lp = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (16 * d).toInt(); y = (200 * d).toInt() }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false; var t0 = 0L
        bubble.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false; t0 = e.eventTime }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) { lp.x = (ox + dx).toInt(); lp.y = (oy + dy).toInt(); wm.updateViewLayout(bubble, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) {
                    if (e.eventTime - t0 > 600) canvas.clearAll() else toggle()   // long-press = clear
                }
            }
            true
        }
        wm.addView(bubble, lp)
    }

    private fun toggle() {
        drawing = !drawing
        canvasLp.flags = if (drawing) baseFlags else baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        wm.updateViewLayout(canvas, canvasLp)
        bg.setColor(if (drawing) 0xFFE24B4A.toInt() else 0xCC555555.toInt())
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        when (i?.action) {
            "UNDO" -> canvas.undo()
            "CLEAR" -> canvas.clearAll()
            "STOP" -> { stopSelf(); return START_NOT_STICKY }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { wm.removeView(bubble) }
        runCatching { wm.removeView(canvas) }
        super.onDestroy()
    }

    private fun action(name: String, label: String): Notification.Action {
        val pi = PendingIntent.getService(
            this, name.hashCode(), Intent(this, OverlayService::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(null, label, pi).build()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("ov", "Overlay", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "ov")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Straight Lines is active")
            .setContentText("Tap the bubble to draw. Tap again to use your app.")
            .addAction(action("UNDO", "Undo"))
            .addAction(action("CLEAR", "Clear"))
            .addAction(action("STOP", "Stop"))
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }
}
