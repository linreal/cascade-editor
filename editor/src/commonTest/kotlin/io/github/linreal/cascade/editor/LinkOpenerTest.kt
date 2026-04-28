package io.github.linreal.cascade.editor

import androidx.compose.ui.platform.UriHandler
import io.github.linreal.cascade.editor.ui.createLinkOpener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LinkOpenerTest {

    @Test
    fun `consumer link opener receives URL and exceptions propagate`() {
        val calls = mutableListOf<String>()
        val opener = createLinkOpener(
            onOpenLink = { url ->
                calls += url
                error("consumer failure")
            },
            uriHandler = RecordingUriHandler(),
        )

        assertFailsWith<IllegalStateException> {
            opener("https://example.com")
        }
        assertEquals(listOf("https://example.com"), calls)
    }

    @Test
    fun `default platform opener swallows failures`() {
        val uriHandler = RecordingUriHandler(shouldThrow = true)
        val opener = createLinkOpener(
            onOpenLink = null,
            uriHandler = uriHandler,
        )

        opener("https://example.com")

        assertEquals(listOf("https://example.com"), uriHandler.calls)
    }

    private class RecordingUriHandler(
        private val shouldThrow: Boolean = false,
    ) : UriHandler {
        val calls = mutableListOf<String>()

        override fun openUri(uri: String) {
            calls += uri
            if (shouldThrow) {
                error("platform failure")
            }
        }
    }
}
