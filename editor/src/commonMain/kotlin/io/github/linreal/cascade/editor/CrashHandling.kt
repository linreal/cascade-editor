package io.github.linreal.cascade.editor

import kotlin.coroutines.cancellation.CancellationException

/** How the editor handles an internal failure caught at a containment boundary. */
public enum class CrashPolicy {
    /** Re-throw caught failures. Use in tests and debug builds to surface bugs. */
    Rethrow,

    /** Catch the failure, report it, and degrade to a safe fallback. Use in release. */
    ContainAndReport,
}

/** A contained internal failure, surfaced to the host via [CascadeErrorReporter]. */
public data class CascadeError(
    /** Short, stable label for where the failure happened, e.g. "blockRender". */
    val context: String,
    /** The caught throwable. */
    val cause: Throwable,
)

/** Host hook for contained internal failures. Never invoked under [CrashPolicy.Rethrow]. */
public typealias CascadeErrorReporter = (CascadeError) -> Unit

/**
 * Runs [block] under [policy].
 *
 * Under [CrashPolicy.ContainAndReport] a thrown [Throwable] (except [CancellationException],
 * which always propagates) is reported via [reporter] — or [loge] when [reporter] is null —
 * and [fallback] is returned. Reporter failures are logged and contained so host telemetry
 * wiring cannot defeat the editor's containment boundary. Under [CrashPolicy.Rethrow] the
 * throwable propagates unchanged.
 */
internal inline fun <T> guarded(
    policy: CrashPolicy,
    noinline reporter: CascadeErrorReporter?,
    context: String,
    fallback: () -> T,
    block: () -> T,
): T {
    return try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        if (policy == CrashPolicy.Rethrow) throw throwable
        reportContainedFailure(reporter, CascadeError(context, throwable))
        fallback()
    }
}

internal fun reportContainedFailure(
    reporter: CascadeErrorReporter?,
    error: CascadeError,
) {
    if (reporter == null) {
        loge("Contained failure in ${error.context}: ${error.cause}")
        return
    }
    try {
        reporter(error)
    } catch (cancellation: CancellationException) {
        // A reporter wired into a coroutine scope may be cancelled; honor structured
        // concurrency rather than swallowing the cancellation here.
        throw cancellation
    } catch (reporterFailure: Throwable) {
        loge(
            "Contained failure in ${error.context}; internal-error reporter also failed: " +
                "$reporterFailure"
        )
    }
}
