package com.genservices.glassnotify

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Shows ONE reusable floating glass card above every app.
 * The card view is built once and its content is swapped, never re-created per notification.
 * All calls must happen on the main thread (NotifService posts to it).
 *
 * WHERE TO CHANGE THE LOOK: `build()` (shapes, sizes, text) and `bind()` (what goes where).
 */
object OverlayManager {
    private val handler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { dismiss() }

    private var wm: WindowManager? = null
    private var root: LinearLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var attached = false

    private lateinit var avatar: ImageView
    private lateinit var appIcon: ImageView
    private lateinit var appName: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView

    private var currentKey: String? = null
    private var count = 1
    private var currentOpen: android.app.PendingIntent? = null
    private var baseY = 0
    private var topEdge = true
    private var glassBlur = false

    fun canDraw(c: Context) = Settings.canDrawOverlays(c)

    private fun dp(c: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

    // ------------------------------------------------------------------ public
    fun show(context: Context, card: Card) {
        val c = context.applicationContext
        if (!canDraw(c)) return
        if (wm == null) wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        topEdge = Prefs.top(c)
        glassBlur = Build.VERSION.SDK_INT >= 31 && Prefs.blur(c) && wm!!.isCrossWindowBlurEnabled
        if (root == null) build(c)
        applyLook(c)

        val sameChat = attached && card.key == currentKey
        count = if (sameChat) count + 1 else 1
        currentKey = card.key
        currentOpen = card.open
        bind(card)

        val r = root!!
        val params = buildParams(c)
        lp = params
        if (!attached) {
            r.alpha = 0f; r.scaleX = 0.9f; r.scaleY = 0.9f
            try { wm!!.addView(r, params); attached = true } catch (e: Exception) { return }
            r.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(OvershootInterpolator(1.2f)).start()
        } else {
            try { wm!!.updateViewLayout(r, params) } catch (e: Exception) { return }
            r.animate().cancel(); r.alpha = 1f; r.scaleX = 1f; r.scaleY = 1f
            // a little "pop" so the user notices the card updated
            r.scaleX = 0.96f; r.scaleY = 0.96f
            r.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator(2f)).start()
        }
        scheduleHide(c)
    }

    fun dismiss() {
        handler.removeCallbacks(autoHide)
        val r = root ?: return
        if (!attached) return
        r.animate().cancel()
        r.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(180).withEndAction { remove() }.start()
    }

    private fun remove() {
        val r = root ?: return
        if (attached) { try { wm?.removeViewImmediate(r) } catch (e: Exception) { /* already gone */ } }
        attached = false
        currentKey = null
        count = 1
    }

