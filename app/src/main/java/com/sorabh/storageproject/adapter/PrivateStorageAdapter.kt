package com.sorabh.storageproject.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sorabh.storageproject.model.InternalStoragePhoto
import com.sorabh.storageproject.databinding.ImageLayoutBinding
import javax.inject.Inject

class PrivateStorageAdapter @Inject constructor() : RecyclerView.Adapter<ImageVieHolder>() {
    private val photos: MutableList<InternalStoragePhoto> = mutableListOf()
    var onPhotoClick: ((String) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ImageVieHolder(
            ImageLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ImageVieHolder, position: Int) {
        holder.binding.imgStorage.setImageBitmap(photos[position].bitmap)
        holder.binding.imgStorage.setOnLongClickListener {
            onPhotoClick?.let { onPhoto -> onPhoto(photos[position].fileName) }
            true
        }
    }

    override fun getItemCount() = photos.size

    fun updatePhotos(newPhotos: List<InternalStoragePhoto>) {
        photos.clear()
        photos.addAll(newPhotos)
        notifyDataSetChanged()
    }
}