package com.cardcopyautomat.app

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cardcopyautomat.app.databinding.ItemImageBinding

data class CardImage(val name: String, val uri: Uri)

class ImageAdapter(private var images: List<CardImage> = emptyList()) :
    RecyclerView.Adapter<ImageAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemImageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val image = images[position]
        holder.binding.fileNameText.text = image.name
        
        val extension = image.name.substringAfterLast('.', "").lowercase()
        val isVideo = extension == "mlv"
        
        holder.binding.imageView.load(image.uri) {
            crossfade(true)
            placeholder(if (isVideo) android.R.drawable.ic_media_play else android.R.drawable.ic_menu_gallery)
            error(if (isVideo) android.R.drawable.ic_media_play else android.R.drawable.ic_menu_report_image)
        }
    }

    override fun getItemCount(): Int = images.size

    fun updateImages(newImages: List<CardImage>) {
        images = newImages
        notifyDataSetChanged()
    }
}
