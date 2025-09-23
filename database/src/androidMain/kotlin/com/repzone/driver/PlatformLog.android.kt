package com.repzone.driver

import android.util.Log

actual fun platformLog(tag: String, message: String) {
    Log.d(tag, message)
}