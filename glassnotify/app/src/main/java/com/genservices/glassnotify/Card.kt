package com.genservices.glassnotify

import android.app.PendingIntent
import android.graphics.drawable.Drawable

/** Everything one glass card needs to show. */
class Card(
    val key: String,            // same key = same chat, so repeated messages update one card
    val appLabel: String,
    val title: String,          // sender or headline
    val text: String,           // message preview
    val avatar: Drawable?,      // contact photo if the notification has one
    val appIcon: Drawable?,
    val open: PendingIntent?    // what to launch when the card is tapped
) {
    var accent: Int = 0         // app color for the glass tint (0 = none)
    var otp: String? = null     // one-time code found in the text, if any
    var vip: Boolean = false    // VIP cards get the extra vibration/sound
    var chips: List<Chip> = emptyList()   // tappable buttons under the message (link, phone, address, summarize)
}
