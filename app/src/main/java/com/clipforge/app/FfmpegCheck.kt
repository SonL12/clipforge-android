package com.clipforge.app

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig

private val DICEK = listOf(
    "h264_mediacodec", "libx264", "libopenh264",
    "libvpx-vp9", "libmp3lame", "libopus", "libvorbis", "libwebp",
    "aac", "mpeg4", "wmv2", "flv", "mpeg2video"
)

@Composable
fun FfmpegCheck() {
    var hasil by remember { mutableStateOf("") }
    var sibuk by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            enabled = !sibuk,
            onClick = {
                sibuk = true
                FFmpegKit.executeWithArgumentsAsync(arrayOf("-hide_banner", "-encoders")) { session ->
                    val out = session.output ?: ""
                    val teks = "FFmpeg ${FFmpegKitConfig.getFFmpegVersion()}\n" +
                        DICEK.joinToString("\n") { n ->
                            val ada = Regex("\\s" + Regex.escape(n) + "\\s").containsMatchIn(out)
                            (if (ada) "✅ " else "❌ ") + n
                        }
                    Handler(Looper.getMainLooper()).post {
                        hasil = teks
                        sibuk = false
                    }
                }
            }
        ) { Text(if (sibuk) "Mengecek…" else "Cek FFmpeg") }

        if (hasil.isNotEmpty()) {
            Text(hasil, style = MaterialTheme.typography.bodySmall)
        }
    }
}