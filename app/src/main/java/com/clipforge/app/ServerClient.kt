package com.clipforge.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ServerException(val code: Int, message: String) : Exception(message)

data class JobStatus(val status: String, val error: String?, val filename: String?)

object ServerClient {

    fun normalizeUrl(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        return u
    }

    private fun open(url: String, apiKey: String?, method: String, readTimeoutMs: Int = 30_000): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = readTimeoutMs
        if (apiKey != null) conn.setRequestProperty("X-API-Key", apiKey)
        return conn
    }

    private fun errorDetail(conn: HttpURLConnection): String {
        val code = try { conn.responseCode } catch (e: Exception) { -1 }
        if (code in listOf(502, 503, 504, 530)) {
            return "Server Colab tidak aktif atau terowongan mati (kode $code). " +
                "Jalankan ulang sel server di Colab, lalu tempel URL baru."
        }
        val body = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (e: Exception) { "" }
        val detail = try { JSONObject(body).opt("detail")?.toString() ?: body } catch (e: Exception) { body }
        return detail.take(300)
    }

    private fun statusCode(url: String, apiKey: String?): Int {
        val conn = open(url, apiKey, "GET", 10_000)
        try {
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
            val health = statusCode("$base/health", null)
            if (health != 200) {
                return "❌ Server tidak menjawab dengan benar (kode $health). " +
                    "Link mungkin sudah mati, ambil URL baru dari Colab."
            }
            // Job "cek" tidak ada: key benar = 404, key salah = 401
            when (val k = statusCode("$base/jobs/cek", apiKey.trim())) {
                404 -> "✅ Terhubung, API key benar."
                401, 403 -> "⚠️ Server hidup, tapi API key salah."
                else -> "⚠️ Server hidup, tapi respons tak terduga (kode $k)."
            }
        } catch (e: Exception) {
            "❌ Gagal terhubung: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun fileSize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
        }
        return -1L
    }

    // Unggah file, kembalikan job_id
    suspend fun createJob(
        context: Context, base: String, apiKey: String,
        uri: Uri, name: String, fmt: String,
        path: String = "/jobs",
        extra: Map<String, String> = emptyMap(),
        onProgress: (Int) -> Unit
    ): String {
        val boundary = "----ClipForge" + UUID.randomUUID().toString().replace("-", "")
        val safeName = name.replace("\"", "_").replace("\r", "").replace("\n", "")
        val sb = StringBuilder()
        for ((k, v) in linkedMapOf("fmt" to fmt) + extra) {
            sb.append("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n")
        }
        sb.append("--$boundary\r\n")
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"$safeName\"\r\n")
        sb.append("Content-Type: application/octet-stream\r\n\r\n")
        val head = sb.toString().toByteArray(Charsets.UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val size = fileSize(context, uri)

        val conn = open("$base$path", apiKey, "POST", 120_000)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        if (size > 0) conn.setFixedLengthStreamingMode(head.size + size + tail.size)
        else conn.setChunkedStreamingMode(64 * 1024)

        try {
            conn.outputStream.use { out ->
                out.write(head)
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Tidak bisa membaca file")
                input.use {
                    val buf = ByteArray(64 * 1024)
                    var sent = 0L
                    var lastPct = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = it.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sent += n
                        if (size > 0) {
                            val pct = (sent * 100 / size).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
                out.write(tail)
            }
            val code = conn.responseCode
            if (code == 401) throw ServerException(401, "API key salah")
            if (code != 200) throw ServerException(code, errorDetail(conn))
            return JSONObject(conn.inputStream.bufferedReader().readText()).getString("job_id")
        } finally {
            conn.disconnect()
        }
    }

    fun jobStatus(base: String, apiKey: String, jobId: String): JobStatus {
        val conn = open("$base/jobs/$jobId", apiKey, "GET")
        try {
            val code = conn.responseCode
            if (code == 404) throw ServerException(404, "Job hilang dari server (Colab mungkin restart)")
            if (code != 200) throw ServerException(code, errorDetail(conn))
            val j = JSONObject(conn.inputStream.bufferedReader().readText())
            return JobStatus(
                j.getString("status"),
                if (j.isNull("error")) null else j.getString("error"),
                if (j.isNull("filename")) null else j.getString("filename")
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(
        base: String, apiKey: String, jobId: String,
        out: OutputStream, onBytes: (Long) -> Unit
    ) {
        val conn = open("$base/jobs/$jobId/download", apiKey, "GET", 120_000)
        try {
            val code = conn.responseCode
            if (code != 200) throw ServerException(code, errorDetail(conn))
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                var lastReport = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    if (total - lastReport >= 256 * 1024) { lastReport = total; onBytes(total) }
                }
                onBytes(total)
            }
        } finally {
            conn.disconnect()
        }
    }

    // Best effort: gagal pun tidak masalah
    fun deleteJob(base: String, apiKey: String, jobId: String) {
        try {
            val conn = open("$base/jobs/$jobId", apiKey, "DELETE", 10_000)
            try { conn.responseCode } finally { conn.disconnect() }
        } catch (e: Exception) { }
    }
}