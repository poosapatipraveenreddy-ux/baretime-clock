package com.genservices.glassnotify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * One simple setup screen (built in code, no layout files):
 *   1. Notification access   2. Display over other apps   3. Battery   then settings and a test button.
 * WHERE TO ADD A SETTING: add a line in Prefs.kt, then one more button/switch in `onCreate` below.
 */
class MainActivity : Activity() {
    private lateinit var s1: TextView
    private lateinit var s2: TextView
    private lateinit var s3: TextView
    private lateinit var posBtn: Button
    private lateinit var secBtn: Button
    private lateinit var blurBtn: Button
    private lateinit var blocked: EditText

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun label(text: String, size: Float = 14f, bold: Boolean = false, color: Int = Color.WHITE): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.setPadding(0, dp(6f), 0, dp(2f))
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20f), dp(28f), dp(20f), dp(28f))
        val sv = ScrollView(this)
        sv.addView(col)
        setContentView(sv)

        col.addView(label("GlassNotify", 26f, true))
        col.addView(label("Important notifications as a floating glass card. Finish the 3 steps below once.", 14f, false, Color.argb(200, 255, 255, 255)))

        col.addView(label("Step 1 · Notification access", 16f, true))
        s1 = label("", 13f); col.addView(s1)
        col.addView(button("Open notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        col.addView(label("If the switch is greyed out (Android 13+, app installed from a file): open this app's info page, tap ⋮ (top right) → “Allow restricted settings”, then try again.", 12f, false, Color.argb(170, 255, 255, 255)))
        col.addView(button("Open app info page") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })

        col.addView(label("Step 2 · Display over other apps", 16f, true))
        s2 = label("", 13f); col.addView(s2)
        col.addView(button("Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        col.addView(label("Step 3 · Keep it running", 16f, true))
        s3 = label("", 13f); col.addView(s3)
        col.addView(button("Battery: don’t restrict this app") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        col.addView(label("On Xiaomi / Oppo / Vivo / Samsung also allow “Autostart” and set battery to “No restrictions” in the phone's app settings, or the phone may stop the service.", 12f, false, Color.argb(170, 255, 255, 255)))

        col.addView(label("Look & behaviour", 16f, true))
        posBtn = button("") {
            Prefs.setTop(this, !Prefs.top(this)); refresh()
        }
        col.addView(posBtn)
        secBtn = button("") {
            val order = intArrayOf(3, 5, 8, 12)
            val i = order.indexOf(Prefs.seconds(this))
            Prefs.setSeconds(this, order[(i + 1) % order.size]); refresh()
        }
        col.addView(secBtn)
        blurBtn = button("") {
            Prefs.setBlur(this, !Prefs.blur(this)); refresh()
        }
        col.addView(blurBtn)
        col.addView(label("Never show cards for these apps (package names, comma separated):", 13f))
        blocked = EditText(this)
        blocked.setTextColor(Color.WHITE)
        blocked.setHintTextColor(Color.argb(120, 255, 255, 255))
        blocked.hint = "com.android.vending, com.whatsapp"
        blocked.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        blocked.setText(Prefs.blockedText(this))
        col.addView(blocked)

        col.addView(label("Try it", 16f, true))
        col.addView(button("Send a test card") { sendTest() })
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.gravity = Gravity.CENTER
        b.setOnClickListener { onClick() }
        return b
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPause() {
        Prefs.setBlockedText(this, blocked.text.toString())
        super.onPause()
    }

    private fun listenerOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return s.split(':').any { it.startsWith("$packageName/") }
    }

    private fun batteryOk(): Boolean =
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun mark(ok: Boolean, good: String, bad: String) = if (ok) "✓ $good" else "✗ $bad"

    private fun refresh() {
        s1.text = mark(listenerOn(), "Allowed", "Not allowed yet")
        s1.setTextColor(if (listenerOn()) Color.rgb(120, 230, 150) else Color.rgb(255, 150, 150))
        val o = OverlayManager.canDraw(this)
        s2.text = mark(o, "Allowed", "Not allowed yet")
        s2.setTextColor(if (o) Color.rgb(120, 230, 150) else Color.rgb(255, 150, 150))
        val b = batteryOk()
        s3.text = mark(b, "Not restricted", "Restricted (recommended to change)")
        s3.setTextColor(if (b) Color.rgb(120, 230, 150) else Color.rgb(255, 200, 120))
        posBtn.text = "Card position: " + if (Prefs.top(this)) "Top" else "Bottom"
        secBtn.text = "Show for: ${Prefs.seconds(this)} seconds"
        val blurPossible = Build.VERSION.SDK_INT >= 31
        blurBtn.text = "Glass blur: " + (if (!blurPossible) "not available on this Android" else if (Prefs.blur(this)) "On" else "Off")
        blurBtn.visibility = View.VISIBLE
    }

    private fun sendTest() {
        if (!OverlayManager.canDraw(this)) {
            Toast.makeText(this, "Do step 2 first (display over other apps).", Toast.LENGTH_LONG).show()
            return
        }
        Prefs.setBlockedText(this, blocked.text.toString())
        val icon = try { packageManager.getApplicationIcon(packageName) } catch (e: Exception) { null }
        val card = Card("test|Aanya", "Messages", "Aanya", "Hey! This is how a GlassNotify card looks. Tap me, or swipe me away.", null, icon, null)
        OverlayManager.show(applicationContext, card)
    }
}
