package app.baretime.clock

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import org.json.JSONObject

/** Fonts bundled in assets/fonts (downloaded at build time by tools/fetch_fonts.py). */
object FontBook {
    class Face(val weight: Int, val file: String, val variable: Boolean)

    private var index: Map<String, List<Face>>? = null
    private val cache = HashMap<String, Typeface>()

    @Synchronized
    fun faces(ctx: Context): Map<String, List<Face>> {
        index?.let { return it }
        val m = HashMap<String, List<Face>>()
        try {
            val root = JSONObject(ctx.assets.open("fonts/index.json").bufferedReader().use { it.readText() })
            for (name in root.keys()) {
                val arr = root.getJSONArray(name)
                m[name] = (0 until arr.length()).map {
                    val e = arr.getJSONObject(it)
                    Face(e.getInt("w"), e.getString("f"), e.optInt("v", 0) == 1)
                }
            }
        } catch (e: Exception) { /* no bundled fonts: system fonts are used */ }
        index = m
        return m
    }

    @Synchronized
    fun typeface(ctx: Context, name: String, weight: Int): Typeface {
        val key = "$name@$weight"
        cache[key]?.let { return it }
        val list = faces(ctx)[name]
        val tf = if (list.isNullOrEmpty()) system(name, weight) else try {
            val f = list.minByOrNull { Math.abs(it.weight - weight) }!!
            if (f.variable && Build.VERSION.SDK_INT >= 26) {
                Typeface.Builder(ctx.assets, "fonts/${f.file}")
                    .setFontVariationSettings("'wght' $weight").build() ?: system(name, weight)
            } else Typeface.createFromAsset(ctx.assets, "fonts/${f.file}")
        } catch (e: Exception) { system(name, weight) }
        cache[key] = tf
        return tf
    }

    private fun system(name: String, weight: Int): Typeface {
        val base = if (name.contains("Mono") || name == "VT323") Typeface.MONOSPACE else Typeface.SANS_SERIF
        return if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false)
        else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
}
