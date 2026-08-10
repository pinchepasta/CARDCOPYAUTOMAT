package com.cardcopyautomat.app

import android.content.Context
import android.net.Uri

/**
 * Thin wrapper around SharedPreferences holding every user-configured setting:
 * which SAF tree is the card reader volume, which SAF tree is the internal
 * destination folder, which cloud target is selected, and the signed-in
 * Google account (if any).
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("cardcopyautomat_prefs", Context.MODE_PRIVATE)

    enum class UploadTarget { NONE, GOOGLE_DRIVE }

    var cardVolumeUri: Uri?
        get() = sp.getString(KEY_CARD_URI, null)?.let { Uri.parse(it) }
        set(value) = sp.edit().putString(KEY_CARD_URI, value?.toString()).apply()

    var destFolderUri: Uri?
        get() = sp.getString(KEY_DEST_URI, null)?.let { Uri.parse(it) }
        set(value) = sp.edit().putString(KEY_DEST_URI, value?.toString()).apply()

    var uploadTarget: UploadTarget
        get() = UploadTarget.valueOf(sp.getString(KEY_TARGET, UploadTarget.NONE.name)!!)
        set(value) = sp.edit().putString(KEY_TARGET, value.name).apply()

    var googleAccountName: String?
        get() = sp.getString(KEY_GOOGLE_ACCOUNT, null)
        set(value) = sp.edit().putString(KEY_GOOGLE_ACCOUNT, value).apply()

    var driveFolderId: String?
        get() = sp.getString(KEY_DRIVE_FOLDER_ID, null)
        set(value) = sp.edit().putString(KEY_DRIVE_FOLDER_ID, value).apply()

    companion object {
        private const val KEY_CARD_URI = "card_volume_uri"
        private const val KEY_DEST_URI = "dest_folder_uri"
        private const val KEY_TARGET = "upload_target"
        private const val KEY_GOOGLE_ACCOUNT = "google_account_name"
        private const val KEY_DRIVE_FOLDER_ID = "drive_folder_id"
    }
}
