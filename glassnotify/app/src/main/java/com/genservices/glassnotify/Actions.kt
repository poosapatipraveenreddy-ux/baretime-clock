package com.genservices.glassnotify

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Handles notification actions like Direct Reply (WhatsApp, Telegram, etc.)
 * and Mark as Read using Android's native Notification.Action and RemoteInput.
 */
object Actions {
    fun findReply(n: Notification): Notification.Action? {
        val actions = n.actions ?: return null
        for (a in actions) {
            if (a.remoteInputs?.isNotEmpty() == true) return a
        }
        return null
    }

    fun findMarkRead(n: Notification): PendingIntent? {
        val actions = n.actions ?: return null
        for (a in actions) {
            val t = a.title?.toString()?.lowercase() ?: ""
            if (t.contains("read") || t.contains("mark")) return a.actionIntent
        }
        return null
    }

    fun sendReply(c: Context, action: Notification.Action, text: String): Boolean {
        val ri = action.remoteInputs?.firstOrNull() ?: return false
        return try {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(ri.resultKey, text)
            RemoteInput.addResultsToIntent(arrayOf(ri), intent, bundle)
            action.actionIntent.send(c, 0, intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun markRead(actionIntent: PendingIntent): Boolean {
        return try {
            actionIntent.send()
            true
        } catch (e: Exception) {
            false
        }
    }
}
