package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.richtext.LinkUrlPolicy
import io.github.linreal.cascade.editor.richtext.LinkValidationError
import io.github.linreal.cascade.editor.richtext.LinkValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkUrlPolicyTest {

    @Test
    fun `bare domain is normalized to https`() {
        assertValid(
            input = "example.com/path",
            expected = "https://example.com/path",
        )
    }

    @Test
    fun `https url preserves path query and fragment`() {
        assertValid(
            input = "https://example.com/path?q=One#Two",
            expected = "https://example.com/path?q=One#Two",
        )
    }

    @Test
    fun `http url is preserved verbatim`() {
        assertValid(
            input = "http://example.com/path",
            expected = "http://example.com/path",
        )
    }

    @Test
    fun `non-http scheme with double slash is preserved verbatim`() {
        assertValid(
            input = "ftp://example.com/file",
            expected = "ftp://example.com/file",
        )
    }

    @Test
    fun `surrounding whitespace is trimmed before normalization`() {
        assertValid(
            input = "  example.com/path?q=One#Two  ",
            expected = "https://example.com/path?q=One#Two",
        )
    }

    @Test
    fun `bare word without dot is accepted and prefixed`() {
        assertValid(
            input = "example",
            expected = "https://example",
        )
    }

    @Test
    fun `localhost host is accepted and prefixed`() {
        assertValid(
            input = "localhost:8080/path",
            expected = "https://localhost:8080/path",
        )
    }

    @Test
    fun `ip literal is accepted and prefixed`() {
        assertValid(
            input = "127.0.0.1",
            expected = "https://127.0.0.1",
        )
    }

    @Test
    fun `arbitrary text without scheme is accepted and prefixed`() {
        assertValid(
            input = "anything goes here",
            expected = "https://anything goes here",
        )
    }

    @Test
    fun `blank input is rejected`() {
        assertInvalid("", LinkValidationError.Blank)
        assertInvalid("   ", LinkValidationError.Blank)
    }

    private fun assertValid(input: String, expected: String) {
        val result = LinkUrlPolicy.validate(input)
        assertEquals(expected, result.normalizedUrl)
        assertNull(result.error)
        assertEquals(LinkValidationResult.Valid(expected), result)
    }

    private fun assertInvalid(input: String, expected: LinkValidationError) {
        val result = LinkUrlPolicy.validate(input)
        assertEquals(expected, result.error)
        assertNull(result.normalizedUrl)
        assertEquals(LinkValidationResult.Invalid(expected), result)
    }
}
