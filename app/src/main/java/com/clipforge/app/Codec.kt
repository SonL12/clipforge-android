package com.clipforge.app

object Codec {
    // H.264 lewat encoder hardware HP (libx264 tidak ada di build LGPL)
    private val H264_HW = listOf(
        "-c:v", "h264_mediacodec", "-b:v", "5M", "-c:a", "aac"
    )

    // Cadangan kalau h264_mediacodec ternyata tidak tersedia
    private val H264_FALLBACK = listOf(
        "-c:v", "mpeg4", "-q:v", "4", "-c:a", "aac"
    )

    // Pilih salah satu: true kalau tombol Cek FFmpeg menunjukkan h264_mediacodec ✅
    const val PAKAI_H264_HW = true

    private val H264 = if (PAKAI_H264_HW) H264_HW else H264_FALLBACK

    val settings: Map<String, List<String>> = mapOf(
        // ---- VIDEO ----
        "mp4"  to H264,
        "mkv"  to H264,
        "mov"  to H264,
        "m4v"  to H264,
        "ts"   to H264,
        "3gp"  to H264,
        "webm" to listOf("-c:v", "libvpx-vp9", "-deadline", "realtime", "-cpu-used", "8",
                         "-row-mt", "1", "-crf", "33", "-b:v", "0", "-c:a", "libopus"),
        "avi"  to listOf("-c:v", "mpeg4", "-c:a", "libmp3lame"),
        "flv"  to listOf("-c:v", "flv", "-c:a", "libmp3lame"),
        "wmv"  to listOf("-c:v", "wmv2", "-c:a", "wmav2"),
        "mpg"  to listOf("-c:v", "mpeg2video", "-q:v", "4", "-c:a", "mp2"),
        "gif"  to listOf("-vf", "fps=12,scale='min(480,iw)':-2:flags=lanczos", "-an"),

        // ---- AUDIO ----
        "mp3"  to listOf("-c:a", "libmp3lame", "-q:a", "2"),
        "wav"  to listOf("-c:a", "pcm_s16le"),
        "opus" to listOf("-c:a", "libopus", "-b:a", "96k"),
        "ogg"  to listOf("-c:a", "libvorbis", "-q:a", "5"),
        "flac" to listOf("-c:a", "flac"),
        "aac"  to listOf("-c:a", "aac", "-b:a", "192k"),
        "m4a"  to listOf("-c:a", "aac", "-b:a", "192k"),
        "wma"  to listOf("-c:a", "wmav2", "-b:a", "192k"),
        "aiff" to listOf("-c:a", "pcm_s16be"),
        "ac3"  to listOf("-c:a", "ac3", "-b:a", "192k"),

        // ---- GAMBAR ----
        "jpg"  to listOf("-c:v", "mjpeg", "-q:v", "2"),
        "jpeg" to listOf("-c:v", "mjpeg", "-q:v", "2"),
        "png"  to listOf("-c:v", "png"),
        "webp" to listOf("-c:v", "libwebp", "-q:v", "80"),
        "bmp"  to listOf("-c:v", "bmp"),
        "tiff" to listOf("-c:v", "tiff"),
        "ico"  to listOf("-vf", "scale='min(256,iw)':'min(256,ih)':force_original_aspect_ratio=decrease")
    )

    // Container yang boleh diisi H.264 + AAC tanpa re-encode (remux)
    val remuxOk = setOf("mp4", "mkv", "mov", "m4v", "ts")

    // Pengaman: semua format di dropdown WAJIB punya setting di sini
    init {
        val semua = Formats.video + Formats.audio + Formats.image
        val kurang = semua.filter { it !in settings }
        require(kurang.isEmpty()) { "Format belum punya setting di Codec: $kurang" }
    }
}