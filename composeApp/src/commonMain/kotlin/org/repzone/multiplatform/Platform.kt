package org.repzone.multiplatform

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform