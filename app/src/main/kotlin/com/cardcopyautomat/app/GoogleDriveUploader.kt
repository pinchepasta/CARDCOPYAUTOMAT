package com.cardcopyautomat.app

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Minimal Google Drive v3 REST client for uploading a local file into a
 * (optionally pre-created) "CardCopyAutomat" folder in the signed-in user's
 * Drive. Uses the drive.file scope, which only grants access to files this
 * app itself creates — not the user's whole Drive.
 */
class GoogleDriveUploader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .build()

    private fun getAccessToken(accountName: String): String {
        val account = Account(accountName, "com.google")
        // "oauth2:<scope>" token type, as required by GoogleAuthUtil.
        return GoogleAuthUtil.getToken(context, account, "oauth2:$SCOPE")
    }

    /** Finds (or creates) the destination folder in Drive, returns its file ID. */
    fun ensureDestinationFolder(accountName: String, folderName: String = "CardCopyAutomat"): String {
        val token = getAccessToken(accountName)

        val query = "mimeType='application/vnd.google-apps.folder' and name='$folderName' " +
            "and trashed=false"
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.googleapis.com/drive/v3/files" +
            "?q=$encodedQuery&fields=files(id,name)"

        val searchReq = Request.Builder()
            .url(searchUrl)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(searchReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).getString("id")
                }
            }
        }

        // Not found — create it.
        val metadata = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
        }
        val createReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .header("Authorization", "Bearer $token")
            .post(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        client.newCall(createReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("Could not create Drive folder: HTTP ${resp.code}")
            }
            val json = JSONObject(resp.body?.string().orEmpty())
            return json.getString("id")
        }
    }

    /** Uploads [localFile] into Drive folder [parentFolderId]. */
    fun uploadFile(accountName: String, localFile: File, parentFolderId: String) {
        var token = getAccessToken(accountName)

        fun performUpload(accessToken: String): okhttp3.Response {
            val metadata = JSONObject().apply {
                put("name", localFile.name)
                put("parents", org.json.JSONArray().put(parentFolderId))
            }

            val mimeType = when (localFile.extension.lowercase()) {
                "cr3" -> "image/x-canon-cr3"
                "cr2" -> "image/x-canon-cr2"
                "dng", "cdng" -> "image/x-adobe-dng"
                "mlv" -> "application/x-mlv"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                else -> "application/octet-stream"
            }

            val requestBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(
                    metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
                )
                .addPart(
                    localFile.asRequestBody(mimeType.toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            return client.newCall(request).execute()
        }

        var response = performUpload(token)
        if (response.code == 401) {
            response.close()
            try {
                GoogleAuthUtil.clearToken(context, token)
            } catch (_: Exception) {}
            token = getAccessToken(accountName)
            response = performUpload(token)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("Drive upload failed for ${localFile.name}: HTTP ${resp.code} ${resp.body?.string()}")
            }
        }
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
