package com.genservices.baretime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.format.DateFormat
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws a BareTime design into a transparent bitmap.
 * Works entirely offline using the phone's own clock.
 *
 * All sizes are first laid out in "reference units" where the clock text is 100 units tall
 * (the same idea as 1em in the web designer), then multiplied by one scale factor so the
 * whole clock fits the widget. Making the widget bigger therefore makes the clock bigger.
 */
object ClockRenderer {
    private val faces = HashMap<String, Typeface>()
    private var fontMap: JSONObject? = null

    // ---------------------------------------------------------------- fonts
    private fun fontJson(ctx: Context): JSONObject {
        fontMap?.let { return it }
        val m = try {
            JSONObject(ctx.assets.open("fonts.json").bufferedReader().use { it.readText() })
        } catch (e: Exception) { JSONObject() }
        fontMap = m
        return m
    }

    fun typeface(ctx: Context, name: String, weight: Int): Typeface {
        val key = "$name@$weight"
        faces[key]?.let { return it }
        val tf = (try { loadFont(ctx, name, weight) } catch (e: Exception) { null }) ?: systemFace(name, weight)
        faces[key] = tf
        return tf
    }

    private fun loadFont(ctx: Context, name: String, weight: Int): Typeface? {
        val arr = fontJson(ctx).optJSONArray(name) ?: return null
        var best: JSONObject? = null
        var bestDist = Int.MAX_VALUE
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            if (e.optBoolean("var")) {
                val w = weight.coerceIn(e.getInt("min"), e.getInt("max"))
                return Typeface.Builder(ctx.assets, e.getString("file"))
                    .setFontVariationSettings("'wght' $w")
                    .build()
            }
            val d = abs(e.getInt("weight") - weight)
            if (d < bestDist) { bestDist = d; best = e }
        }
        return best?.let { Typeface.createFromAsset(ctx.assets, it.getString("file")) }
    }

    private fun systemFace(name: String, weight: Int): Typeface {
        val base = if (name == "System Mono") Typeface.MONOSPACE else Typeface.SANS_SERIF
        return if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false)
        else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

    // ---------------------------------------------------------------- helpers
    private fun col(s: String, fallback: Int): Int = try { Color.parseColor(s) } catch (e: Exception) { fallback }
    private fun withAlpha(c: Int, a: Float): Int = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    private fun dateText(t: JSONObject, ctx: Context, cal: Calendar, tz: TimeZone): String {
        val loc = Locale.getDefault()
        val weekday = t.optBoolean("weekday", true)
        val pattern = when (t.optString("dateFmt", "long")) {
            "medium" -> DateFormat.getBestDateTimePattern(loc, if (weekday) "EEEdMMM" else "dMMM")
            "dmy" -> (if (weekday) "EEE, " else "") + "dd/MM/yyyy"
            "mdy" -> (if (weekday) "EEE, " else "") + "MM/dd/yyyy"
            "iso" -> (if (weekday) "EEE, " else "") + "yyyy-MM-dd"
            else -> DateFormat.getBestDateTimePattern(loc, if (weekday) "EEEEdMMMMyyyy" else "dMMMMyyyy")
        }
        val f = SimpleDateFormat(pattern, loc)
        f.timeZone = tz
        return f.format(cal.time)
    }

    // ---------------------------------------------------------------- main entry
    fun render(ctx: Context, t: JSONObject, W: Int, H: Int, now: Date = Date()): Bitmap {
        val bmp = Bitmap.createBitmap(max(W, 1), max(H, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val mode = t.optString("mode", "transparent")
        val tf = typeface(ctx, t.optString("font", "Inter"), t.optInt("weight", 300))
        val textColor = col(t.optString("color", "#ffffff"), Color.WHITE)
        val color2 = col(t.optString("color2", "#ffd1ff"), Color.WHITE)
        val gradText = t.optBoolean("gradText", false)
        val opacity = t.optDouble("opacity", 100.0).toFloat() / 100f
        val shadow = t.optDouble("shadow", 35.0).toFloat()
        val glow = t.optDouble("glow", 0.0).toFloat()
        val outline = t.optDouble("outline", 0.0).toFloat()
        val outlineColor = col(t.optString("outlineColor", "#000000"), Color.BLACK)
        val letter = t.optDouble("spacing", 0.0).toFloat() * 0.01f
        val lineMul = t.optDouble("lh", 100.0).toFloat() / 100f
        val stacked = t.optString("layout", "single") == "stacked"
        val align = t.optString("align", "center")

        // ---- time + date strings (phone clock, optional time zone)
        val tzId = t.optString("tz", "local")
        val tz = if (tzId == "local") TimeZone.getDefault() else TimeZone.getTimeZone(tzId)
        val cal = Calendar.getInstance(tz)
        cal.time = now
        var hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val h12 = t.optBoolean("hour12", !DateFormat.is24HourFormat(ctx))
        val ap = if (h12 && t.optBoolean("ampm", true)) (if (hour >= 12) "PM" else "AM") else ""
        if (h12) hour = if (hour % 12 == 0) 12 else hour % 12
        val hs = if (t.optBoolean("pad0", true)) String.format(Locale.US, "%02d", hour) else hour.toString()
        val ms = String.format(Locale.US, "%02d", minute)
        val showDate = t.optBoolean("dateShow", true)
        val dateStr = if (showDate) dateText(t, ctx, cal, tz) else ""

        // ---- paints at reference size (clock text = 100 units)
        fun paint(size: Float, ls: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = size
            letterSpacing = ls
            fontFeatureSettings = "tnum"
        }
        val tp = paint(100f, letter)
        val ap0 = paint(26f, 0.06f)
        val dsz = t.optDouble("dateSize", 22.0).toFloat()
        val dp = paint(dsz, t.optDouble("dateSpacing", 2.0).toFloat() * 0.01f)

        val lineH = 100f * lineMul
        val apGap = 10.4f
        val timeW: Float
        val timeH: Float
        if (stacked) {
            timeW = max(max(tp.measureText(hs), tp.measureText(ms)), if (ap.isEmpty()) 0f else ap0.measureText(ap))
            timeH = lineH * 2 + (if (ap.isEmpty()) 0f else 26f * 1.3f)
        } else {
            timeW = tp.measureText("$hs:$ms") + (if (ap.isEmpty()) 0f else apGap + ap0.measureText(ap))
            timeH = lineH
        }
        val dateW = if (dateStr.isEmpty()) 0f else dp.measureText(dateStr)
        val dateH = if (dateStr.isEmpty()) 0f else dsz * 1.25f
        val dateGap = if (dateStr.isEmpty()) 0f else 14f
        val contentW = max(timeW, dateW)
        val contentH = timeH + dateGap + dateH

        val hasCard = mode == "color" || mode == "glass"
        val pad = t.optDouble("pad", 40.0).toFloat()
        val padX = if (hasCard) pad * 2f else 0f
        val padY = if (hasCard) pad else 0f
        val cardW = contentW + padX * 2
        val cardH = contentH + padY * 2

        val s = min(W * 0.96f / cardW, H * 0.96f / cardH)
        val x0 = when (align) {
            "left" -> W * 0.02f
            "right" -> W - W * 0.02f - cardW * s
            else -> (W - cardW * s) / 2f
        }
        val y0 = (H - cardH * s) / 2f
        val cx0 = x0 + padX * s
        val cy0 = y0 + padY * s

        // ---- card
        if (hasCard) {
            val r = RectF(x0, y0, x0 + cardW * s, y0 + cardH * s)
            val radius = t.optDouble("radius", 32.0).toFloat() * s
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            if (mode == "glass") {
                // Android widgets cannot blur what is behind them, so glass becomes a soft tint.
                fill.color = withAlpha(Color.WHITE, t.optDouble("glassTint", 12.0).toFloat() / 100f + 0.04f)
                canvas.drawRoundRect(r, radius, radius, fill)
                val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = max(1.5f, 1.2f * s)
                    color = withAlpha(Color.WHITE, 0.28f)
                }
                canvas.drawRoundRect(r, radius, radius, edge)
            } else {
                val a = t.optDouble("cardOpacity", 100.0).toFloat() / 100f
                val c1 = withAlpha(col(t.optString("bg1", "#6d5dfc"), Color.MAGENTA), a)
                if (t.optString("bgType", "gradient") == "solid") {
                    fill.color = c1
                } else {
                    val c2 = withAlpha(col(t.optString("bg2", "#ff6bcb"), Color.MAGENTA), a)
                    val ang = Math.toRadians(t.optDouble("angle", 135.0))
                    val dx = sin(ang).toFloat()
                    val dy = -cos(ang).toFloat()
                    val w = r.width()
                    val h = r.height()
                    val len = abs(w * sin(ang).toFloat()) + abs(h * cos(ang).toFloat())
                    val mx = r.centerX()
                    val my = r.centerY()
                    fill.shader = LinearGradient(mx - dx * len / 2, my - dy * len / 2, mx + dx * len / 2, my + dy * len / 2, c1, c2, Shader.TileMode.CLAMP)
                }
                canvas.drawRoundRect(r, radius, radius, fill)
            }
        }

        // ---- text
        fun style(p: Paint, alphaMul: Float) {
            p.textSize = p.textSize * s
            p.alpha = (255 * opacity * alphaMul).toInt().coerceIn(0, 255)
            if (gradText) {
                p.shader = LinearGradient(cx0, cy0, cx0 + contentW * s, cy0 + contentH * s, textColor, color2, Shader.TileMode.CLAMP)
                p.alpha = (255 * opacity * alphaMul).toInt().coerceIn(0, 255)
            } else {
                p.color = withAlpha(textColor, opacity * alphaMul)
            }
        }
        val dateAlpha = t.optDouble("dateOpacity", 80.0).toFloat() / 100f
        style(tp, 1f); style(ap0, 1f); style(dp, dateAlpha)

        fun drawFx(txt: String, x: Float, y: Float, p: Paint) {
            if (outline > 0f) {
                val sp = Paint(p)
                sp.shader = null
                sp.style = Paint.Style.STROKE
                sp.strokeWidth = outline * 0.008f * p.textSize
                sp.color = outlineColor
                sp.clearShadowLayer()
                canvas.drawText(txt, x, y, sp)
            }
            if (glow > 0f) {
                val gc = withAlpha(textColor, min(1f, glow / 70f))
                for (k in floatArrayOf(0.004f, 0.012f)) {
                    val gp = Paint(p)
                    gp.setShadowLayer(max(1f, glow * k * p.textSize), 0f, 0f, gc)
                    canvas.drawText(txt, x, y, gp)
                }
            }
            if (shadow > 0f) {
                val sp = Paint(p)
                sp.setShadowLayer(0.045f * p.textSize, 0f, 0.03f * p.textSize, Color.argb((shadow / 100f * 0.85f * 255).toInt().coerceIn(0, 255), 0, 0, 0))
                canvas.drawText(txt, x, y, sp)
            }
            canvas.drawText(txt, x, y, p)
        }

        // x position of a line of width w (reference units) inside the content block
        fun lineX(w: Float): Float = when (align) {
            "left" -> cx0
            "right" -> cx0 + (contentW - w) * s
            else -> cx0 + (contentW - w) / 2f * s
        }
        fun baseline(top: Float, h: Float, p: Paint): Float {
            val fm = p.fontMetrics
            return top + (h - (fm.descent - fm.ascent)) / 2f - fm.ascent
        }

        var y = cy0
        if (stacked) {
            val hb = baseline(y, lineH * s, tp)
            drawFx(hs, lineX(tp.measureText(hs) / s), hb, tp)
            y += lineH * s
            val mb = baseline(y, lineH * s, tp)
            drawFx(ms, lineX(tp.measureText(ms) / s), mb, tp)
            y += lineH * s
            if (ap.isNotEmpty()) {
                val ah = 26f * 1.3f * s
                drawFx(ap, lineX(ap0.measureText(ap) / s), baseline(y, ah, ap0), ap0)
                y += ah
            }
        } else {
            val mainStr = "$hs:$ms"
            val mw = tp.measureText(mainStr) / s
            val lw = mw + (if (ap.isEmpty()) 0f else apGap + ap0.measureText(ap) / s)
            val lx = lineX(lw)
            val bl = baseline(y, lineH * s, tp)
            drawFx(mainStr, lx, bl, tp)
            if (ap.isNotEmpty()) drawFx(ap, lx + (mw + apGap) * s, bl, ap0)
            y += lineH * s
        }
        if (dateStr.isNotEmpty()) {
            y += dateGap * s
            drawFx(dateStr, lineX(dp.measureText(dateStr) / s), baseline(y, dateH * s, dp), dp)
        }
        return bmp
    }
}
