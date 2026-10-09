package com.clipforge.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

// Tebakan batas terowongan Cloudflare gratis, belum dicek. Ubah kalau ternyata beda.
const val MAX_UPLOAD_MB = 100L

class ConvertResult(
    val name: String,
    val label: String,
    val uri: Uri?,
    val mime: String,
    val text: String? = null
)

private class TeeOutputStream(private val a: OutputStream, private val b: OutputStream) : OutputStream() {
    override fun write(x: Int) { a.write(x); b.write(x) }
    override fun write(buf: ByteArray, off: Int, len: Int) { a.write(buf, off, len); b.write(buf, off, len) }
    override fun flush() { a.flush() }
}

suspend fun convertFile(
    context: Context,
    f: PickedFile,
    fmt: String,
    path: String = "/jobs",
    extra: Map<String, String> = emptyMap(),
    keepText: Boolean = false,
    update: (String) -> Unit
): ConvertResult {
    val base = ServerClient.normalizeUrl(Prefs.serverUrl(context))
    val key = Prefs.apiKey(context).trim()
    var jobId: String? = null

    try {
        return withContext(Dispatchers.IO) {
            val size = ServerClient.fileSize(context, f.uri)
            if (size > MAX_UPLOAD_MB * 1024 * 1024) {
                throw IOException("File lebih dari $MAX_UPLOAD_MB MB, kemungkinan ditolak oleh terowongan")
            }

            update("⬆️ ${f.name}: mengunggah 0%")
            val id = ServerClient.createJob(context, base, key, f.uri, f.name, fmt, path, extra) { pct ->
                update("⬆️ ${f.name}: mengunggah $pct%")
            }
            jobId = id

            update("⏳ ${f.name}: menunggu server…")
            var gagal = 0
            var namaHasil: String? = null
            while (true) {
                delay(1500)
                val s = try {
                    ServerClient.jobStatus(base, key, id).also { gagal = 0 }
                } catch (e: IOException) {
                    gagal++
                    if (gagal >= 5) throw IOException("Koneksi ke server terputus: ${e.message}")
                    continue
                }
                when (s.status) {
                    "queued" -> update("⏳ ${f.name}: antre di server…")
                    "running" -> update("⚙️ ${f.name}: sedang diproses…")
                    "error" -> throw Exception(s.error ?: "Gagal di server")
                    "cancelled" -> throw Exception("Dibatalkan di server")
                    "done" -> { namaHasil = s.filename; break }
                }
            }

            val outName = namaHasil ?: "${f.name.substringBeforeLast('.')}.$fmt"
            val target = Storage.openDownload(context, outName)
            val salinan = if (keepText) ByteArrayOutputStream() else null
            try {
                val tujuan: OutputStream = if (salinan != null) TeeOutputStream(target.stream, salinan) else target.stream
                ServerClient.download(base, key, id, tujuan) { bytes ->
                    update("⬇️ ${f.name}: mengunduh ${bytes / 1024} KB")
                }
                target.finish()
            } catch (e: Throwable) {
                target.abort()
                throw e
            }
            ConvertResult(outName, target.label, target.uri, target.mime,
                salinan?.toString(Charsets.UTF_8.name()))
        }
    } finally {
        val id = jobId
        if (id != null) {
            withContext(NonCancellable + Dispatchers.IO) {
                ServerClient.deleteJob(base, key, id)
            }
        }
    }
}