@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.linreal.cascade.editor.richtext

import kotlinx.cinterop.useContents
import platform.Foundation.NSDataDetector
import platform.Foundation.NSMakeRange
import platform.Foundation.NSNotFound
import platform.Foundation.NSRegularExpression
import platform.Foundation.NSTextCheckingResult
import platform.Foundation.NSTextCheckingTypePhoneNumber
import platform.Foundation.matchesInString
import platform.Foundation.phoneNumber
import platform.Foundation.rangeAtIndex

private val phoneNumberDetector: NSDataDetector? by lazy {
    runCatching {
        NSDataDetector(
            types = NSTextCheckingTypePhoneNumber,
            error = null,
        )
    }.getOrNull()
}

internal actual fun detectPlatformPhoneLinks(text: String): List<DetectedPhoneLink> {
    if (text.isEmpty()) return emptyList()
    val detector = phoneNumberDetector ?: return emptyList()

    return runCatching {
        val matches = (detector as NSRegularExpression).matchesInString(
            string = text,
            options = 0u,
            range = NSMakeRange(0u, text.length.toULong()),
        )
        matches.mapNotNull { value ->
            val match = value as? NSTextCheckingResult ?: return@mapNotNull null
            val (location, length) = match.rangeAtIndex(0u).useContents {
                location to length
            }
            if (location == NSNotFound.toULong() || location > Int.MAX_VALUE.toULong()) {
                return@mapNotNull null
            }
            if (length > Int.MAX_VALUE.toULong() - location) {
                return@mapNotNull null
            }

            val start = location.toInt()
            val end = start + length.toInt()
            val target = match.phoneNumber?.let(::phoneNumberToTelTarget)
                ?: return@mapNotNull null
            DetectedPhoneLink(start = start, end = end, target = target)
        }
    }.getOrDefault(emptyList())
}
