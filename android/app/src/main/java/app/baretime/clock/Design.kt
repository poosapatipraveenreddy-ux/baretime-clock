package app.baretime.clock

import android.content.Context
import android.graphics.Color
import android.text.format.DateFormat
import org.json.JSONObject

/** A clock design. Same keys and ranges as THEME-SPEC.md; unknown or invalid values fall back to defaults. */
class Design(o: JSONObject, hour12Default: Boolean) {
    private fun str(k: String, d: String, ok: Set<String>? = null): String {
        val v = o.opt(k) as? String ?: return d
        return if (ok == null || v in ok) v else d
    }
    private fun num(k: String, d: Double, lo: Double, hi: Double): Double {
        val v = (o.opt(k) as? Number)?.toDouble() ?: return d
        return if (v.isNaN() || v.isInfinite()) d else v.coerceIn(lo, hi)
    }
    private fun bool(k: String, d: Boolean) = o.opt(k) as? Boolean ?: d
    private fun col(k: String, d: Int): Int {
        val v = o.opt(k) as? String ?: return d
        return if (Regex("^#[0-9a-fA-F]{6}$").matches(v)) Color.parseColor(v) else d
    }

    val mode = str("mode", "transparent", setOf("transparent", "color", "glass"))
    val font = str("font", "Inter")
    val weight = num("weight", 300.0, 200.0, 900.0).toInt()
    val size = num("size", 100.0, 20.0, 300.0).toFloat()
    val spacing = num("spacing", 0.0, -10.0, 30.0).toFloat()
    val lh = num("lh", 100.0, 60.0, 140.0).toFloat()
    val stacked = str("layout", "single", setOf("single", "stacked")) == "stacked"
    val align = str("align", "center", setOf("left", "center", "right"))
    val offX = num("offX", 0.0, -40.0, 40.0).toFloat()
    val offY = num("offY", 0.0, -40.0, 40.0).toFloat()
    val color = col("color", Color.WHITE)
    val gradText = bool("gradText", false)
    val color2 = col("color2", Color.parseColor("#ffd1ff"))
    val opacity = num("opacity", 100.0, 10.0, 100.0).toFloat()
    val shadow = num("shadow", 35.0, 0.0, 100.0).toFloat()
    val glow = num("glow", 0.0, 0.0, 100.0).toFloat()
    val outline = num("outline", 0.0, 0.0, 12.0).toFloat()
    val outlineColor = col("outlineColor", Color.BLACK)
    val gradientCard = str("bgType", "gradient", setOf("solid", "gradient")) == "gradient"
    val bg1 = col("bg1", Color.parseColor("#6d5dfc"))
    val bg2 = col("bg2", Color.parseColor("#ff6bcb"))
    val angle = num("angle", 135.0, 0.0, 360.0).toFloat()
    val cardOpacity = num("cardOpacity", 100.0, 10.0, 100.0).toFloat()
    val radius = num("radius", 32.0, 0.0, 100.0).toFloat()
    val pad = num("pad", 40.0, 0.0, 120.0).toFloat()
    val glassTint = num("glassTint", 12.0, 0.0, 60.0).toFloat()
    val hour12 = bool("hour12", hour12Default)
    val ampm = bool("ampm", true)
    val pad0 = bool("pad0", true)
    val tz = str("tz", "local")
    val dateShow = bool("dateShow", true)
    val dateFmt = str("dateFmt", "long", setOf("long", "medium", "dmy", "mdy", "iso"))
    val weekday = bool("weekday", true)
    val dateSize = num("dateSize", 22.0, 8.0, 80.0).toFloat()
    val dateOpacity = num("dateOpacity", 80.0, 10.0, 100.0).toFloat()
    val dateSpacing = num("dateSpacing", 2.0, 0.0, 30.0).toFloat()

    companion object {
        fun parse(ctx: Context, json: String?): Design {
            val o = try { JSONObject(json ?: "{}") } catch (e: Exception) { JSONObject() }
            return Design(o, !DateFormat.is24HourFormat(ctx))
        }
    }
}
