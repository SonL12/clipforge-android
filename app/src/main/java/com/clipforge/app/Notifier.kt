package com.clipforge.app

import android.Manifest
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object Notifier {
    private const val CHANNEL = "clipforge_hasil"
    private const val NOTIF_ID = 2

    fun selesai(context: Context, berhasil: List<ConvertResult>, gagal: Int) {
        if (berhasil.isEmpty() && gagal == 0) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Hasil convert", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val judul: String
        val teks: String
        when {
            berhasil.isEmpty() -> {
                judul = "Convert gagal"
                teks = "$gagal file gagal diproses"
            }
            berhasil.size == 1 && gagal == 0 -> {
                judul = "Convert selesai"
                teks = "${berhasil[0].name} tersimpan di ${berhasil[0].label.substringBeforeLast('/')}"
            }
            else -> {
                judul = "Convert selesai"
                teks = "${berhasil.size} file berhasil" + if (gagal > 0) ", $gagal gagal" else ""
            }
        }

        // Satu file berhasil: buka file-nya. Selain itu: buka folder Download.
        val satu = berhasil.singleOrNull()
        val intent = if (satu?.uri != null && gagal == 0) {
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(satu.uri, satu.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL)
        else Notification.Builder(context)
        val notif = builder
            .setContentTitle(judul)
            .setContentText(teks)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID, notif)
    }
}