package com.clipforge.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ServerSettings() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(Prefs.serverUrl(context)) }
    var key by remember { mutableStateOf(Prefs.apiKey(context)) }
    var showKey by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Server", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = url,
                onValueChange = { url = it; Prefs.save(context, url, key) },
                label = { Text("URL server (dari Colab)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = key,
                onValueChange = { key = it; Prefs.save(context, url, key) },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation =
                    if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val teks = clipboard.getText()?.text.orEmpty()
                    // Kalau yang disalin satu baris penuh dari Colab, ambil URL-nya saja
                    val hasil = Regex("https?://[^\\s\"']+").find(teks)?.value ?: teks
                    if (hasil.isNotBlank()) {
                        url = ServerClient.normalizeUrl(hasil)
                        Prefs.save(context, url, key)
                        status = ""
                    }
                }) { Text("Tempel URL") }

                TextButton(onClick = { showKey = !showKey }) {
                    Text(if (showKey) "Sembunyikan key" else "Tampilkan key")
                }
            }

            Button(
                enabled = !testing,
                onClick = {
                    scope.launch {
                        testing = true
                        status = "Mengecek…"
                        status = withContext(Dispatchers.IO) {
                            ServerClient.testConnection(url, key)
                        }
                        testing = false
                    }
                }
            ) { Text("Tes koneksi") }

            if (status.isNotEmpty()) Text(status)
        }
    }
}