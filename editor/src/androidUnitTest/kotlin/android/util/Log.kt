@file:JvmName("Log")
@file:Suppress("PackageDirectoryMismatch")

package android.util

/*
 * Test-only shadow of android.util.Log.
 */

public fun d(tag: String?, msg: String?): Int {
    println("D/$tag: $msg")
    return 0
}

public fun e(tag: String?, msg: String?): Int {
    println("E/$tag: $msg")
    return 0
}
