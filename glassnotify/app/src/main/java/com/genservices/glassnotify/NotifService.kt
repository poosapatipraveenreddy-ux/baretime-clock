package com.genservices.glassnotify

import android.app.Notification
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Android hands every notification to this service once the user grants "Notification access".
 * We turn it into a Card and give it to the overlay. Nothing is stored or sent anywhere.
 *
 * WHERE TO ADD RULES: the `shouldShow` function below. One place, plain if-statements.
 */
class NotifService : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (!shouldShow(sbn)) return
            val n = sbn.notification
            val ex = n.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
            if (title.isEmpty() && text.isEmpty()) return

            val pm = packageManager
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { sbn.packageName }
            val appIcon: Drawable? = try { pm.getApplicationIcon(sbn.packageName) } catch (e: Exception) { n.smallIcon?.loadDrawable(this) }
            val avatar: Drawable? = try { n.getLargeIcon()?.loadDrawable(this) } catch (e: Exception) { null }

            // Same app + same sender = same chat, so a burst of messages updates one card.
            val card = Card(sbn.packageName + "|" + title, label, title, text, avatar, appIcon, n.contentIntent)
            main.post { OverlayManager.show(applicationContext, card) }
        } catch (e: Exception) {
            // Never crash the listener: Android would stop sending us notifications.
        }
    }

    /** RULES GO HERE. Return false to skip a notification (the normal system notification still appears). */
    private fun shouldShow(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false                       // our own
        if (sbn.packageName in Prefs.blocked(this)) return false               // user's blocked apps
        val n = sbn.notification
        if ((n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY)) != 0) return false
        when (n.category) {
            Notification.CATEGORY_TRANSPORT,   // music players
            Notification.CATEGORY_PROGRESS,    // downloads
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_CALL,        // incoming calls keep the phone's own screen
            Notification.CATEGORY_NAVIGATION -> return false
        }
        // IDEAS (each is one more `if`): quiet hours, keyword block list, VIP senders, hide promotions.
        return true
    }
}
