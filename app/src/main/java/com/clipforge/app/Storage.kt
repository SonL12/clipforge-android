package com.clipforge.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.io.OutputStream

class SaveTarget(
    val stream: OutputStream,
    val label: String,
    val uri: Uri?,
    val mime: String,
    private val onFinish: () -> Unit,
    private val onAbort: () -> Unit
) {
    fun finish() { stream.close(); onFinish() }
    fun abort() { runCatching { stream.close() }; onAbort() }
}

object Storage {

    private fun safe(name: String) =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "hasil" }

    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    fun openDownload(context: Context, rawName: String): SaveTarget {
        val name = safe(rawName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) openMediaStore(context, name)
        else openLegacy(context, name)
    }

    // Android 10+: masuk ke Download/ClipForge tanpa izin tambahan
    @android.annotation.TargetApi(29)
    private fun openMediaStore(context: Context, name: String): SaveTarget {
        val resolver = context.contentResolver
        val mime = mimeOf(name)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClipForge")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Gagal membuat file di folder Download")
        val stream = resolver.openOutputStream(uri)
            ?: throw IOException("Gagal membuka file tujuan")
        return SaveTarget(
            stream, "Download/ClipForge/$name", uri, mime,
            onFinish = {
                val v = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, v, null, null)
            },
            onAbort = { resolver.delete(uri, null, null) }
        )
    }

    // Android 9 ke bawah: folder app (tanpa izin)
    private fun openLegacy(context: Context, name: String): SaveTarget {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        dir.mkdirs()
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var file = File(dir, name)
        var n = 1
        while (file.exists()) {
            file = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
            n++
        }
        val stream = file.outputStream()
        return SaveTarget(
            stream, "Android/data/${context.packageName}/files/Download/${file.name}",
            null, mimeOf(name),
            onFinish = { }, onAbort = { file.delete() }
        )
    }
}