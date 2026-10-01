package com.clipforge.app

import android.content.Context

object Prefs {
    private const val NAME = "clipforge"

    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun serverUrl(c: Context): String = sp(c).getString("server_url", "") ?: ""
    fun apiKey(c: Context): String = sp(c).getString("api_key", "") ?: ""

    fun save(c: Context, url: String, key: String) {
        sp(c).edit().putString("server_url", url).putString("api_key", key).apply()
    }
}