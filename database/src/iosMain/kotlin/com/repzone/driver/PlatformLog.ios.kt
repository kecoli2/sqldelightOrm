package com.repzone.driver

import platform.Foundation.NSLog

actual fun platformLog(tag: String, message: String) {
    NSLog("[$tag] $message")
}