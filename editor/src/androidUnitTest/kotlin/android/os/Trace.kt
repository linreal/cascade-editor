@file:JvmName("Trace")
@file:Suppress("PackageDirectoryMismatch")

package android.os

/*
 * Test-only no-op shadow of android.os.Trace.
 *
 * The Android unit-test classpath ships a stub `mockable-android.jar` whose
 * framework methods throw "Method ... not mocked." by default. Compose
 * runtime calls Trace.beginSection / endSection during composition, so any
 * commonTest exercising Compose on the Android target (e.g. the toolbar
 * controller's manual Recomposer harness) would otherwise blow up before a
 * single assertion runs.
 *
 * Placing this file under src/androidUnitTest puts it on the test classpath
 * ahead of mockable-android.jar, so JUnit picks up these no-ops instead of
 * the throwing stubs. Surgical: only Trace is replaced. Every other
 * framework call stays strict and will throw loudly if exercised, the way
 * the Android Gradle plugin intends.
 *
 * The file-level @JvmName makes these top-level functions compile to
 * `static` members of `android.os.Trace`, matching the real API's call shape
 * exactly. No @JvmStatic on object members, no reflection tricks.
 */

public fun beginSection(sectionName: String?): Unit = Unit
public fun endSection(): Unit = Unit
public fun isEnabled(): Boolean = false
public fun beginAsyncSection(methodName: String?, cookie: Int): Unit = Unit
public fun endAsyncSection(methodName: String?, cookie: Int): Unit = Unit
public fun setCounter(counterName: String?, counterValue: Long): Unit = Unit
