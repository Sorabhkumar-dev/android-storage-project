package com.sorabh.storageproject.utils

import android.os.Build

inline fun <T> sdk29AndAbove(sdk29: () -> T): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) sdk29()
    else null
}