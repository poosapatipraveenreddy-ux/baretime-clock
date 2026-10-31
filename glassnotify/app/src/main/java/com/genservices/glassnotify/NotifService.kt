package com.genservices.glassnotify

import android.app.Notification
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Android hands every notification to this service once the user grants "Notification access".
 * Flow: skip system noise -> (optional) save to history -> rules (Rules.kt) -> build Card + chips -> overlay.
 * History is stored ONLY if the user turns it on, and only on this phone.
 * The AI is called ONLY when the user taps the "Summarize" chip.
 */
class NotifService : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    private val labels = HashMap<String, String>()   // package -> app name, looked up once

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (!isRealMessage(sbn)) return
            val rules = Rules.ensure(this)
            val pkg = sbn.packageName
            val n = sbn.notification
            val ex = n.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
            if (title.isEmpty() && text.isEmpty()) return

            val label = labels.getOrPut(pkg) {
                try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
            }

            // Privacy: for chosen apps show only "New message" (nothing private reaches the card, history, chips or AI).
            val hide = pkg in rules.hideText
            val shownTitle = if (hide) "" else title
            val shownText = if (hide) "New message" else text

            if (Prefs.historyOn(this)) History.add(this, pkg, label, shownTitle, shownText)

            val level = rules.decide(pkg, "$title $text")
            if (level == Rules.HIDE) return

            val pm = packageManager
            val appIcon: Drawable? = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { n.smallIcon?.loadDrawable(this) }
            val avatar: Drawable? = try { n.getLargeIcon()?.loadDrawable(this) } catch (e: Exception) { null }

            // Same app + same sender = same chat, so a burst of messages updates one card.
            val card = Card(pkg + "|" + title, label, shownTitle, shownText, avatar, appIcon, n.contentIntent)
            card.accent = Accent.of(pkg, appIcon)
            card.vip = level == Rules.VIP
            card.otp = if (hide) null else Detect.otp(text)

            // ---- chips: link and phone are instant; the address arrives a moment later (below) ----
            val chipsOn = !hide && Prefs.bool(this, "chips", true)
            if (chipsOn) card.chips = Detect.chips(text)

            // ---- "Summarize" chip: only if AI is on, the chat has several messages, and the app is not excluded ----
            if (!hide && card.otp == null && Ai.enabled(this) && !Ai.skipped(this, pkg)) {
                val thread = threadOf(n, title)
                if (thread.size >= Ai.MIN_MESSAGES) {
                    val joined = Ai.pack(thread)
                    val key = card.key
                    card.chips = card.chips + Chip("✨ Summarize", null) {
                        OverlayManager.showBody(key, "Summarizing…", 15)
                        Ai.summarize(applicationContext, joined) { s -> main.post { OverlayManager.showBody(key, s, 20) } }
                    }
                }
            }

            main.post {
                OverlayManager.show(applicationContext, card)
                if (card.vip) Sound.alert(applicationContext)
            }

            // ---- address: runs on a background thread, then adds one more chip if the card is still showing ----
            if (chipsOn && Detect.mayHaveAddress(text)) {
                val key = card.key
                Detect.io.execute {
                    val a = Detect.address(applicationContext, text)
                    if (a != null) main.post { OverlayManager.addChip(key, Detect.addressChip(a)) }
                }
            }

            // Optional: remove the plain system notification (it stays in History if that is on).
            if (Prefs.hideOriginal(this)) cancelNotification(sbn.key)
        } catch (e: Exception) {
            // Never crash the listener: Android would stop sending us notifications.
        }
    }

    /** The recent messages of this chat ("Sender: text"), oldest first. Chat apps put them in EXTRA_MESSAGES. */
    private fun threadOf(n: Notification, title: String): List<String> {
        val ex = n.extras
        val out = ArrayList<String>()
        @Suppress("DEPRECATION")
        val arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (arr != null) {
            for (m in Notification.MessagingStyle.Message.getMessagesFromBundleArray(arr)) {
                val who = (if (Build.VERSION.SDK_INT >= 28) m.senderPerson?.name?.toString() else null) ?: title
                val t = m.text?.toString() ?: continue
                out.add("$who: $t")
            }
        } else {
            ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { out.add(it.toString()) }
        }
        return out.takeLast(8)
    }

    /** Things that are never a "message": our own, ongoing, group summaries, music, downloads, calls, navigation. */
    private fun isRealMessage(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false
        val n = sbn.notification
        if ((n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY)) != 0) return false
        when (n.category) {
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_NAVIGATION -> return false
        }
        return true
    }
}
