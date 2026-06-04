package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class CascadeEditorConfigCrashTest {

    @Test
    fun defaults_contain_and_report_with_no_reporter() {
        val config = CascadeEditorConfig()
        assertEquals(CrashPolicy.ContainAndReport, config.crashPolicy)
        assertNull(config.onInternalError)
    }

    @Test
    fun copy_preserves_crash_fields() {
        val reporter: CascadeErrorReporter = {}
        val config = CascadeEditorConfig(crashPolicy = CrashPolicy.Rethrow, onInternalError = reporter)
        val copy = config.copy(readOnly = true)
        assertEquals(CrashPolicy.Rethrow, copy.crashPolicy)
        assertSame(reporter, copy.onInternalError)
    }
}
