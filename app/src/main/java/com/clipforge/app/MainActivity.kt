package com.clipforge.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build

data class PickedFile(val uri: Uri, val name: String) {
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
}

fun queryName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) return c.getString(i)
    }
    return uri.lastPathSegment ?: "file"
}

// jpg dan jpeg dianggap sama
private fun samaFormat(a: String, b: String): Boolean {
    fun n(x: String) = if (x == "jpeg") "jpg" else x
    return n(a) == n(b)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen()
                }
            }
        }
    }
}

@Composable
fun ConverterScreen(onBusy: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf(emptyList<PickedFile>()) }
    var categories by remember { mutableStateOf(emptyList<MediaType>()) }
    var category by remember { mutableStateOf<MediaType?>(null) }
    var format by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) { onBusy(running) }
    var convertJob by remember { mutableStateOf<Job?>(null) }
    val log = remember { mutableStateListOf<String>() }
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun resetPilihan() {
        categories = emptyList()
        category = null
        format = null
        expanded = false
    }

    fun pilihKategori(cat: MediaType, daftar: List<PickedFile>) {
        category = cat
        format = Formats.targetFormats(cat, daftar.map { it.ext }.toSet()).firstOrNull()
    }

    // Satu pintu untuk semua perubahan daftar file (pilih, hapus satu, hapus semua)
    fun terapkan(picked: List<PickedFile>) {
        files = picked
        log.clear()
        if (picked.isEmpty()) {
            resetPilihan()
            message = ""
            return
        }
        val types = picked.map { Formats.typeOf(it.ext) }.toSet()
        when {
            null in types -> {
                resetPilihan()
                message = "⚠️ Ada file dengan format yang tidak didukung."
            }
            types.size > 1 -> {
                resetPilihan()
                message = "⚠️ Pilih satu jenis saja per proses (semua video, semua audio, atau semua gambar)."
            }
            else -> {
                val jenis = types.first()!!
                categories = Formats.categoriesFor(jenis)
                pilihKategori(categories.first(), picked)
                message = "📁 ${picked.size} file terdeteksi: ${jenis.label}"
            }
        }
    }

    fun mulaiConvert() {
        val tujuan = format ?: return
        if (Prefs.serverUrl(context).isBlank() || Prefs.apiKey(context).isBlank()) {
            message = "⚠️ Isi URL server dan API key dulu di bagian Server."
            return
        }
        val daftar = files
        log.clear()
        daftar.forEach { log.add("⏸️ ${it.name}: antre") }

        val adaKerja = daftar.any { !samaFormat(it.ext, tujuan) }
        if (adaKerja && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        convertJob = scope.launch {
            running = true
            if (adaKerja) runCatching { KeepAliveService.start(context) }
            val berhasil = mutableListOf<ConvertResult>()
            var gagal = 0
            try {
                daftar.forEachIndexed { i, f ->
                    if (samaFormat(f.ext, tujuan)) {
                        log[i] = "⏭️ ${f.name}: sudah berformat .$tujuan, dilewati"
                        return@forEachIndexed
                    }
                    try {
                        val hasil = convertFile(context, f, tujuan) { log[i] = it }
                        berhasil.add(hasil)
                        log[i] = "✅ ${f.name} → ${hasil.label}"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        gagal++
                        log[i] = "❌ ${f.name}: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
                if (adaKerja) Notifier.selesai(context, berhasil, gagal)
            } catch (e: CancellationException) {
                for (i in log.indices) {
                    val t = log[i]
                    log[i] = when {
                        t.startsWith("✅") || t.startsWith("❌") || t.startsWith("⏭️") -> t
                        t.startsWith("⏸️") -> "⏹️ ${daftar[i].name}: dilewati"
                        else -> "🛑 ${daftar[i].name}: dibatalkan"
                    }
                }
                throw e
            } finally {
                running = false
                convertJob = null
                if (adaKerja) KeepAliveService.stop(context)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            terapkan(uris.map { PickedFile(it, queryName(context, it)) })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ClipForge", style = MaterialTheme.typography.headlineMedium)
        Text("Converter", style = MaterialTheme.typography.titleMedium)

        ServerSettings()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running,
                onClick = { picker.launch(arrayOf("video/*", "audio/*", "image/*")) }
            ) { Text("Pilih file") }

            if (files.isNotEmpty()) {
                OutlinedButton(
                    enabled = !running,
                    onClick = { terapkan(emptyList()) }
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
                    TextButton(onClick = { terapkan(files - f) }) { Text("✕") }
                }
            }
        }

        if (categories.isNotEmpty()) {
            Text("Convert ke jenis", style = MaterialTheme.typography.labelLarge)
            categories.forEach { cat ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = category == cat,
                        enabled = !running,
                        onClick = { pilihKategori(cat, files) }
                    )
                    Text(cat.label)
                }
            }

            val cat = category
            if (cat != null) {
                val formats = Formats.targetFormats(cat, files.map { it.ext }.toSet())
                Text("Format tujuan", style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(enabled = !running, onClick = { expanded = true }) {
                        Text(format ?: "Pilih format")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        formats.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f) },
                                onClick = { format = f; expanded = false }
                            )
                        }
                    }
                }
            }

            if (!running) {
                Button(
                    enabled = format != null,
                    onClick = { mulaiConvert() }
                ) { Text("Convert") }
            } else {
                Button(
                    onClick = { convertJob?.cancel() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Batalkan proses") }
            }
        }

        log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun AppScreen() {
    var tab by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, enabled = !busy || tab == 0,
                onClick = { tab = 0 }, text = { Text("Converter") })
            Tab(selected = tab == 1, enabled = !busy || tab == 1,
                onClick = { tab = 1 }, text = { Text("Subtitle") })
        }
        Box(Modifier.weight(1f)) {
            if (tab == 0) ConverterScreen { busy = it } else SubtitleScreen { busy = it }
        }
    }
}