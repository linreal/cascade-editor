package io.github.linreal.cascade.storage

import androidx.compose.runtime.Composable

interface DocumentStorage {
    suspend fun read(): String?
    suspend fun write(json: String)
    suspend fun delete()
}

@Composable
expect fun rememberDocumentStorage(): DocumentStorage
