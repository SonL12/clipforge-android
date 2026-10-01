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

data class PickedFile(val uri: Uri, val name: String) {
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
}

private fun queryName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) return c.getString(i)
    }
    return uri.lastPathSegment ?: "file"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConverterScreen()
                }
            }
        }
    }
}

@Composable
fun ConverterScreen() {
    val context = LocalContext.current

    var files by remember { mutableStateOf(emptyList<PickedFile>()) }
    var categories by remember { mutableStateOf(emptyList<MediaType>()) }
    var category by remember { mutableStateOf<MediaType?>(null) }
    var format by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

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
        FfmpegCheck()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                picker.launch(arrayOf("video/*", "audio/*", "image/*"))
            }) { Text("Pilih file") }

            if (files.isNotEmpty()) {
                OutlinedButton(onClick = { terapkan(emptyList()) }) {
                    Text("Batal / hapus semua")
                }
            }
        }

        if (message.isNotEmpty()) Text(message)

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

        if (categories.isNotEmpty()) {
            Text("Convert ke jenis", style = MaterialTheme.typography.labelLarge)
            categories.forEach { cat ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = category == cat,
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
                    OutlinedButton(onClick = { expanded = true }) {
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

            Button(onClick = { }, enabled = false) {
                Text("Convert (segera hadir)")
            }
        }
    }
}