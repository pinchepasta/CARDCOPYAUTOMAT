package com.cardcopyautomat.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.concurrent.Executors

/**
 * Does the actual work once a card is detected:
 *  1. Scan the card reader's SAF tree for Canon RAW files (any subfolder).
 *  2. Copy each one into the app's internal storage folder.
 *  3. Copy each one to the Photo Roll in a "CC-Automat" folder.
 *  4. If Google Drive is the configured target, upload each copy to Drive.
 *  5. Delete the originals from the card.
 *  6. Release the app's access permission to the card volume (as close to
 *     an "eject" as a non-rooted Android app can get) and play the beep +
 *     double-vibrate signal that it's safe to physically remove the card.
 *
 * Runs as a foreground service (dataSync type) since this is a
 * potentially-long-running background file operation triggered by a
 * broadcast, which Android 8+ requires a foreground service + notification
 * for.
 */
class CardCopyService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID_WORKING, buildWorkingNotification(getString(R.string.status_scanning)))

        if (intent?.action == ACTION_USB_ATTACHED || intent?.action == ACTION_RUN_NOW) {
            executor.execute { runCopyJob() }
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    // ---------------------------------------------------------------------

    private fun runCopyJob() {
        val cardUri = prefs.cardVolumeUri
        val destDir = File(getExternalFilesDir(null), "CanonRawCopies").apply { 
            deleteRecursively()
            mkdirs() 
        }

        if (cardUri == null) {
            // First-time setup not done yet — ask the user to open the app.
            notifyActionNeeded()
            stopSelf()
            return
        }

        val cardRoot = DocumentFile.fromTreeUri(this, cardUri)
        if (cardRoot == null || !cardRoot.isDirectory || !cardRoot.canRead()) {
            finishWithError(getString(R.string.status_error, "can't access the card volume — reselect it in Settings"))
            return
        }

        val rawFiles = RawFileScanner.findImageFiles(cardRoot)
        if (rawFiles.isEmpty()) {
            finish(getString(R.string.status_no_raw_files), signal = false)
            return
        }

        val copiedLocalFiles = mutableListOf<File>()
        val successfullyHandled = mutableListOf<DocumentFile>()

        // Step 1: copy every image file into internal storage.
        for ((index, doc) in rawFiles.withIndex()) {
            val progress = ((index.toFloat() / rawFiles.size) * 100).toInt()
            updateProgressNotification(
                getString(R.string.status_copying, doc.name ?: "file", index + 1, rawFiles.size),
                progress
            )
            try {
                val extension = doc.name?.substringAfterLast('.', "jpg") ?: "jpg"
                val localFile = File(destDir, doc.name ?: "img_${System.currentTimeMillis()}.$extension")
                contentResolver.openInputStream(doc.uri)?.use { input ->
                    localFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw RuntimeException("could not open ${doc.name}")
                copiedLocalFiles.add(localFile)
                successfullyHandled.add(doc)
                
                // Also copy to Photo Roll (MediaStore)
                saveToPhotoRoll(localFile)
            } catch (e: Exception) {
                // Skip this file, keep going with the rest of the card.
                continue
            }
        }

        if (copiedLocalFiles.isEmpty()) {
            finishWithError(getString(R.string.status_error, "none of the files could be copied"))
            return
        }

        // Step 2: upload to Google Drive, if that's the configured target.
        if (prefs.uploadTarget == Prefs.UploadTarget.GOOGLE_DRIVE) {
            val account = prefs.googleAccountName
            if (account == null) {
                finishWithError(getString(R.string.status_error, "no Google account signed in — check Settings"))
                return
            }
            try {
                val uploader = GoogleDriveUploader(this)
                val folderId = prefs.driveFolderId ?: uploader.ensureDestinationFolder(account).also {
                    prefs.driveFolderId = it
                }
                for ((index, file) in copiedLocalFiles.withIndex()) {
                    val progress = ((index.toFloat() / copiedLocalFiles.size) * 100).toInt()
                    updateProgressNotification(
                        getString(R.string.status_uploading, file.name, index + 1, copiedLocalFiles.size),
                        progress
                    )
                    uploader.uploadFile(account, file, folderId)
                }
            } catch (e: Exception) {
                // Local copies already exist safely — surface the upload error but
                // still proceed to delete-from-card only for files we're fully done with.
                finishWithError(getString(R.string.status_error, "Drive upload failed: ${e.message}"))
                return
            }
        }

        // Step 3: delete originals from the card now that copy (+ upload) succeeded.
        updateProgressNotification(getString(R.string.status_deleting))
        for (doc in successfullyHandled) {
            try {
                doc.delete()
            } catch (_: Exception) {
                // Leave it on the card rather than fail the whole job.
            }
        }

        // Step 4: release our access to the card volume (closest thing to
        // "eject" available without root) and signal the user.
        try {
            contentResolver.releasePersistableUriPermission(
                cardUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Some providers don't support release; harmless either way.
        }

        finish(
            getString(R.string.status_done, getString(R.string.status_done_prefix), copiedLocalFiles.size),
            signal = true
        )
    }

    private fun saveToPhotoRoll(file: File) {
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            val extension = file.extension.lowercase()
            val mimeType = when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "cr2" -> "image/x-canon-cr2"
                "cr3" -> "image/x-canon-cr3"
                "dng", "cdng" -> "image/x-adobe-dng"
                "mlv" -> "application/x-mlv"
                else -> "image/*"
            }
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CC-Automat")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            } catch (e: Exception) {
                // If failed, delete the partial entry
                resolver.delete(uri, null, null)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Notifications / finishing states

    private fun finish(message: String, signal: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_WORKING, buildDoneNotification(message))
        if (signal) {
            FeedbackHelper.playEjectSignal(this)
            sendProgressBroadcast(message, progress = 100, finished = true)
        } else {
            sendProgressBroadcast(message, finished = true)
        }
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun finishWithError(message: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_WORKING, buildDoneNotification(message))
        sendProgressBroadcast(message, finished = true)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun notifyActionNeeded() {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.notif_action_needed_title))
            .setContentText(getString(R.string.notif_action_needed_body))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID_ACTION_NEEDED, notif)
        sendProgressBroadcast("Action needed: setup required")
    }

    private fun updateProgressNotification(text: String, progress: Int = -1) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID_WORKING, buildWorkingNotification(text))
        sendProgressBroadcast(text, progress)
    }

    private fun sendProgressBroadcast(status: String, progress: Int = -1, finished: Boolean = false) {
        val intent = Intent(ACTION_PROGRESS_UPDATE).apply {
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_FINISHED, finished)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun buildWorkingNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.notif_title_working))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun buildDoneNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.notif_title_done))
            .setContentText(text)
            .setAutoCancel(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_USB_ATTACHED = "com.cardcopyautomat.app.action.USB_ATTACHED"
        const val ACTION_RUN_NOW = "com.cardcopyautomat.app.action.RUN_NOW"
        const val ACTION_PROGRESS_UPDATE = "com.cardcopyautomat.app.action.PROGRESS_UPDATE"
        const val EXTRA_STATUS = "status"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_FINISHED = "finished"

        private const val CHANNEL_ID = "card_copy_channel"
        private const val NOTIF_ID_WORKING = 1001
        private const val NOTIF_ID_ACTION_NEEDED = 1002
    }
}
