package com.sorabh.storageproject.adapter

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sorabh.storageproject.model.InternalStoragePhoto
import com.sorabh.storageproject.databinding.ImageLayoutBinding
import com.sorabh.storageproject.model.SharedStoragePhoto
import javax.inject.Inject

class SharedStorageAdapter @Inject constructor() : RecyclerView.Adapter<ImageVieHolder>() {
    private val photos: MutableList<SharedStoragePhoto> = mutableListOf()
    var onPhotoClick: ((Uri) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ImageVieHolder(
            ImageLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ImageVieHolder, position: Int) {
        holder.binding.imgStorage.setImageURI(Uri.parse(photos[position].contentUri.toString()))
        holder.binding.imgStorage.setOnClickListener {
            Log.d("TVS","Running1")
            onPhotoClick?.let { onPhoto -> onPhoto(photos[position].contentUri) }
        }
    }

    override fun getItemCount() = photos.size

    fun updatePhotos(newPhotos: List<SharedStoragePhoto>) {
        Log.d("SorabhK",newPhotos.toString())
        photos.clear()
        photos.addAll(newPhotos)
        notifyDataSetChanged()
    }
}