package app.baretime.clock

import android.content.Context
import android.graphics.*
import android.text.format.DateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the clock onto a bitmap. The layout follows the designer's CSS (styles.css):
 * everything is measured in em of the time font size F, so the whole clock scales
 * with F. The widget always fits itself to its own size ("Size" 100% = fill the widget).
 */
object ClockRenderer {
    private const val AP_SCALE = 0.26f      // .time .ap {font-size:.26em}
    private const val DATE_LINE = 1.45f     // date inherits body line-height 1.45
    private const val DATE_GAP = 0.14f      // gap between time and date, in em of F

    private class Text(val s: String, val size: Float, val spacingEm: Float)

    private class Layout(
        val time: List<Text>, val date: Text?, val F: Float,
        val timeW: Float, val timeH: Float, val dateW: Float, val dateH: Float,
        val padH: Float, val padV: Float, val contentW: Float, val boxW: Float, val boxH: Float
    )

    fun render(ctx: Context, d: Design, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val tf = FontBook.typeface(ctx, d.font, d.weight)
        val (timeParts, dateText) = texts(ctx, d)

        // 1) measure at a reference size, then scale so the clock fits the widget
        val ref = layout(d, tf, timeParts, dateText, 100f)
        val fit = min((w * 0.92f) / ref.boxW, (h * 0.92f) / ref.boxH) * 100f
        val F = max(4f, fit * min(1f, d.size / 100f))
        val L = layout(d, tf, timeParts, dateText, F)

        // 2) position the box
        val margin = w * 0.03f
        var x = when (d.align) { "left" -> margin; "right" -> w - margin - L.boxW; else -> (w - L.boxW) / 2f }
        var y = (h - L.boxH) / 2f
        x += d.offX / 100f * w
        y += d.offY / 100f * h

        val layer = canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), (d.opacity * 2.55f).toInt().coerceIn(0, 255))

        // 3) card
        val card = RectF(x, y, x + L.boxW, y + L.boxH)
        val r = min(d.radius * 0.01f * F, min(card.width(), card.height()) / 2f)
        if (d.mode == "color") {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val a = (d.cardOpacity * 2.55f).toInt().coerceIn(0, 255)
            p.shader = if (d.gradientCard) cssGradient(card, d.angle, withAlpha(d.bg1, a), withAlpha(d.bg2, a))
                       else null
            if (!d.gradientCard) p.color = withAlpha(d.bg1, a)
            canvas.drawRoundRect(card, r, r, p)
        } else if (d.mode == "glass") {
            // A widget can't blur what's behind it, so glass becomes a soft white tint.
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.argb(((d.glassTint / 100f + 0.05f).coerceAtMost(1f) * 255).toInt(), 255, 255, 255)
            canvas.drawRoundRect(card, r, r, p)
            val b = Paint(Paint.ANTI_ALIAS_FLAG)
            b.style = Paint.Style.STROKE; b.strokeWidth = max(1.5f, F * 0.01f)
            b.color = Color.argb(72, 255, 255, 255)
            canvas.drawRoundRect(card, r, r, b)
        }

        // 4) time and date
        val innerX = x + L.padH
        var top = y + L.padV
        drawTime(canvas, d, tf, L, innerX, top)
        if (L.date != null) {
            top += L.timeH + DATE_GAP * F
            val dx = alignX(d.align, innerX, L.contentW, L.dateW)
            val p = textPaint(tf, L.date.size, L.date.spacingEm, false)
            val fm = p.fontMetrics
            val base = top + (L.dateH - (fm.descent - fm.ascent)) / 2f - fm.ascent
            drawStyled(canvas, d, p, L.date.s, dx, base, F, RectF(dx, top, dx + L.dateW, top + L.dateH), d.dateOpacity / 100f)
        }
        canvas.restoreToCount(layer)
        return bmp
    }

    // ---------------------------------------------------------------- text content

    private fun texts(ctx: Context, d: Design): Pair<List<String>, String?> {
        val zone = if (d.tz == "local") TimeZone.getDefault() else TimeZone.getTimeZone(d.tz)
        val cal = Calendar.getInstance(zone)
        var hh = cal.get(Calendar.HOUR_OF_DAY)
        var ap = ""
        if (d.hour12) { ap = if (hh >= 12) "PM" else "AM"; hh = hh % 12; if (hh == 0) hh = 12 }
        val hs = if (d.pad0) hh.toString().padStart(2, '0') else hh.toString()
        val ms = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
        // [hour, minute, am/pm or ""]
        val parts = listOf(hs, ms, if (d.hour12 && d.ampm) ap else "")

        val loc = Locale.getDefault()
        fun fmt(pattern: String): String =
            java.text.SimpleDateFormat(pattern, loc).apply { timeZone = zone }.format(cal.time)
        val date = if (!d.dateShow) null else when (d.dateFmt) {
            "long" -> fmt(DateFormat.getBestDateTimePattern(loc, if (d.weekday) "EEEEdMMMMyyyy" else "dMMMMyyyy"))
            "medium" -> fmt(DateFormat.getBestDateTimePattern(loc, if (d.weekday) "EEEdMMM" else "dMMM"))
            else -> {
                val body = fmt(when (d.dateFmt) { "iso" -> "yyyy-MM-dd"; "mdy" -> "MM/dd/yyyy"; else -> "dd/MM/yyyy" })
                if (d.weekday) fmt("EEE") + ", " + body else body
            }
        }
        return parts to date
    }

    // ---------------------------------------------------------------- layout

    private fun textPaint(tf: Typeface, size: Float, spacingEm: Float, tnum: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf; textSize = size; letterSpacing = spacingEm
        if (tnum) fontFeatureSettings = "tnum"
    }

    private fun layout(d: Design, tf: Typeface, parts: List<String>, date: String?, F: Float): Layout {
        val ls = d.spacing * 0.01f
        val apSize = F * AP_SCALE
        val hr = Text(parts[0], F, ls)
        val mn = Text(parts[1], F, ls)
        val ap = if (parts[2].isEmpty()) null else Text(parts[2], apSize, 0.06f)
        val lineH = d.lh / 100f
        fun wid(t: Text, tnum: Boolean = true) = textPaint(tf, t.size, t.spacingEm, tnum).measureText(t.s)

        val timeW: Float; val timeH: Float
        val items = ArrayList<Text>()
        if (d.stacked) {
            items += hr; items += mn; if (ap != null) items += ap
            timeW = items.maxOf { wid(it) }
            timeH = items.sumOf { (it.size * lineH).toDouble() }.toFloat()
        } else {
            val sep = Text(":", F, ls)
            items += hr; items += sep; items += mn; if (ap != null) items += ap
            val sepW = wid(sep) + 2 * 0.02f * F
            timeW = wid(hr) + sepW + wid(mn) + (if (ap != null) 0.4f * apSize + wid(ap) else 0f)
            timeH = F * lineH
        }
        val dateT = date?.let { Text(it, F * d.dateSize / 100f, d.dateSpacing * 0.01f) }
        val dateW = dateT?.let { textPaint(tf, it.size, it.spacingEm, false).measureText(it.s) } ?: 0f
        val dateH = dateT?.let { it.size * DATE_LINE } ?: 0f

        val card = d.mode != "transparent"
        val padH = if (card) d.pad * 0.02f * F else 0f
        val padV = if (card) d.pad * 0.01f * F else 0f
        val contentW = max(timeW, dateW)
        val contentH = timeH + (if (dateT != null) DATE_GAP * F + dateH else 0f)
        return Layout(items, dateT, F, timeW, timeH, dateW, dateH, padH, padV, contentW,
            contentW + 2 * padH, contentH + 2 * padV)
    }

    private fun alignX(align: String, left: Float, width: Float, itemW: Float) = when (align) {
        "left" -> left; "right" -> left + width - itemW; else -> left + (width - itemW) / 2f
    }

    // ---------------------------------------------------------------- drawing

    private fun drawTime(c: Canvas, d: Design, tf: Typeface, L: Layout, innerX: Float, top: Float) {
        val F = L.F
        val x0 = alignX(d.align, innerX, L.contentW, L.timeW)
        val lineH = d.lh / 100f

        fun baseline(p: Paint, lineTop: Float, lineHeight: Float): Float {
            val fm = p.fontMetrics
            return lineTop + (lineHeight - (fm.descent - fm.ascent)) / 2f - fm.ascent
        }

        if (d.stacked) {
            var y = top
            for (t in L.time) {
                val p = textPaint(tf, t.size, t.spacingEm, true)
                val tw = p.measureText(t.s)
                val lh = t.size * lineH
                val x = alignX(d.align, x0, L.timeW, tw)
                drawStyled(c, d, p, t.s, x, baseline(p, y, lh), F, RectF(x, y, x + tw, y + lh), 1f)
                y += lh
            }
        } else {
            // one baseline shared by all pieces
            val mainP = textPaint(tf, F, d.spacing * 0.01f, true)
            val base = baseline(mainP, top, F * lineH)
            var x = x0
            val box = RectF(x0, top, x0 + L.timeW, top + L.timeH)
            for ((i, t) in L.time.withIndex()) {
                val p = textPaint(tf, t.size, t.spacingEm, true)
                        if (i == 3) x += 0.4f * t.size
                val padSep = if (t.s == ":" && i == 1) 0.02f * F else 0f
                x += padSep
                drawStyled(c, d, p, t.s, x, base, F, box, 1f)
                x += p.measureText(t.s) + padSep
            }
        }
    }

    /** Glow, shadow, outline, then the fill (plain or gradient), like the CSS filters. */
    private fun drawStyled(c: Canvas, d: Design, p: Paint, s: String, x: Float, base: Float, F: Float, box: RectF, alpha: Float) {
        // Element opacity (the date) fades the whole group, effects included, as CSS does.
        val layer = if (alpha < 1f) c.saveLayerAlpha(null, (alpha * 255).toInt().coerceIn(0, 255)) else -1
        if (d.glow > 0) {
            val g = withAlpha(d.color, (min(1f, d.glow / 70f) * 255).toInt())
            for (k in floatArrayOf(d.glow * 0.004f, d.glow * 0.012f)) {
                val gp = Paint(p); gp.style = Paint.Style.FILL; gp.color = d.color
                gp.setShadowLayer(max(0.5f, k * F * 1.6f), 0f, 0f, g)
                c.drawText(s, x, base, gp)
            }
        }
        if (d.shadow > 0) {
            val sp = Paint(p); sp.style = Paint.Style.FILL; sp.color = d.color
            sp.setShadowLayer(0.07f * F, 0f, 0.03f * F, Color.argb((d.shadow / 100f * 0.85f * 255).toInt(), 0, 0, 0))
            c.drawText(s, x, base, sp)
        }
        if (d.outline > 0) {
            val op = Paint(p); op.style = Paint.Style.STROKE; op.strokeJoin = Paint.Join.ROUND
            op.strokeWidth = d.outline * 0.004f * F; op.color = d.outlineColor
            c.drawText(s, x, base, op)
        }
        val fp = Paint(p); fp.style = Paint.Style.FILL
        if (d.gradText) fp.shader = cssGradient(box, 135f, d.color, d.color2) else fp.color = d.color
        c.drawText(s, x, base, fp)
        if (layer >= 0) c.restoreToCount(layer)
    }

    private fun withAlpha(color: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    /** CSS linear-gradient(<angle>deg, c1, c2): 0° points up, 90° points right. */
    private fun cssGradient(r: RectF, angleDeg: Float, c1: Int, c2: Int): Shader {
        val a = Math.toRadians(angleDeg.toDouble())
        val dx = sin(a).toFloat(); val dy = -cos(a).toFloat()
        val len = abs(r.width() * dx) + abs(r.height() * dy)
        val cx = r.centerX(); val cy = r.centerY()
        return LinearGradient(cx - dx * len / 2, cy - dy * len / 2, cx + dx * len / 2, cy + dy * len / 2,
            c1, c2, Shader.TileMode.CLAMP)
    }
}
