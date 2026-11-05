package com.tchatz.klassio.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tchatz.klassio.KlassioApp
import com.tchatz.klassio.MainActivity
import com.tchatz.klassio.R
import com.tchatz.klassio.data.repo.KlassioRepository
import java.util.concurrent.TimeUnit

/**
 * Notification des nouveaux documents.
 *
 * L'application n'embarque pas de clé privée Firebase : le contrôle périodique
 * interroge le serveur (WorkManager) et n'affiche une notification que pour les
 * documents réellement publiés depuis la dernière synchronisation.
 * Fonctionne donc sur toute connexion, y compris instable, sans service payant.
 */
class NewDocumentWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KlassioApp ?: return Result.success()
        val repo: KlassioRepository = app.container.repository
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!repo.store.notificationsEnabled()) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        createChannel(applicationContext)

        val sessions = repo.store.sessions()
        for (session in sessions) {
            val since = repo.lastSeen(session)
            if (since == null) {
                // Première exécution : on mémorise l'instant présent, sans notifier l'existant.
                repo.markUpdatesSeen(session)
                continue
            }
            val updates = runCatching { repo.updates(session) }.getOrNull() ?: continue
            updates.take(5).forEachIndexed { index, document ->
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_CLASSROOM_ID, session.classroomId)
                    putExtra(MainActivity.EXTRA_DOCUMENT_ID, document.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pending = PendingIntent.getActivity(
                    applicationContext,
                    document.id.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(document.subjectName.ifBlank { session.className })
                    .setContentText(document.title)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("${document.title} — ${document.teacherDisplayName}"))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                runCatching { manager.notify(NOTIFICATION_BASE + index, notification) }
            }
            if (updates.isNotEmpty()) repo.markUpdatesSeen(session)
        }
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "klassio_documents"
        private const val NOTIFICATION_BASE = 4200
        private const val UNIQUE_WORK = "klassio-new-documents"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewDocumentWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_documents_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_documents_desc)
                setShowBadge(true)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}

class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra("id", 0))
    }
}
