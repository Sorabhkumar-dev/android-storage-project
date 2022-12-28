package com.sorabh.storageproject.activity

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore.*
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sorabh.storageproject.adapter.PrivateStorageAdapter
import com.sorabh.storageproject.adapter.SharedStorageAdapter
import com.sorabh.storageproject.databinding.ActivityMainBinding
import com.sorabh.storageproject.model.InternalStoragePhoto
import com.sorabh.storageproject.model.SharedStoragePhoto
import com.sorabh.storageproject.utils.sdk29AndAbove
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

    @Inject
    lateinit var sharedPhotoAdapter: SharedStorageAdapter

    private var readPermissionRequest = false
    private var writePermissionRequest = false
    private lateinit var contentObserver: ContentObserver
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        capturePhoto()
        updateOrRequestPermission()
        setupRecyclerView()
        initObserver()
        contentResolver.registerContentObserver(
            Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
        intentSenderLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                if (it.resultCode == RESULT_OK) {
                    Toast.makeText(this, "Photo deleted successfully!", Toast.LENGTH_LONG).show()
                }else {
                    Toast.makeText(this, "Photo not deleted!", Toast.LENGTH_LONG).show()
                }
            }
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
        sharedPhotoAdapter.onPhotoClick = ::deletePhotoFromExternalStorage
        binding.rvSharedStorage.adapter = sharedPhotoAdapter
        lifecycleScope.launch {
            if (readPermissionRequest)
                sharedPhotoAdapter.updatePhotos(loadPhotosFromExternalStorage())
        }
    }

    private fun onPhotoClicked(photoName: String) {
        if (deletePhoto(photoName))
            Toast.makeText(this, "file deleted successfully!", Toast.LENGTH_LONG).show()
        else
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

    /**                        external storage                                */

    private fun updateOrRequestPermission() {
        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permission ->
                readPermissionRequest =
                    permission[Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermissionRequest
                writePermissionRequest =
                    permission[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermissionRequest
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

        val permissionToRequest = mutableListOf<String>()

        if (!readPermissionRequest)
            permissionToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)

        if (!writePermissionRequest)
            permissionToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (permissionToRequest.isNotEmpty())
            permissionLauncher.launch(permissionToRequest.toTypedArray())
    }

    private fun savePhotoToExternalStorage(displayName: String, bitmap: Bitmap): Boolean {
        val imageCollection = sdk29AndAbove {
            Images.Media.getContentUri(VOLUME_EXTERNAL_PRIMARY)
        } ?: Images.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(Images.Media.MIME_TYPE, "image/jpeg")
            put(Images.Media.WIDTH, bitmap.width)
            put(Images.Media.HEIGHT, bitmap.height)
        }
        return try {
            contentResolver?.insert(imageCollection, contentValues)?.also { uri ->
                contentResolver.openOutputStream(uri).use { outputStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 96, outputStream))
                        throw IOException("Couldn't save bitmap")
                }
            } ?: throw IOException("Couldn't create MediaStore entry!")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun initObserver() {
        contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                lifecycleScope.launch {
                    if (readPermissionRequest)
                        sharedPhotoAdapter.updatePhotos(loadPhotosFromExternalStorage())
                }
            }
        }
    }

    private suspend fun loadPhotosFromExternalStorage(): List<SharedStoragePhoto> {
        return withContext(Dispatchers.IO) {
            val photos = mutableListOf<SharedStoragePhoto>()
            val collection = sdk29AndAbove {
                Images.Media.getContentUri(VOLUME_EXTERNAL)
            } ?: Images.Media.EXTERNAL_CONTENT_URI


            val projection = arrayOf(
                Images.Media._ID,
                Images.Media.DISPLAY_NAME,
                Images.Media.WIDTH,
                Images.Media.HEIGHT
            )
            contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${Images.Media.DISPLAY_NAME} ASC"
            )?.use { cursur ->
                val idColumn = cursur.getColumnIndexOrThrow(Images.Media._ID)
                val displayNameColumn =
                    cursur.getColumnIndexOrThrow(Images.Media.DISPLAY_NAME)
                val widthColumn = cursur.getColumnIndexOrThrow(Images.Media.WIDTH)
                val heightColumn = cursur.getColumnIndexOrThrow(Images.Media.HEIGHT)

                while (cursur.moveToNext()) {
                    val id = cursur.getLong(idColumn)
                    val displayName = cursur.getString(displayNameColumn)
                    val width = cursur.getInt(widthColumn)
                    val height = cursur.getInt(heightColumn)
                    val contentUri =
                        ContentUris.withAppendedId(Images.Media.EXTERNAL_CONTENT_URI, id)
                    photos.add(SharedStoragePhoto(id, displayName, width, height, contentUri))
                }
            }
            photos.toList()
        }
    }

    private fun deletePhotoFromExternalStorage(photoUri: Uri) {
        Log.d("TVS","Running2")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("TVS","Running3")
                contentResolver.delete(photoUri, null, null)
            } catch (e: SecurityException) {
                val intentSender = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        Log.d("TVS","Running4")
                        createDeleteRequest(
                            contentResolver,
                            listOf(photoUri)
                        ).intentSender
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        Log.d("TVS","Running5")
                        val recoverableSecurityException = e as? RecoverableSecurityException
                        recoverableSecurityException?.userAction?.actionIntent?.intentSender
                    }
                    else -> null
                }
                intentSender?.let {
                    Log.d("TVS","Running6")
                    intentSenderLauncher.launch(IntentSenderRequest.Builder(it).build())
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
    }
}