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
    var message by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val log = remember { mutableStateListOf<String>() }

    LaunchedEffect(running) { onBusy(running) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val picked = uris.map { PickedFile(it, queryName(context, it)) }
            val salah = picked.filter {
                Formats.typeOf(it.ext) !in listOf(MediaType.VIDEO, MediaType.AUDIO)
            }
            log.clear()
            if (salah.isNotEmpty()) {
                files = emptyList()
                message = "⚠️ Subtitle hanya untuk video atau audio. Bukan: " +
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
        val bahasa = language
        val gaya = gaya
        val format = fmt
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
                        val hasil = convertFile(
                            context, f, format, "/subtitle",
                            mapOf("language" to bahasa, "maxchars" to gayaTeks)
                        ) { log[i] = it }
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running,
                onClick = { picker.launch(arrayOf("video/*", "audio/*")) }
            ) { Text("Pilih video / audio") }

            if (files.isNotEmpty()) {
                OutlinedButton(
                    enabled = !running,
                    onClick = { files = emptyList(); log.clear(); message = "" }
                ) { Text("Batal / hapus semua") }
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
                "Format subtitle",
                listOf("srt" to "SRT", "vtt" to "VTT", "txt" to "Teks biasa (TXT)"),
                fmt, !running
            ) { fmt = it }

            PilihanDropdown(
                "Panjang teks",
                listOf(
                    "0" to "Normal (kalimat panjang)",
                    "28" to "Pendek (cocok TikTok)",
                    "14" to "Sangat pendek (1-3 kata)"
                ),
                gaya, !running
            ) { gaya = it }

            if (!running) {
                Button(onClick = { mulai() }) { Text("Buat subtitle") }
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