    private fun scheduleHide(c: Context) {
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, Prefs.seconds(c) * 1000L)
    }

    // ------------------------------------------------------------------ view
    private fun build(c: Context) {
        val r = LinearLayout(c)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(c, 14f), dp(c, 12f), dp(c, 14f), dp(c, 12f))

        avatar = ImageView(c)
        avatar.scaleType = ImageView.ScaleType.CENTER_CROP
        avatar.clipToOutline = true
        avatar.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }
        r.addView(avatar, LinearLayout.LayoutParams(dp(c, 46f), dp(c, 46f)))

        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        val colLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        colLp.marginStart = dp(c, 12f)
        r.addView(col, colLp)

        val head = LinearLayout(c)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        appIcon = ImageView(c)
        head.addView(appIcon, LinearLayout.LayoutParams(dp(c, 14f), dp(c, 14f)))
        appName = TextView(c)
        appName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        appName.setTextColor(Color.argb(210, 255, 255, 255))
        appName.maxLines = 1
        val nameLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        nameLp.marginStart = dp(c, 6f)
        head.addView(appName, nameLp)
        col.addView(head)

        title = TextView(c)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setTextColor(Color.WHITE)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        col.addView(title)

        body = TextView(c)
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        body.setTextColor(Color.argb(235, 255, 255, 255))
        body.maxLines = 2
        body.ellipsize = android.text.TextUtils.TruncateAt.END
        col.addView(body)

        for (t in arrayOf(appName, title, body)) t.setShadowLayer(3f, 0f, 1f, Color.argb(120, 0, 0, 0))

        r.setOnTouchListener(touch)
        root = r
    }

    /** Glass when the phone can blur what is behind the card; otherwise a darker see-through card that stays readable. */
    private fun applyLook(c: Context) {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(c, if (glassBlur) 18f else 22f).toFloat()
        if (glassBlur) {
            bg.setColor(Color.argb(60, 255, 255, 255))
            bg.setStroke(dp(c, 1f), Color.argb(110, 255, 255, 255))
        } else {
            bg.setColor(Color.argb(215, 28, 30, 44))
            bg.setStroke(dp(c, 1f), Color.argb(60, 255, 255, 255))
        }
        root?.background = bg
    }

    private fun bind(card: Card) {
        val icon = card.avatar ?: card.appIcon
        avatar.setImageDrawable(icon)
        appIcon.setImageDrawable(card.appIcon)
        appName.text = if (count > 1) "${card.appLabel} · $count new" else card.appLabel
        title.text = card.title
        title.visibility = if (card.title.isEmpty()) View.GONE else View.VISIBLE
        body.text = card.text
        body.visibility = if (card.text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun buildParams(c: Context): WindowManager.LayoutParams {
        val screenW = c.resources.displayMetrics.widthPixels
        val p = WindowManager.LayoutParams(
            screenW - dp(c, 24f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        if (glassBlur && Build.VERSION.SDK_INT >= 31) {
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            p.blurBehindRadius = 45
        }
        p.gravity = (if (topEdge) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
        val statusBar = c.resources.getIdentifier("status_bar_height", "dimen", "android")
            .let { if (it > 0) c.resources.getDimensionPixelSize(it) else dp(c, 24f) }
        baseY = if (topEdge) statusBar + dp(c, 8f) else dp(c, 56f)
        p.x = 0
        p.y = baseY
        return p
    }

    // ------------------------------------------------------------------ touch: tap = open, drag = move, fling away = dismiss
    private val touch = object : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val p = lp ?: return false
            val slop = ViewConfiguration.get(v.context).scaledTouchSlop
            val sign = if (topEdge) 1 else -1   // for BOTTOM gravity, a bigger y moves the card UP
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = p.x; startY = p.y
                    moved = false
                    handler.removeCallbacks(autoHide)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && hypot(dx, dy) > slop) moved = true
                    if (moved) {
                        p.x = startX + dx.toInt()
                        p.y = startY + (dy * sign).toInt()
                        try { wm?.updateViewLayout(v, p) } catch (ex: Exception) { /* ignore */ }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        openApp()
                        dismiss()
                    } else {
                        val awayY = (baseY - p.y)    // positive = pushed toward the screen edge
                        if (abs(p.x) > v.width * 0.35f || awayY > dp(v.context, 60f)) flingOut(v) else snapBack(v)
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> { snapBack(v); return true }
            }
            return false
        }
    }

    private fun openApp() {
        try { currentOpen?.send() } catch (e: Exception) { /* app can't be opened from here */ }
    }

    private fun flingOut(v: View) {
        val p = lp ?: return
        val w = v.resources.displayMetrics.widthPixels
        val startX = p.x
        val targetX = if (startX >= 0) w else -w
        val a = ValueAnimator.ofInt(startX, targetX)
        a.duration = 180
        a.addUpdateListener {
            p.x = it.animatedValue as Int
            try { wm?.updateViewLayout(v, p) } catch (ex: Exception) { /* ignore */ }
        }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) { remove() }
        })
        a.start()
    }

    private fun snapBack(v: View) {
        val p = lp ?: return
        val x0 = p.x
        val y0 = p.y
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 240
        a.interpolator = OvershootInterpolator(1.4f)
        a.addUpdateListener {
            val f = it.animatedValue as Float
            p.x = (x0 + (0 - x0) * f).toInt()
            p.y = (y0 + (baseY - y0) * f).toInt()
            try { wm?.updateViewLayout(v, p) } catch (ex: Exception) { /* ignore */ }
        }
        a.start()
        scheduleHide(v.context.applicationContext)
    }
}
