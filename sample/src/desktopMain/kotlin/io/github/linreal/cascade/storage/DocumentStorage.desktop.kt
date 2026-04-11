package io.github.linreal.cascade.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private class DesktopDocumentStorage(
    dataDirectory: Path,
) : DocumentStorage {
    private val file = dataDirectory.resolve("saved_document.json")

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (Files.exists(file)) {
            Files.readString(file)
        } else {
            null
        }
    }

    override suspend fun write(json: String): Unit = withContext(Dispatchers.IO) {
        Files.createDirectories(file.parent)
        Files.writeString(file, json)
    }

    override suspend fun delete(): Unit = withContext(Dispatchers.IO) {
        Files.deleteIfExists(file)
    }
}

@Composable
actual fun rememberDocumentStorage(): DocumentStorage {
    return remember { DesktopDocumentStorage(resolveDesktopDataDirectory()) }
}

private fun resolveDesktopDataDirectory(): Path {
    val home = System.getProperty("user.home")
    val osName = System.getProperty("os.name").lowercase()

    return when {
        osName.contains("mac") -> Paths.get(
            home,
            "Library",
            "Application Support",
            "CascadeEditor",
        )
        osName.contains("win") -> {
            val appData = System.getenv("APPDATA")
            if (appData != null) {
                Paths.get(appData, "CascadeEditor")
            } else {
                Paths.get(home, "AppData", "Roaming", "CascadeEditor")
            }
        }
        else -> {
            val xdgDataHome = System.getenv("XDG_DATA_HOME")
            if (xdgDataHome != null) {
                Paths.get(xdgDataHome, "CascadeEditor")
            } else {
                Paths.get(home, ".local", "share", "CascadeEditor")
            }
        }
    }
}
