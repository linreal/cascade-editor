package io.github.linreal.cascade.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private class AndroidDocumentStorage(filesDir: File) : DocumentStorage {
    private val file = File(filesDir, "saved_document.json")

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (file.exists()) file.readText() else null
    }

    override suspend fun write(json: String): Unit = withContext(Dispatchers.IO) {
        file.writeText(json)
    }

    override suspend fun delete(): Unit = withContext(Dispatchers.IO) {
        file.delete()
    }
}

@Composable
actual fun rememberDocumentStorage(): DocumentStorage {
    val context = LocalContext.current
    return remember { AndroidDocumentStorage(context.applicationContext.filesDir) }
}
