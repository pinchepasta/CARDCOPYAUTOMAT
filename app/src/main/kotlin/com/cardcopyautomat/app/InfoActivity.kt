package com.cardcopyautomat.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cardcopyautomat.app.databinding.ActivityInfoBinding

class InfoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.websiteLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://feinlabs.de"))
            startActivity(intent)
        }

        binding.backButton.setOnClickListener {
            finish()
        }
    }
}
