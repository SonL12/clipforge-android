package com.clipforge.app

import java.net.HttpURLConnection
import java.net.URL

object ServerClient {

    fun normalizeUrl(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        return u
    }

    private fun get(url: String, apiKey: String?): Int {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (apiKey != null) conn.setRequestProperty("X-API-Key", apiKey)
            return conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    // Blocking: panggil dari Dispatchers.IO
    fun testConnection(rawUrl: String, apiKey: String): String {
        val base = normalizeUrl(rawUrl)
        if (base.isEmpty()) return "⚠️ URL server masih kosong."
        return try {
            val health = get("$base/health", null)
            if (health != 200) {
                return "❌ Server tidak menjawab dengan benar (kode $health). " +
                    "Link mungkin sudah mati, ambil URL baru dari Colab."
            }
            // Cek API key: job "cek" tidak ada, jadi key benar = 404, key salah = 401
            when (val k = get("$base/jobs/cek", apiKey.trim())) {
                404 -> "✅ Terhubung, API key benar."
                401, 403 -> "⚠️ Server hidup, tapi API key salah."
                else -> "⚠️ Server hidup, tapi respons tak terduga (kode $k)."
            }
        } catch (e: Exception) {
            "❌ Gagal terhubung: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}