package io.github.linreal.cascade.ios.resources

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException
import org.jetbrains.compose.resources.ResourceReader
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength
import platform.posix.memcpy

private const val CascadeEditorBundleId: String = "io.github.linreal.cascade.editor"

/**
 * Reads Compose resources from CascadeEditor's own dynamic framework bundle.
 *
 * Compose's default iOS reader uses the first embedded framework that contains a
 * `composeResources` directory. That is ambiguous when an app embeds more than
 * one Compose-based SDK. Binding the editor subtree to this reader keeps resource
 * lookup deterministic. The main-bundle fallback preserves local static/debug
 * integrations that explicitly copy resources.
 */
@OptIn(ExperimentalResourceApi::class, BetaInteropApi::class, ExperimentalForeignApi::class)
internal object CascadeEditorResourceReader : ResourceReader {
    private val resourceBaseDirectory: String by lazy {
        val fileManager = NSFileManager.defaultManager
        val frameworkResourceDirectory = NSBundle.bundleWithIdentifier(CascadeEditorBundleId)
            ?.resourcePath
            ?.takeIf { fileManager.fileExistsAtPath("$it/composeResources") }

        frameworkResourceDirectory ?: "${NSBundle.mainBundle.resourcePath}/compose-resources"
    }

    override suspend fun read(path: String): ByteArray =
        readData(resourcePath(path)).toByteArray()

    override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray {
        val fullPath = resourcePath(path)
        val fileHandle = NSFileHandle.fileHandleForReadingAtPath(fullPath)
            ?: throw MissingResourceException(fullPath)
        return try {
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                fileHandle.seekToOffset(offset.toULong(), error.ptr)
                error.value?.let { seekError ->
                    throw MissingResourceException("$fullPath. ${seekError.localizedDescription}")
                }
            }
            fileHandle.readDataOfLength(size.toULong()).toByteArray()
        } finally {
            fileHandle.closeFile()
        }
    }

    override fun getUri(path: String): String =
        NSURL.fileURLWithPath(resourcePath(path)).toString()

    private fun resourcePath(path: String): String = "$resourceBaseDirectory/$path"

    private fun readData(path: String): NSData =
        NSFileManager.defaultManager.contentsAtPath(path)
            ?: throw MissingResourceException(path)

    private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).apply {
        if (isNotEmpty()) {
            usePinned { pinned ->
                memcpy(pinned.addressOf(0), bytes, length)
            }
        }
    }
}
