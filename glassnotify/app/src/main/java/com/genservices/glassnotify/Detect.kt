package com.genservices.glassnotify

/** Finds a one-time code in a message. Only looks when the text has digits. */
object Detect {
    // "OTP is 482913", "your code: 1234", "verification code 556677"
    private val afterWord = Regex("(?i)(?:otp|code|passcode|pin|verification)[^0-9]{0,25}(\\d{4,8})(?!\\d)")
    // "482913 is your OTP"
    private val beforeWord = Regex("(?i)(?<!\\d)(\\d{4,8})(?!\\d)[^0-9]{0,6}(?:is|as)\\s+(?:your|the)\\s+(?:otp|code|pin)")

    fun otp(text: String): String? {
        if (text.none { it.isDigit() }) return null
        return afterWord.find(text)?.groupValues?.get(1)
            ?: beforeWord.find(text)?.groupValues?.get(1)
    }
}
