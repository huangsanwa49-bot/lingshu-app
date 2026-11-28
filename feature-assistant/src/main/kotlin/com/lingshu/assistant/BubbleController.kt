package com.lingshu.assistant

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

/**
 * 悬浮小助理：屏幕边上一个“家”字小圆球，可拖动、自动贴边。
 * 点一下打开快捷对话；长按播报问候与天气；有通知时旁边弹出一句话。
 * 用系统 View 直接画，不在悬浮窗里跑 Compose，省内存。
 */
class BubbleController(
    private val context: Context,
    private val prefs: AssistantPrefs,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val size = dp(54)
    private val ball = BallView(context)
    private val ballParams = overlayParams(size, size).apply {
        x = prefs.bubbleX.takeIf { it != Int.MIN_VALUE } ?: (screenWidth() - size - dp(8))
        y = prefs.bubbleY.takeIf { it != Int.MIN_VALUE } ?: (screenHeight() / 3)
    }
    private val balloon = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        maxWidth = dp(260)
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.argb(235, 20, 24, 31))
        }
        elevation = dp(6).toFloat()
        setOnClickListener { hideBalloon(); onTap() }
    }
    private val balloonParams = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
    private var shown = false
    private var balloonShown = false
    private val hideBalloonRunnable = Runnable { hideBalloon() }

    var unread: Int = 0
        set(v) {
            field = v
            ball.invalidate()
        }

    init {
        ball.setOnTouchListener(DragListener())
        ball.contentDescription = "benbentongxue 悬浮助理"
    }

    fun show() {
        if (shown) return
        clampToScreen()
        wm.addView(ball, ballParams)
        shown = true
    }

    fun hide() {
        if (!shown) return
        hideBalloon()
        wm.removeView(ball)
        shown = false
    }

    /** 在小球旁边显示一句话，几秒后自动收起。 */
    fun say(text: String, millis: Long = 7000) {
        if (!shown) return
        balloon.text = text
        val onLeft = ballParams.x < screenWidth() / 2
        balloonParams.gravity = Gravity.TOP or Gravity.START
        balloon.measure(
            View.MeasureSpec.makeMeasureSpec(dp(260), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        balloonParams.x = if (onLeft) ballParams.x + size + dp(6) else ballParams.x - balloon.measuredWidth - dp(6)
        balloonParams.y = ballParams.y + (size - balloon.measuredHeight) / 2
        if (balloonShown) wm.updateViewLayout(balloon, balloonParams) else wm.addView(balloon, balloonParams)
        balloonShown = true
        main.removeCallbacks(hideBalloonRunnable)
        main.postDelayed(hideBalloonRunnable, millis)
    }

    private fun hideBalloon() {
        main.removeCallbacks(hideBalloonRunnable)
        if (balloonShown) {
            wm.removeView(balloon)
            balloonShown = false
        }
    }

    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w,
        h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun screenWidth() = context.resources.displayMetrics.widthPixels
    private fun screenHeight() = context.resources.displayMetrics.heightPixels
    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun clampToScreen() {
        ballParams.x = ballParams.x.coerceIn(0, screenWidth() - size)
        ballParams.y = ballParams.y.coerceIn(dp(24), screenHeight() - size - dp(24))
    }

    private fun snapToEdge() {
        val target = if (ballParams.x + size / 2 < screenWidth() / 2) dp(6) else screenWidth() - size - dp(6)
        ValueAnimator.ofInt(ballParams.x, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                ballParams.x = it.animatedValue as Int
                if (shown) wm.updateViewLayout(ball, ballParams)
            }
            start()
        }
        prefs.bubbleX = target
        prefs.bubbleY = ballParams.y
    }

    private inner class DragListener : View.OnTouchListener {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false
        private val longPress = Runnable {
            longPressed = true
            ball.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPress()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = ballParams.x; startY = ballParams.y
                    dragging = false; longPressed = false
                    main.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    ball.squeezed = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        main.removeCallbacks(longPress)
                        hideBalloon()
                    }
                    if (dragging) {
                        ballParams.x = startX + dx.toInt()
                        ballParams.y = startY + dy.toInt()
                        clampToScreen()
                        wm.updateViewLayout(ball, ballParams)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    ball.squeezed = false
                    if (dragging) snapToEdge()
                    else if (!longPressed && e.actionMasked == MotionEvent.ACTION_UP) {
                        hideBalloon()
                        onTap()
                    }
                }
            }
            return true
        }
    }

    /** 渐变圆球 + “家”字 + 未读红点。 */
    private inner class BallView(context: Context) : View(context) {
        var squeezed = false
            set(v) {
                field = v
                animate().scaleX(if (v) 0.9f else 1f).scaleY(if (v) 0.9f else 1f).setDuration(90).start()
            }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = dp(2).toFloat()
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xD0, 0x3B, 0x3B) }
        private val dotText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        init {
            elevation = dp(8).toFloat()
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT) }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            fill.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.rgb(0x2A, 0x78, 0xD6), Color.rgb(0x1B, 0xAF, 0x7A), Shader.TileMode.CLAMP)
            text.textSize = h * 0.42f
            dotText.textSize = h * 0.2f
        }

        override fun onDraw(canvas: Canvas) {
            val r = min(width, height) / 2f
            canvas.drawCircle(width / 2f, height / 2f, r - dp(1), fill)
            canvas.drawCircle(width / 2f, height / 2f, r - dp(2), ring)
            canvas.drawText("家", width / 2f, height / 2f - (text.descent() + text.ascent()) / 2, text)
            if (unread > 0) {
                val dr = r * 0.34f
                val cx = width - dr
                val cy = dr
                canvas.drawCircle(cx, cy, dr, dot)
                val label = if (unread > 9) "9+" else unread.toString()
                canvas.drawText(label, cx, cy - (dotText.descent() + dotText.ascent()) / 2, dotText)
            }
        }
    }
}
