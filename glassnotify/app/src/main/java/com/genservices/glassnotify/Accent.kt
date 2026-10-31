package com.genservices.glassnotify

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable

/** Picks one nice color from an app's icon. Computed once per app, then cached. */
object Accent {
    private val cache = HashMap<String, Int>()
    private val DEFAULT = Color.rgb(139, 123, 255)

    fun of(pkg: String, icon: Drawable?): Int {
        cache[pkg]?.let { return it }
        val color = compute(icon)
        cache[pkg] = color
        return color
    }

    private fun compute(icon: Drawable?): Int {
        if (icon == null) return DEFAULT
        return try {
            // copy the drawable so we never change the one the card is showing
            val d = icon.constantState?.newDrawable()?.mutate() ?: icon
            val size = 24
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, size, size)
            d.draw(Canvas(bmp))
            val px = IntArray(size * size)
            bmp.getPixels(px, 0, size, 0, 0, size, size)
            bmp.recycle()

            var r = 0.0; var g = 0.0; var b = 0.0; var w = 0.0
            for (p in px) {
                if ((p ushr 24) < 128) continue            // skip see-through pixels
                val pr = Color.red(p); val pg = Color.green(p); val pb = Color.blue(p)
                val sat = maxOf(pr, pg, pb) - minOf(pr, pg, pb)
                val wt = 1.0 + sat * sat / 64.0              // colorful pixels count more than grey/white
                r += pr * wt; g += pg * wt; b += pb * wt; w += wt
            }
            if (w == 0.0) return DEFAULT
            val hsv = FloatArray(3)
            Color.RGBToHSV((r / w).toInt(), (g / w).toInt(), (b / w).toInt(), hsv)
            if (hsv[1] < 0.15f) return DEFAULT               // grey/white icon: keep the default tint
            hsv[1] = maxOf(hsv[1], 0.5f)
            hsv[2] = maxOf(hsv[2], 0.85f)
            Color.HSVToColor(hsv)
        } catch (e: Exception) {
            DEFAULT
        }
    }
}
