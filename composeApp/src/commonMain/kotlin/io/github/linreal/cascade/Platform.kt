package io.github.linreal.cascade

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform