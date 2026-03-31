package com.example.fishingmapkmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform