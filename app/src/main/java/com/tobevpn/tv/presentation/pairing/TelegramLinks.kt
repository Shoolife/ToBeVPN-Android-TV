package com.tobevpn.tv.presentation.pairing

import android.net.Uri

object TelegramLinks {
    private const val DEFAULT_BOT_USERNAME = "meow_meow_vpn_bot"

    fun buildWebStartLink(
        startParam: String,
        botUsername: String = DEFAULT_BOT_USERNAME,
    ): String = Uri.Builder()
        .scheme("https")
        .authority("t.me")
        .appendPath(botUsername)
        .appendQueryParameter("start", startParam)
        .build()
        .toString()
}
