package com.zakee.myiptv

import android.content.Context

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("zedtv", Context.MODE_PRIVATE)

    fun lastChannel(ctx: Context): String? = sp(ctx).getString("last_channel", null)
    fun setLastChannel(ctx: Context, id: String) = sp(ctx).edit().putString("last_channel", id).apply()
}
