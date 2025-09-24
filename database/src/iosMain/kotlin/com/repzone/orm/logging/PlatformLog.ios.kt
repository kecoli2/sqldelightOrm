package com.repzone.orm.logging

import platform.Foundation.NSLog

actual fun platformLog(tag: String, message: String) {
    NSLog("%s: %s", tag, message)
    // İstersen println(message) de ekleyebilirsin:
    // println("$tag: $message")
}