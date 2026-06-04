package io.github.linreal.cascade.editor

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class CrashGuardTest {

    @Test
    fun success_returns_block_result_and_does_not_report() {
        var reported: CascadeError? = null
        val result = guarded(
            policy = CrashPolicy.ContainAndReport,
            reporter = { reported = it },
            context = "ctx",
            fallback = { -1 },
        ) { 42 }
        assertEquals(42, result)
        assertNull(reported)
    }

    @Test
    fun contain_and_report_returns_fallback_and_reports_context_and_cause() {
        var reported: CascadeError? = null
        val boom = IllegalStateException("boom")
        val result = guarded(
            policy = CrashPolicy.ContainAndReport,
            reporter = { reported = it },
            context = "blockRender",
            fallback = { -1 },
        ) { throw boom }
        assertEquals(-1, result)
        assertEquals("blockRender", reported?.context)
        assertSame(boom, reported?.cause)
    }

    @Test
    fun rethrow_policy_propagates_and_never_reports() {
        var reported: CascadeError? = null
        assertFailsWith<IllegalStateException> {
            guarded(
                policy = CrashPolicy.Rethrow,
                reporter = { reported = it },
                context = "ctx",
                fallback = { -1 },
            ) { throw IllegalStateException("boom") }
        }
        assertNull(reported)
    }

    @Test
    fun cancellation_is_always_rethrown_even_when_containing() {
        assertFailsWith<CancellationException> {
            guarded(
                policy = CrashPolicy.ContainAndReport,
                reporter = {},
                context = "ctx",
                fallback = { -1 },
            ) { throw CancellationException("cancel") }
        }
    }

    @Test
    fun null_reporter_falls_back_to_logger_without_throwing() {
        val result = guarded(
            policy = CrashPolicy.ContainAndReport,
            reporter = null,
            context = "ctx",
            fallback = { 7 },
        ) { throw RuntimeException("x") }
        assertEquals(7, result)
    }

    @Test
    fun throwing_reporter_is_contained_and_fallback_is_returned() {
        val result = guarded(
            policy = CrashPolicy.ContainAndReport,
            reporter = { throw IllegalStateException("reporter boom") },
            context = "ctx",
            fallback = { 7 },
        ) { throw RuntimeException("editor boom") }
        assertEquals(7, result)
    }

    @Test
    fun reporter_throwing_cancellation_propagates() {
        assertFailsWith<CancellationException> {
            reportContainedFailure(
                reporter = { throw CancellationException("reporter cancelled") },
                error = CascadeError("ctx", RuntimeException("editor boom")),
            )
        }
    }
}
