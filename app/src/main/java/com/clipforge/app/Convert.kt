package com.clipforge.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

// Tebakan batas terowongan Cloudflare gratis, belum dicek. Ubah kalau ternyata beda.
const val MAX_UPLOAD_MB = 100L

// Mengembalikan lokasi file hasil. Melempar exception kalau gagal.
suspend fun convertFile(
    context: Context,
    f: PickedFile,
    fmt: String,
    update: (String) -> Unit
): String {
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
            val id = ServerClient.createJob(context, base, key, f.uri, f.name, fmt) { pct ->
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
                    "running" -> update("⚙️ ${f.name}: sedang diconvert…")
                    "error" -> throw Exception(s.error ?: "Gagal di server")
                    "done" -> { namaHasil = s.filename; break }
                }
            }

            val outName = namaHasil ?: "${f.name.substringBeforeLast('.')}.$fmt"
            val target = Storage.openDownload(context, outName)
            try {
                ServerClient.download(base, key, id, target.stream) { bytes ->
                    update("⬇️ ${f.name}: mengunduh ${bytes / 1024} KB")
                }
                target.finish()
            } catch (e: Throwable) {
                target.abort()
                throw e
            }
            target.label
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