package com.clipforge.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
private fun PilihanDropdown(
    label: String,
    options: List<Pair<String, String>>,   // nilai to teks tampilan
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelLarge)
    Box {
        OutlinedButton(enabled = enabled, onClick = { expanded = true }) {
            Text(options.firstOrNull { it.first == value }?.second ?: value)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (v, t) ->
                DropdownMenuItem(text = { Text(t) }, onClick = { onChange(v); expanded = false })
            }
        }
    }
}

@Composable
fun SubtitleScreen(onBusy: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf(emptyList<PickedFile>()) }
    var mode by remember { mutableStateOf("file") }          // "file" atau "audio"
    var language by remember { mutableStateOf("id") }
    var fmt by remember { mutableStateOf("srt") }
    var gaya by remember { mutableStateOf("28") }
    var ukuran by remember { mutableStateOf("sedang") }
    var warna by remember { mutableStateOf("putih") }
    var posisi by remember { mutableStateOf("tengah") }
    var latar by remember { mutableStateOf("hitam") }
    var rasio by remember { mutableStateOf("vertikal") }
    var message by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val log = remember { mutableStateListOf<String>() }

    LaunchedEffect(running) { onBusy(running) }

    fun resetSemua() {
        files = emptyList()
        log.clear()
        message = ""
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val picked = uris.map { PickedFile(it, queryName(context, it)) }
            val diizinkan = if (mode == "audio") listOf(MediaType.AUDIO)
            else listOf(MediaType.VIDEO, MediaType.AUDIO)
            val salah = picked.filter { Formats.typeOf(it.ext) !in diizinkan }
            log.clear()
            if (salah.isNotEmpty()) {
                files = emptyList()
                message = (if (mode == "audio") "⚠️ Mode ini hanya untuk file audio. Bukan: "
                else "⚠️ Subtitle hanya untuk video atau audio. Bukan: ") +
                    salah.joinToString { it.name }
            } else {
                files = picked
                message = "📁 ${picked.size} file dipilih"
            }
        }
    }

    fun mulai() {
        if (Prefs.serverUrl(context).isBlank() || Prefs.apiKey(context).isBlank()) {
            message = "⚠️ Isi URL server dan API key dulu di bagian Server."
            return
        }
        val daftar = files
        val modeAudio = mode == "audio"
        val path = if (modeAudio) "/audio-video" else "/subtitle"
        val kirimFmt = if (modeAudio) "mp4" else fmt
        val extra = mutableMapOf("language" to language, "maxchars" to gaya)
        if (modeAudio) {
            extra["ukuran"] = ukuran
            extra["warna"] = warna
            extra["posisi"] = posisi
            extra["latar"] = latar
            extra["rasio"] = rasio
        }

        log.clear()
        daftar.forEach { log.add("⏸️ ${it.name}: antre") }

        job = scope.launch {
            running = true
            runCatching { KeepAliveService.start(context) }
            val berhasil = mutableListOf<ConvertResult>()
            var gagal = 0
            try {
                daftar.forEachIndexed { i, f ->
                    try {
                        val hasil = convertFile(context, f, kirimFmt, path, extra) { log[i] = it }
                        berhasil.add(hasil)
                        log[i] = "✅ ${f.name} → ${hasil.label}"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        gagal++
                        log[i] = "❌ ${f.name}: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
                Notifier.selesai(context, berhasil, gagal)
            } catch (e: CancellationException) {
                for (i in log.indices) {
                    val t = log[i]
                    log[i] = when {
                        t.startsWith("✅") || t.startsWith("❌") -> t
                        t.startsWith("⏸️") -> "⏹️ ${daftar[i].name}: dilewati"
                        else -> "🛑 ${daftar[i].name}: dibatalkan"
                    }
                }
                throw e
            } finally {
                running = false
                job = null
                KeepAliveService.stop(context)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Subtitle generator", style = MaterialTheme.typography.titleMedium)
        Text(
            "Server memakai Whisper. Pertama kali dipakai, server mengunduh model, " +
                "jadi file pertama bisa lebih lama.",
            style = MaterialTheme.typography.bodySmall
        )

        ServerSettings()

        PilihanDropdown(
            "Hasil",
            listOf(
                "file" to "File subtitle (.srt / .vtt / .txt)",
                "audio" to "Video dari audio (subtitle menempel)"
            ),
            mode, !running
        ) { mode = it; resetSemua() }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running,
                onClick = {
                    picker.launch(
                        if (mode == "audio") arrayOf("audio/*") else arrayOf("video/*", "audio/*")
                    )
                }
            ) { Text(if (mode == "audio") "Pilih audio" else "Pilih video / audio") }

            if (files.isNotEmpty()) {
                OutlinedButton(enabled = !running, onClick = { resetSemua() }) {
                    Text("Batal / hapus semua")
                }
            }
        }

        if (message.isNotEmpty()) Text(message)

        if (log.isEmpty()) {
            files.forEach { f ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "• ${f.name}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { files = files - f }) { Text("✕") }
                }
            }
        }

        if (files.isNotEmpty()) {
            PilihanDropdown(
                "Bahasa ucapan",
                listOf("id" to "Indonesia", "en" to "English", "auto" to "Deteksi otomatis"),
                language, !running
            ) { language = it }

            PilihanDropdown(
                "Panjang teks",
                listOf(
                    "0" to "Normal (kalimat panjang)",
                    "28" to "Pendek (cocok TikTok)",
                    "14" to "Sangat pendek (1-3 kata)"
                ),
                gaya, !running
            ) { gaya = it }

            if (mode == "file") {
                PilihanDropdown(
                    "Format subtitle",
                    listOf("srt" to "SRT", "vtt" to "VTT", "txt" to "Teks biasa (TXT)"),
                    fmt, !running
                ) { fmt = it }
            } else {
                PilihanDropdown(
                    "Format layar",
                    listOf("vertikal" to "Vertikal 9:16 (TikTok)", "horizontal" to "Horizontal 16:9"),
                    rasio, !running
                ) { rasio = it }
                PilihanDropdown(
                    "Warna latar",
                    listOf("hitam" to "Hitam", "biru" to "Biru tua", "ungu" to "Ungu tua"),
                    latar, !running
                ) { latar = it }
                PilihanDropdown(
                    "Ukuran teks",
                    listOf("kecil" to "Kecil", "sedang" to "Sedang", "besar" to "Besar"),
                    ukuran, !running
                ) { ukuran = it }
                PilihanDropdown(
                    "Warna teks",
                    listOf("putih" to "Putih", "kuning" to "Kuning"),
                    warna, !running
                ) { warna = it }
                PilihanDropdown(
                    "Posisi teks",
                    listOf("tengah" to "Tengah", "bawah" to "Bawah"),
                    posisi, !running
                ) { posisi = it }
            }

            if (!running) {
                Button(onClick = { mulai() }) {
                    Text(if (mode == "audio") "Buat video" else "Buat subtitle")
                }
            } else {
                Button(
                    onClick = { job?.cancel() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Batalkan proses") }
            }
        }

        log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}