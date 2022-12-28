package com.sorabh.storageproject.model

import android.net.Uri

data class SharedStoragePhoto(val id: Long,val displayName:String,val width:Int,val height:Int,val contentUri:Uri)