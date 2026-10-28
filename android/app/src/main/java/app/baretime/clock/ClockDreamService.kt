package app.baretime.clock

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.random.Random

/** Screen saver: shows the widget's design full screen while the phone charges or docks. */
class ClockDreamService : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private var image: ImageView? = null
    private var lastW = 0
    private var lastH = 0

    private val tick = object : Runnable {
        override fun run() {
            draw()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + 200L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false      // any touch wakes the phone
        isFullscreen = true
        isScreenBright = false     // dimmer, good for a bedside clock
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        image = img
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w = r - l
            val h = b - t
            if (w > 0 && h > 0 && (w != lastW || h != lastH)) { lastW = w; lastH = h; draw() }
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(tick)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun draw() {
        val img = image ?: return
        if (lastW <= 0 || lastH <= 0) return
        try {
            val design = Design.parse(this, DesignStore.load(this))
            img.setImageBitmap(ClockRenderer.render(this, design, lastW, lastH))
            // Shift a few pixels every minute so no pixel stays lit (burn-in protection).
            val d = resources.displayMetrics.density
            img.translationX = Random.nextInt(-12, 13) * d
            img.translationY = Random.nextInt(-12, 13) * d
        } catch (e: Exception) { /* keep the last picture */ }
    }
}
