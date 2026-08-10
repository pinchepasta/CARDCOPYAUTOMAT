package com.cardcopyautomat.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.cardcopyautomat.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: ImageAdapter

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(CardCopyService.EXTRA_STATUS)
            val progress = intent?.getIntExtra(CardCopyService.EXTRA_PROGRESS, -1) ?: -1
            val finished = intent?.getBooleanExtra(CardCopyService.EXTRA_FINISHED, false) ?: false

            if (status != null) {
                binding.statusText.text = status
            }

            if (progress >= 0) {
                binding.progressBar.isIndeterminate = false
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBar.progress = progress
            } else if (!finished) {
                binding.progressBar.isIndeterminate = true
                binding.progressBar.visibility = View.VISIBLE
            } else {
                binding.progressBar.visibility = View.GONE
            }

            if (finished && status?.contains(getString(R.string.status_done_prefix), ignoreCase = true) == true) {
                flashScreenOrange()
                // Reset UI after a short delay so the user can see the "Done" message
                binding.root.postDelayed({
                    checkUsbStatus()
                    loadThumbnails()
                }, 3000)
            }
        }
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadThumbnails()
            checkUsbStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        setupRecyclerView()
        requestPermissionsIfNeeded()
        startScanlineAnimation()

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.infoButton.setOnClickListener {
            startActivity(Intent(this, InfoActivity::class.java))
        }

        binding.runNowButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (prefs.cardVolumeUri == null) {
                binding.statusText.text = getString(R.string.setup_needed)
            } else {
                val intent = Intent(this, CardCopyService::class.java).apply {
                    action = CardCopyService.ACTION_RUN_NOW
                }
                ContextCompat.startForegroundService(this, intent)
                binding.statusText.text = getString(R.string.status_scanning)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ImageAdapter()
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3) // 3 columns for better gallery view
        binding.recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        
        val refreshFilter = IntentFilter("com.cardcopyautomat.app.ACTION_REFRESH_UI")
        val progressFilter = IntentFilter(CardCopyService.ACTION_PROGRESS_UPDATE)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, refreshFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(progressReceiver, progressFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(refreshReceiver, refreshFilter)
            registerReceiver(progressReceiver, progressFilter)
        }

        checkUsbStatus()
        loadThumbnails()

        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val svcIntent = Intent(this, CardCopyService::class.java).apply {
                action = CardCopyService.ACTION_USB_ATTACHED
            }
            ContextCompat.startForegroundService(this, svcIntent)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(refreshReceiver)
        unregisterReceiver(progressReceiver)
    }

    private fun flashScreenOrange() {
        val root = binding.root
        // IBM P70 orange flash
        val flashDrawable = android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#FF8F00"))
        root.post {
            flashDrawable.setBounds(0, 0, root.width, root.height)
            root.overlay.add(flashDrawable)
            
            val animator = android.animation.ValueAnimator.ofInt(0, 180, 0)
            animator.duration = 150
            animator.repeatCount = 1
            animator.addUpdateListener { animation ->
                flashDrawable.alpha = animation.animatedValue as Int
            }
            animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    root.overlay.remove(flashDrawable)
                }
            })
            animator.start()
        }
    }

    private fun startScanlineAnimation() {
        val scanline = binding.scanline
        scanline.visibility = View.VISIBLE
        
        // Continuous scanline movement
        val rootHeight = resources.displayMetrics.heightPixels.toFloat()
        val animator = android.animation.ObjectAnimator.ofFloat(scanline, "translationY", -100f, rootHeight)
        animator.duration = 4000
        animator.repeatCount = android.animation.ValueAnimator.INFINITE
        animator.interpolator = android.view.animation.LinearInterpolator()
        animator.start()

        // Occasional "glitch" effect
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val glitchRunnable = object : Runnable {
            override fun run() {
                val duration = (50..150).random().toLong()
                binding.root.translationX = ((-10)..10).random().toFloat()
                binding.root.alpha = 0.8f
                
                handler.postDelayed({
                    binding.root.translationX = 0f
                    binding.root.alpha = 1f
                }, duration)
                
                handler.postDelayed(this, (2000..6000).random().toLong())
            }
        }
        handler.postDelayed(glitchRunnable, 3000)
    }

    private fun checkUsbStatus() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val isConnected = usbManager.deviceList.isNotEmpty()
        
        if (!isConnected) {
            binding.statusText.text = "No card reader detected. Connect one to see images."
            binding.runNowButton.isEnabled = false
            adapter.updateImages(emptyList())
        } else {
            binding.runNowButton.isEnabled = true
            binding.statusText.text = if (prefs.cardVolumeUri == null) {
                getString(R.string.setup_needed)
            } else {
                getString(R.string.status_idle)
            }
        }
    }

    private fun loadThumbnails() {
        val cardUri = prefs.cardVolumeUri
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        
        if (cardUri == null || usbManager.deviceList.isEmpty()) {
            adapter.updateImages(emptyList())
            binding.recyclerView.visibility = View.GONE
            return
        }

        val cardRoot = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, cardUri)
        if (cardRoot == null || !cardRoot.isDirectory || !cardRoot.canRead()) {
            adapter.updateImages(emptyList())
            binding.recyclerView.visibility = View.GONE
            return
        }

        binding.recyclerView.visibility = View.VISIBLE
        binding.statusText.text = "Loading gallery..."
        // Run scanning on a background thread to avoid UI jank
        kotlin.concurrent.thread {
            try {
                val rawFiles = RawFileScanner.findImageFiles(cardRoot)
                val cardImages = rawFiles.map { CardImage(it.name ?: "unknown", it.uri) }
                
                runOnUiThread {
                    adapter.updateImages(cardImages)
                    checkUsbStatus() // Restore idle/setup status text
                }
            } catch (e: Exception) {
                runOnUiThread {
                    adapter.updateImages(emptyList())
                    checkUsbStatus()
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 1)
        }
    }
}
