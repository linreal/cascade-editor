package io.github.linreal.cascade.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

private class BrowserDocumentStorage : DocumentStorage {
    private val key = "cascade_editor_document"

    override suspend fun read(): String? {
        return window.localStorage.getItem(key)
    }

    override suspend fun write(json: String) {
        window.localStorage.setItem(key, json)
    }

    override suspend fun delete() {
        window.localStorage.removeItem(key)
    }
}

@Composable
actual fun rememberDocumentStorage(): DocumentStorage {
    return remember { BrowserDocumentStorage() }
}
