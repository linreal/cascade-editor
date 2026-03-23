package io.github.linreal.cascade.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile

private class IosDocumentStorage : DocumentStorage {
    private val filePath: String = run {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        )
        (paths.first() as String) + "/saved_document.json"
    }

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(filePath)) return@withContext null
        val data = fileManager.contentsAtPath(filePath) ?: return@withContext null
        NSString.create(data, NSUTF8StringEncoding) as? String
    }

    override suspend fun write(json: String): Unit = withContext(Dispatchers.IO) {
        val nsString = json as NSString
        val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return@withContext
        data.writeToFile(filePath, true)
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun delete(): Unit = withContext(Dispatchers.IO) {
        NSFileManager.defaultManager.removeItemAtPath(filePath, null)
    }
}

@Composable
actual fun rememberDocumentStorage(): DocumentStorage {
    return remember { IosDocumentStorage() }
}
