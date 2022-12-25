package com.sorabh.storageproject.activity

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sorabh.storageproject.adapter.PrivateStorageAdapter
import com.sorabh.storageproject.databinding.ActivityMainBinding
import com.sorabh.storageproject.model.InternalStoragePhoto
import com.sorabh.storageproject.utils.sdk29AndUp
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

    private var readPermissionRequest = false
    private var writePermissionRequest = false
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRecyclerView()
        capturePhoto()
        updateOrRequestPermission()
    }

    private fun capturePhoto() {
        val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
            it?.let { bitmap ->

                val isSavedSuccessfully = when {
                    binding.isPrivateSwitch.isChecked ->
                        savePhotoToInternalStorage(UUID.randomUUID().toString(), bitmap)
                    writePermissionRequest ->
                        savePhotoToExternalStorage(UUID.randomUUID().toString(), bitmap)
                    else -> false
                }
                if (isSavedSuccessfully) {
                    lifecycleScope.launch {
                        photoAdapter.updatePhotos(loadPhotosFromInternalStorage())
                        Toast.makeText(
                            this@MainActivity,
                            "Photo saved successfully!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "failed to save photo!",
                        Toast.LENGTH_LONG
                    ).show()
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

    /** for internal storage accessing */

    //deleting photo from internal
    private fun onPhotoClicked(photoName: String) {
        if (deletePhoto(photoName)) {
            lifecycleScope.launch {
                photoAdapter.updatePhotos(loadPhotosFromInternalStorage())
            }
            Toast.makeText(this, "file deleted successfully!", Toast.LENGTH_LONG).show()
        } else
            Toast.makeText(this, "failed to delete file!", Toast.LENGTH_LONG).show()
    }

    //saving photos from internal storage
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

    //accessing files from internal storage
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

    //delete photo from internal storage
    private fun deletePhoto(fileName: String): Boolean {
        return try {
            deleteFile(fileName)
            true
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            false
        }
    }

    /** accessing external storage */

    private fun updateOrRequestPermission() {
        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permission ->
                readPermissionRequest =
                    permission[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: readPermissionRequest
                writePermissionRequest =
                    permission[Manifest.permission.READ_EXTERNAL_STORAGE] ?: writePermissionRequest
            }

        val hasReadPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val hasWritePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        readPermissionRequest = hasReadPermission
        writePermissionRequest = hasWritePermission || minSdk29

        val permissionRequests = mutableListOf<String>()

        if (!writePermissionRequest)
            permissionRequests.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (!readPermissionRequest)
            permissionRequests.add(Manifest.permission.READ_EXTERNAL_STORAGE)

        if (permissionRequests.isNotEmpty())
            permissionLauncher.launch(permissionRequests.toTypedArray())
    }

    private fun savePhotoToExternalStorage(displayName: String, bitmap: Bitmap): Boolean {
        val imageCollection = sdk29AndUp {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpeg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
        }

        return try {
            contentResolver.insert(imageCollection, contentValues)?.also { uri ->
                contentResolver.openOutputStream(uri).use { outputStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                        throw IOException("Couldn't save bitmap!")
                    }
                }
            } ?: throw IOException("Couldn't create mediaStore entry!")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
}