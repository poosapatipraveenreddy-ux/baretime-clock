package com.genservices.glassnotify

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Extra alert for VIP cards only. Respects the phone's silent / vibrate mode. */
object Sound {
    private var last: Ringtone? = null

    fun alert(c: Context) {
        try {
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val mode = am.ringerMode
            if (mode == AudioManager.RINGER_MODE_SILENT) return
            if (Prefs.bool(c, "vipVibrate", true)) vibrate(c)
            if (mode == AudioManager.RINGER_MODE_NORMAL && Prefs.bool(c, "vipSound", false)) {
                last?.stop()
                val saved = Prefs.text(c, "vipSoundUri")
                val uri = if (saved.isEmpty()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(saved)
                last = RingtoneManager.getRingtone(c, uri)
                last?.play()
            }
        } catch (e: Exception) {
            // sound is a bonus; never crash the listener
        }
    }

    private fun vibrate(c: Context) {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 40, 70, 40), -1)
        if (Build.VERSION.SDK_INT >= 31) {
            (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val v = c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(effect)
        }
    }
}
