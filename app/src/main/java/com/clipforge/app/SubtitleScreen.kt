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
    var language by remember { mutableStateOf("id") }
    var fmt by remember { mutableStateOf("srt") }
    var gaya by remember { mutableStateOf("28") }
    var mode by remember { mutableStateOf("file") }        // "file" atau "video"
    var ukuran by remember { mutableStateOf("sedang") }
    var warna by remember { mutableStateOf("putih") }
    var posisi by remember { mutableStateOf("bawah") }
    var message by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val log = remember { mutableStateListOf<String>() }
    val cues = remember { mutableStateListOf<Cue>() }

    LaunchedEffect(running) { onBusy(running) }

    fun resetSemua() {
        files = emptyList()
        cues.clear()
        log.clear()
        message = ""
    }

    // Satu pintu untuk semua proses ke server
    fun jalankan(
        daftar: List<PickedFile>,
        kirimFmt: String,
        path: String,
        extra: Map<String, String>,
        keepText: Boolean,
        onHasil: (ConvertResult) -> Unit = {}
    ) {
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
                        val hasil = convertFile(context, f, kirimFmt, path, extra, keepText) { log[i] = it }
                        berhasil.add(hasil)
                        log[i] = "✅ ${f.name} → ${hasil.label}"
                        onHasil(hasil)
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

    fun serverSiap(): Boolean {
        if (Prefs.serverUrl(context).isBlank() || Prefs.apiKey(context).isBlank()) {
            message = "⚠️ Isi URL server dan API key dulu di bagian Server."
            return false
        }
        return true
    }

    // Mode "file": buat file subtitle untuk semua file yang dipilih
    fun buatFile() {
        if (!serverSiap()) return
        jalankan(
            files, fmt, "/subtitle",
            mapOf("language" to language, "maxchars" to gaya), false
        )
    }

    // Mode "video", tahap 1: transkripsi, hasilnya masuk editor
    fun buatUntukEdit() {
        if (!serverSiap()) return
        val f = files.firstOrNull() ?: return
        cues.clear()
        jalankan(
            listOf(f), "srt", "/subtitle",
            mapOf("language" to language, "maxchars" to gaya), true
        ) { hasil ->
            cues.addAll(parseSrt(hasil.text ?: ""))
            message = if (cues.isEmpty()) "⚠️ Tidak ada teks yang terbaca." else
                "✏️ ${cues.size} potongan. Perbaiki teks yang salah, lalu tempel ke video."
        }
    }

    // Mode "video", tahap 2: tempel SRT hasil editan ke video
    fun tempelKeVideo() {
        if (!serverSiap()) return
        val f = files.firstOrNull() ?: return
        val srt = toSrt(cues)
        if (srt.isBlank()) {
            message = "⚠️ Semua teks kosong, tidak ada yang bisa ditempel."
            return
        }
        jalankan(
            listOf(f), "mp4", "/burn-srt",
            mapOf("srt" to srt, "ukuran" to ukuran, "warna" to warna, "posisi" to posisi), false
        )
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val picked = uris.map { PickedFile(it, queryName(context, it)) }
            cues.clear()
            log.clear()
            val diizinkan = if (mode == "video") listOf(MediaType.VIDEO)
            else listOf(MediaType.VIDEO, MediaType.AUDIO)
            val salah = picked.filter { Formats.typeOf(it.ext) !in diizinkan }
            if (salah.isNotEmpty()) {
                files = emptyList()
                message = if (mode == "video") "⚠️ Mode video hanya untuk file video. Bukan: "
                else "⚠️ Subtitle hanya untuk video atau audio. Bukan: "
                message += salah.joinToString { it.name }
            } else if (mode == "video" && picked.size > 1) {
                files = picked.take(1)
                message = "📁 Mode video memproses satu video sekali, yang dipakai: ${picked[0].name}"
            } else {
                files = picked
                message = "📁 ${picked.size} file dipilih"
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
                "video" to "Video dengan subtitle (bisa diedit dulu)"
            ),
            mode, !running
        ) { mode = it; resetSemua() }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running,
                onClick = {
                    picker.launch(
                        if (mode == "video") arrayOf("video/*") else arrayOf("video/*", "audio/*")
                    )
                }
            ) { Text(if (mode == "video") "Pilih video" else "Pilih video / audio") }

            if (files.isNotEmpty()) {
                OutlinedButton(enabled = !running, onClick = { resetSemua() }) {
                    Text("Batal / hapus semua")
                }
            }
        }

        if (message.isNotEmpty()) Text(message)

        if (log.isEmpty() && cues.isEmpty()) {
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
            }

            if (!running) {
                Button(onClick = { if (mode == "video") buatUntukEdit() else buatFile() }) {
                    Text(if (mode == "video") "1. Buat subtitle untuk diedit" else "Buat subtitle")
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

        // ---- Editor (mode video, setelah subtitle jadi) ----
        if (mode == "video" && cues.isNotEmpty()) {
            HorizontalDivider()
            Text("2. Edit teks (${cues.size} potongan)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Waktu tidak bisa diubah di sini. Kosongkan teks atau tekan ✕ untuk membuang potongan.",
                style = MaterialTheme.typography.bodySmall
            )

            cues.forEachIndexed { i, cue ->
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(cue.time, style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(
                            value = cue.text,
                            onValueChange = { cues[i] = cue.copy(text = it) },
                            enabled = !running,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TextButton(enabled = !running, onClick = { cues.removeAt(i) }) { Text("✕") }
                }
            }

            HorizontalDivider()
            Text("3. Tempel ke video", style = MaterialTheme.typography.titleSmall)

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
                "Posisi",
                listOf("bawah" to "Bawah (aman dari tombol TikTok)", "tengah" to "Tengah"),
                posisi, !running
            ) { posisi = it }

            if (!running) {
                Button(onClick = { tempelKeVideo() }) { Text("Tempel ke video") }
            }
        }

        log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}