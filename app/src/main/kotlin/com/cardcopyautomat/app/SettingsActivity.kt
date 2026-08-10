package com.cardcopyautomat.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.cardcopyautomat.app.databinding.ActivitySettingsBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs
    private lateinit var googleClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account?.email != null) {
                prefs.googleAccountName = account.email
                prefs.driveFolderId = null
                Toast.makeText(this, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()
                verifyDriveAccess(account.email!!)
            }
        } catch (e: ApiException) {
            android.util.Log.e("CardCopy", "Sign-in failed with Status Code: ${e.statusCode}, Message: ${e.message}")
            handleSignInError(e)
        }
        refreshAll()
    }

    private val cardVolumeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri = result.data?.data ?: return@registerForActivityResult
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.cardVolumeUri = uri
            refreshAll()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GoogleDriveUploader.SCOPE))
            .build()
        googleClient = GoogleSignIn.getClient(this, gso)

        binding.selectCardVolumeButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            cardVolumeLauncher.launch(intent)
        }

        binding.uploadTargetGroup.setOnCheckedChangeListener { _, checkedId ->
            val target = when (checkedId) {
                binding.radioDrive.id -> Prefs.UploadTarget.GOOGLE_DRIVE
                else -> Prefs.UploadTarget.NONE
            }
            prefs.uploadTarget = target
            refreshTargetSpecificUi()
        }

        binding.googleSignInButton.setOnClickListener {
            googleSignInLauncher.launch(googleClient.signInIntent)
        }

        binding.googleSignOutButton.setOnClickListener {
            googleClient.signOut().addOnCompleteListener {
                prefs.googleAccountName = null
                prefs.driveFolderId = null
                Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
                refreshAll()
            }
        }

        // Check if already signed in and has permissions
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            if (GoogleSignIn.hasPermissions(account, Scope(GoogleDriveUploader.SCOPE))) {
                prefs.googleAccountName = account.email
            } else {
                // Signed in but missing Drive scope
                prefs.googleAccountName = null
            }
        }

        refreshAll()
    }

    private fun handleSignInError(e: ApiException) {
        val msg = when (e.statusCode) {
            7 -> "Network Error: Check your internet connection."
            10 -> "Developer Error (10): This usually means your SHA-1 fingerprint or Package Name is NOT registered in the Google Cloud Console."
            12500 -> "Sign-in failed (12500): Check if Google Play Services is up to date and if the app is correctly configured in the console."
            12501 -> "Sign-in cancelled by user."
            else -> "Sign-in failed (Code: ${e.statusCode}): ${e.message ?: "Unknown error"}"
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun verifyDriveAccess(email: String) {
        // Run on background thread because GoogleAuthUtil.getToken blocks
        kotlin.concurrent.thread {
            try {
                val uploader = GoogleDriveUploader(this)
                // This triggers the consent dialog if not already granted
                uploader.ensureDestinationFolder(email)
                runOnUiThread {
                    Toast.makeText(this, "Drive access verified!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Drive permission needed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshAll() {
        val cardUri = prefs.cardVolumeUri
        binding.cardVolumeStatus.text = if (cardUri != null) {
            val name = DocumentFile.fromTreeUri(this, cardUri)?.name ?: cardUri.toString()
            "Selected: $name"
        } else {
            "Not selected yet — plug in the reader with a card inserted, then tap above and pick it."
        }

        val destDir = File(getExternalFilesDir(null), "CanonRawCopies")
        binding.destFolderStatus.text = getString(R.string.dest_folder_info, destDir.absolutePath)

        when (prefs.uploadTarget) {
            Prefs.UploadTarget.NONE -> binding.radioNone.isChecked = true
            Prefs.UploadTarget.GOOGLE_DRIVE -> binding.radioDrive.isChecked = true
            else -> binding.radioNone.isChecked = true
        }

        refreshTargetSpecificUi()
    }

    private fun refreshTargetSpecificUi() {
        val target = prefs.uploadTarget
        val isDrive = target == Prefs.UploadTarget.GOOGLE_DRIVE
        val account = prefs.googleAccountName

        binding.googleSignInButton.visibility = if (isDrive && account == null) View.VISIBLE else View.GONE
        binding.googleSignOutButton.visibility = if (isDrive && account != null) View.VISIBLE else View.GONE
        binding.googleAccountStatus.visibility = if (isDrive) View.VISIBLE else View.GONE

        if (isDrive) {
            binding.googleAccountStatus.text = if (account != null) {
                getString(R.string.signed_in_as, account)
            } else {
                getString(R.string.not_signed_in)
            }
        }
    }
}
