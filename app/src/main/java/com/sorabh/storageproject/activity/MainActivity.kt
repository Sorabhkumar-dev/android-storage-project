package com.sorabh.storageproject.activity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sorabh.storageproject.adapter.PrivateStorageAdapter
import com.sorabh.storageproject.databinding.ActivityMainBinding
import com.sorabh.storageproject.model.InternalStoragePhoto
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var photoAdapter: PrivateStorageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRecyclerView()
        capturePhoto()
    }

    private fun capturePhoto() {
        val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
            it?.let { bitmap ->
                if (binding.isPrivateSwitch.isChecked) {
                    val isSavedSuccessfully =
                        savePhotoToInternalStorage(UUID.randomUUID().toString(), bitmap)
                    if (isSavedSuccessfully) {
                        lifecycleScope.launch {
                            photoAdapter.updatePhotos(loadPhotosFromInternalStorage())
                            Toast.makeText(
                                this@MainActivity,
                                "Photo saved successfully!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }else{
                        Toast.makeText(
                            this@MainActivity,
                            "failed to save photo!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        binding.imgCamera.setOnClickListener { takePhoto.launch() }
    }

    private fun setupRecyclerView() {
        photoAdapter.onPhotoClick = ::onPhotoClicked
        binding.rvPrivateStorage.adapter = photoAdapter
        lifecycleScope.launch {
            photoAdapter.updatePhotos(loadPhotosFromInternalStorage())
        }
    }

    private fun onPhotoClicked(photoName: String) {
        if (deletePhoto(photoName)) {
            lifecycleScope.launch {
                photoAdapter.updatePhotos(loadPhotosFromInternalStorage())
            }
            Toast.makeText(this, "file deleted successfully!", Toast.LENGTH_LONG).show()
        }else
            Toast.makeText(this, "failed to delete file!", Toast.LENGTH_LONG).show()
    }

    private fun savePhotoToInternalStorage(fileName: String, bitmap: Bitmap): Boolean {
        return try {
            openFileOutput("$fileName.jpg", MODE_PRIVATE).use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream))
                    throw IOException("Couldn't saved image")
            }
            lifecycleScope.launch {
                photoAdapter.updatePhotos(loadPhotosFromInternalStorage())
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun loadPhotosFromInternalStorage(): List<InternalStoragePhoto> {
        return withContext(Dispatchers.IO) {
            val files = filesDir.listFiles()
            files?.filter { it.isFile && it.canRead() && it.name.endsWith(".jpg") }?.map {
                val bytes = it.readBytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                InternalStoragePhoto(it.name.toString(), bitmap)
            } ?: emptyList()
        }
    }

    private fun deletePhoto(fileName: String): Boolean {
        return try {
            deleteFile(fileName)
            true
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            false
        }
    }
}