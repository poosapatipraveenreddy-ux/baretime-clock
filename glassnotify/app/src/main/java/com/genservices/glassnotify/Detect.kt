package com.genservices.glassnotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Patterns
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLinks
import java.util.concurrent.Executors

/** One tappable button on a card. It either opens an Intent (link, dialer, map) or runs an action. */
class Chip(val label: String, val intent: Intent?, val action: (() -> Unit)? = null)

/** Finds one-time codes, links, phone numbers and addresses in a message. */
object Detect {
    /** One background thread for the slower address detection. */
    val io = Executors.newSingleThreadExecutor()

    // ---------------------------------------------------------------- one-time codes (from Pack 1)
    private val afterWord = Regex("(?i)(?:otp|code|passcode|pin|verification)[^0-9]{0,25}(\\d{4,8})(?!\\d)")
    private val beforeWord = Regex("(?i)(?<!\\d)(\\d{4,8})(?!\\d)[^0-9]{0,6}(?:is|as)\\s+(?:your|the)\\s+(?:otp|code|pin)")

    fun otp(text: String): String? {
        if (text.none { it.isDigit() }) return null
        return afterWord.find(text)?.groupValues?.get(1)
            ?: beforeWord.find(text)?.groupValues?.get(1)
    }

    // ---------------------------------------------------------------- link + phone (instant, no internet)
    private val phone = Regex("(?<![\\d])\\+?\\d[\\d\\s-]{8,14}\\d(?!\\d)")
    private val dateLike = Regex("\\d{4}-\\d{2}-\\d{2}|\\d{1,2}-\\d{1,2}-\\d{2,4}")

    /** At most one link chip and one phone chip. */
    fun chips(text: String): List<Chip> {
        val out = ArrayList<Chip>(2)

        val m = Patterns.WEB_URL.matcher(text)
        while (m.find()) {
            val start = m.start()
            if (start > 0 && text[start - 1] == '@') continue            // part of an email address
            val raw = m.group()
            val url = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
            val host = raw.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
            out.add(Chip("Open $host", Intent(Intent.ACTION_VIEW, Uri.parse(url))))
            break
        }

        for (mm in phone.findAll(text)) {
            if (dateLike.containsMatchIn(mm.value)) continue             // "2026-10-31 18" is a date, not a phone
            val digits = mm.value.filter { it.isDigit() }
            if (digits.length !in 10..13) continue
            val num = (if (mm.value.trim().startsWith("+")) "+" else "") + digits
            // ACTION_DIAL only opens the dialer with the number filled in; no call permission needed
            out.add(Chip("Call " + mm.value.trim(), Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))))
            break
        }
        return out
    }

    // ---------------------------------------------------------------- address (Android 9+, on-device)
    private val addressHint = Regex(
        "(?i)\\b(road|rd|street|st|nagar|colony|sector|floor|flat|near|lane|avenue|ave|block|plot|apartment|opp|opposite|marg|chowk)\\b"
    )

    /** Cheap pre-check so the slower classifier only runs on messages that look like an address. */
    fun mayHaveAddress(text: String): Boolean =
        Build.VERSION.SDK_INT >= 28 && text.length in 12..400 &&
            text.any { it.isDigit() } && addressHint.containsMatchIn(text)

    /** Uses Android's own on-device text classifier. Call from a BACKGROUND thread only. */
    fun address(c: Context, text: String): String? {
        if (Build.VERSION.SDK_INT < 28) return null
        return try {
            val tcm = c.getSystemService(TextClassificationManager::class.java) ?: return null
            val links = tcm.textClassifier.generateLinks(TextLinks.Request.Builder(text).build()).links
            for (l in links) {
                for (i in 0 until l.entityCount) {
                    if (l.getEntity(i) == TextClassifier.TYPE_ADDRESS &&
                        l.getConfidenceScore(TextClassifier.TYPE_ADDRESS) >= 0.5f
                    ) {
                        return text.substring(l.start, l.end)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun addressChip(address: String) =
        Chip("Map: $address", Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))))
}
