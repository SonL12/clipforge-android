package com.clipforge.app

enum class MediaType(val label: String) {
    VIDEO("Video"), AUDIO("Audio"), IMAGE("Gambar")
}

object Formats {
    val video = listOf("mp4", "mkv", "mov", "avi", "webm", "flv", "wmv", "m4v", "ts", "3gp", "mpg", "gif")
    val audio = listOf("mp3", "wav", "opus", "ogg", "flac", "aac", "m4a", "wma", "aiff", "ac3")
    val image = listOf("jpg", "jpeg", "png", "webp", "bmp", "tiff", "ico")

    // jpg dan jpeg dianggap sama
    private val alias = mapOf(
        "jpg" to setOf("jpg", "jpeg"),
        "jpeg" to setOf("jpg", "jpeg")
    )

    fun typeOf(ext: String): MediaType? = when (ext.lowercase()) {
        in video -> MediaType.VIDEO
        in audio -> MediaType.AUDIO
        in image -> MediaType.IMAGE
        else -> null
    }

    // Video boleh jadi video/audio, audio hanya audio, gambar hanya gambar
    fun categoriesFor(type: MediaType): List<MediaType> = when (type) {
        MediaType.VIDEO -> listOf(MediaType.VIDEO, MediaType.AUDIO)
        MediaType.AUDIO -> listOf(MediaType.AUDIO)
        MediaType.IMAGE -> listOf(MediaType.IMAGE)
    }

    // Format tujuan, tanpa format file asal (kalau semua file formatnya sama)
    fun targetFormats(category: MediaType, sourceExts: Set<String>): List<String> {
        val all = when (category) {
            MediaType.VIDEO -> video
            MediaType.AUDIO -> audio
            MediaType.IMAGE -> image
        }
        if (sourceExts.size != 1) return all
        val src = sourceExts.first()
        val sama = alias[src] ?: setOf(src)
        return all.filter { it !in sama }
    }
}