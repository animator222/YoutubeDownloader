package com.animator222.ytdownloader

import android.content.Context

object Prefs {
    private const val FILE = "settings"
    private const val KEY_TURBO = "turbo"

    /** Turbo = multi-connection downloads through aria2c (16 parallel connections). */
    fun turbo(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_TURBO, true)

    fun setTurbo(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_TURBO, enabled).apply()
    }
}
