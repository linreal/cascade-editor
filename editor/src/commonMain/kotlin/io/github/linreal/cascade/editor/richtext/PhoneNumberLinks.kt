package io.github.linreal.cascade.editor.richtext

import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan

internal data class DetectedPhoneLink(
    val start: Int,
    val end: Int,
    val target: String,
)

internal expect fun detectPlatformPhoneLinks(text: String): List<DetectedPhoneLink>

internal fun automaticPhoneLinkSpans(
    text: String,
    explicitSpans: List<TextSpan>,
    detections: List<DetectedPhoneLink> = detectPlatformPhoneLinks(text),
): List<TextSpan> {
    val explicitLinks = explicitSpans.filter { it.style is SpanStyle.Link }
    val accepted = mutableListOf<TextSpan>()

    detections.sortedBy(DetectedPhoneLink::start).forEach { detection ->
        val target = detection.target
            .takeIf { it.startsWith("tel:", ignoreCase = true) }
            ?.substring(4)
            ?.let(::phoneNumberToTelTarget)
            ?: return@forEach
        if (
            detection.start < 0 ||
            detection.end > text.length ||
            detection.start >= detection.end
        ) {
            return@forEach
        }
        if (explicitLinks.any { it.overlaps(detection.start, detection.end) }) return@forEach
        if (accepted.any { it.overlaps(detection.start, detection.end) }) return@forEach

        accepted += TextSpan(
            start = detection.start,
            end = detection.end,
            style = SpanStyle.Link(target),
        )
    }

    return accepted
}

internal fun phoneNumberToTelTarget(phoneNumber: String): String? {
    var hasPlus = false
    val digits = StringBuilder(phoneNumber.length)

    phoneNumber.forEach { character ->
        when {
            character in '0'..'9' -> digits.append(character)
            character == '+' && !hasPlus && digits.isEmpty() -> hasPlus = true
            character == ' ' || character == '-' || character == '(' ||
                character == ')' || character == '.' -> Unit
            else -> return null
        }
    }

    if (digits.isEmpty()) return null
    return "tel:${if (hasPlus) "+" else ""}$digits"
}

private fun TextSpan.overlaps(start: Int, end: Int): Boolean =
    this.start < end && start < this.end
