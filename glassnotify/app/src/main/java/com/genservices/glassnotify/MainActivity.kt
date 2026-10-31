package com.genservices.glassnotify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
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

    private val fields = ArrayList<Pair<String, EditText>>()

    /** On/Off button saved under `key`. */
    private fun toggle(col: LinearLayout, name: String, key: String, def: Boolean) {
        val b = Button(this)
        b.isAllCaps = false
        val upd = { b.text = name + ": " + (if (Prefs.bool(this, key, def)) "On" else "Off") }
        b.setOnClickListener {
            Prefs.setBool(this, key, !Prefs.bool(this, key, def))
            Rules.reload(this)
            upd()
        }
        upd()
        col.addView(b)
    }

    /** Button that cycles through hours, saved under `key`. */
    private fun cycle(col: LinearLayout, name: String, key: String, options: IntArray, def: Int) {
        val b = Button(this)
        b.isAllCaps = false
        val upd = { b.text = name + ": " + "%02d:00".format(Prefs.num(this, key, def)) }
        b.setOnClickListener {
            val i = options.indexOf(Prefs.num(this, key, def))
            Prefs.setNum(this, key, options[(i + 1) % options.size])
            Rules.reload(this)
            upd()
        }
        upd()
        col.addView(b)
    }

    /** Text box saved under `key` when the screen closes. */
    private fun field(col: LinearLayout, caption: String, key: String, hint: String) {
        col.addView(label(caption, 13f))
        val e = EditText(this)
        e.setTextColor(Color.WHITE)
        e.setHintTextColor(Color.argb(120, 255, 255, 255))
        e.hint = hint
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        e.setText(Prefs.text(this, key))
        col.addView(e)
        fields.add(key to e)
    }

    /** Like `field`, but the text is hidden with dots (for the API key). */
    private fun secret(col: LinearLayout, caption: String, key: String) {
        col.addView(label(caption, 13f))
        val e = EditText(this)
        e.setTextColor(Color.WHITE)
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        e.setText(Prefs.text(this, key))
        col.addView(e)
        fields.add(key to e)
    }

    private fun saveFields() {
        Prefs.setBlockedText(this, blocked.text.toString())
        for ((k, e) in fields) Prefs.setText(this, k, e.text.toString())
        Rules.reload(this)
        Ai.reload(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && resultCode == RESULT_OK) {
            val u = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            Prefs.setText(this, "vipSoundUri", u?.toString() ?: "")
        }
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

        col.addView(label("Display", 16f, true))
        toggle(col, "Small pill (sender only)", "pill", false)
        toggle(col, "Tint card with the app's color", "accent", true)

        col.addView(label("Priority & rules", 16f, true))
        field(col, "VIP apps (always show, even in quiet hours):", "vip", "com.whatsapp, com.whatsapp.w4b")
        field(col, "Silent apps (no card):", "silent", "com.instagram.android")
        field(col, "Hide message text for these apps:", "hideText", "com.google.android.apps.messaging")
        field(col, "Never show if the message contains (comma separated):", "kwBlock", "offer, sale, discount")
        field(col, "Always show if the message contains:", "kwAllow", "urgent, emergency")

        col.addView(label("Quiet hours", 16f, true))
        toggle(col, "Quiet hours", "quiet", false)
        cycle(col, "Starts at", "quietFrom", intArrayOf(20, 21, 22, 23, 0), 22)
        cycle(col, "Ends at", "quietTo", intArrayOf(5, 6, 7, 8, 9), 7)

        col.addView(label("VIP alert", 16f, true))
        toggle(col, "VIP vibration", "vipVibrate", true)
        toggle(col, "VIP sound", "vipSound", false)
        col.addView(button("Choose VIP sound") {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            startActivityForResult(i, 77)
        })

        col.addView(label("History", 16f, true))
        toggle(col, "Save history on this phone", "history", false)
        col.addView(button("Open history inbox") { startActivity(Intent(this, InboxActivity::class.java)) })
        toggle(col, "Hide the original notification when a card shows", "hideOrig", false)

        col.addView(label("Chips", 16f, true))
        toggle(col, "Link, phone and address chips", "chips", true)

        col.addView(label("AI summary (optional, uses internet)", 16f, true))
        col.addView(label("Only when you tap “Summarize” on a chat with 3+ messages, the last messages are sent to the service below. Nothing is sent automatically.", 12f, false, Color.argb(170, 255, 255, 255)))
        toggle(col, "AI summary", "ai", false)
        secret(col, "API key (kept only on this phone):", "aiKey")

        val presets = arrayOf(
            Triple("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-1.5-flash"),
            Triple("OpenRouter", "https://openrouter.ai/api/v1", "google/gemini-flash-1.5"),
            Triple("Groq", "https://api.groq.com/openai/v1", "llama-3.1-8b-instant"),
            Triple("NVIDIA", "https://integrate.api.nvidia.com/v1", "meta/llama-3.1-8b-instruct"),
            Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini")
        )
        val presetBtn = Button(this)
        presetBtn.isAllCaps = false
        presetBtn.text = "Load Provider Preset (tap to cycle: Google, OpenRouter, Groq, NVIDIA...)"
        presetBtn.setOnClickListener {
            val curUrl = Prefs.text(this, "aiUrl")
            val nextIdx = (presets.indexOfFirst { it.second == curUrl } + 1) % presets.size
            val (name, url, model) = presets[nextIdx]
            fields.firstOrNull { it.first == "aiUrl" }?.second?.setText(url)
            fields.firstOrNull { it.first == "aiModel" }?.second?.setText(model)
            saveFields()
            Toast.makeText(this, "Loaded $name ($model)", Toast.LENGTH_SHORT).show()
        }
        col.addView(presetBtn)

        field(col, "Service address (OpenAI-style, must be https):", "aiUrl", "https://generativelanguage.googleapis.com/v1beta/openai")
        field(col, "Model:", "aiModel", "gemini-1.5-flash")
        field(col, "Apps NEVER sent to the AI (banks, private chats):", "aiSkip", "com.google.android.apps.messaging")
        col.addView(button("Test AI connection") {
            saveFields()
            Toast.makeText(this, "Testing…", Toast.LENGTH_SHORT).show()
            Ai.summarize(
                applicationContext,
                "Rahul: Are we meeting at 6?\nRahul: Bring the documents\nRahul: Cafe near the station"
            ) { r -> runOnUiThread { Toast.makeText(this, r, Toast.LENGTH_LONG).show() } }
        })

        col.addView(label("Try it", 16f, true))
        col.addView(button("Send a test card") { sendTest() })
        col.addView(button("Send a test code (OTP) card") { sendTest(otp = "482913") })
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
        saveFields()
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

    private fun sendTest(otp: String? = null) {
        if (!OverlayManager.canDraw(this)) {
            Toast.makeText(this, "Do step 2 first (display over other apps).", Toast.LENGTH_LONG).show()
            return
        }
        Prefs.setBlockedText(this, blocked.text.toString())
        val icon = try { packageManager.getApplicationIcon(packageName) } catch (e: Exception) { null }
        val msg = if (otp != null) "$otp is your verification code. Do not share it." else "Hey! This is how a GlassNotify card looks. Tap me, or swipe me away."
        val card = Card("test|Aanya", "Messages", "Aanya", msg, null, icon, null)
        card.accent = Accent.of(packageName, icon)
        card.otp = otp
        OverlayManager.show(applicationContext, card)
    }
}
