package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.ui.calculateAutoScrollAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [calculateAutoScrollAmount] — the pure function that
 * determines scroll speed based on drag position within the viewport.
 */
class AutoScrollTest {

    // Common test parameters
    private val viewportHeight = 1000f
    private val hotZonePx = 100f
    private val maxSpeed = 25f // px per frame

    // =========================================================================
    // Outside hot zones — no scrolling
    // =========================================================================

    @Test
    fun `drag in middle of viewport returns zero`() {
        val result = calculateAutoScrollAmount(
            dragY = 500f,
            viewportHeight = viewportHeight,
            hotZonePx = hotZonePx,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertEquals(0f, result)
    }

    @Test
    fun `drag exactly at top boundary returns zero`() {
        // dragY == hotZonePx is outside the top hot zone
        val result = calculateAutoScrollAmount(
            dragY = 100f,
            viewportHeight = viewportHeight,
            hotZonePx = hotZonePx,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertEquals(0f, result)
    }

    @Test
    fun `drag exactly at bottom boundary returns zero`() {
        // dragY == viewportHeight - hotZonePx is outside the bottom hot zone
        val result = calculateAutoScrollAmount(
            dragY = 900f,
            viewportHeight = viewportHeight,
            hotZonePx = hotZonePx,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertEquals(0f, result)
    }

    // =========================================================================
    // Top hot zone — scroll up (negative)
    // =========================================================================

    @Test
    fun `drag just inside top hot zone returns small negative`() {
        // dragY = 99 → depth = (100-99)/100 = 0.01
        val result = calculateAutoScrollAmount(
            dragY = 99f,
            viewportHeight = viewportHeight,
            hotZonePx = hotZonePx,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertTrue(result < 0f, "Should scroll up (negative)")
        assertTrue(result > -maxSpeed, "Should be less than max speed")
    }

    @Test
    fun `drag at midpoint of top hot zone returns half max speed`() {
        // dragY = 50 → depth = (100-50)/100 = 0.5
        val result = calculateAutoScrollAmount(
            dragY = 50f,
            viewportHeight = viewportHeight,
            hotZonePx = hotZonePx,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertEquals(-maxSpeed * 0.5f, result)
    }

    @Test
    fun `drag at top edge returns full max speed`() {
        // dragY = 0 → depth = (100-0)/100 = 1.0
        val result = calculateAutoScrollAmount(
            dragY = 0f,
            viewportHeight = viewportHeight,
            hotZonePx = hotZonePx,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertEquals(-maxSpeed, result)
    }

    @Test
    fun `drag beyond top edge clamps to max speed`() {
        // dragY = -50 → depth = (100-(-50))/100 = 1.5, clamped to 1.0
        val result = calculateAutoScrollAmount(
            dragY = -50f,
            viewportHeight = viewportHeight,
            hotZonePx = hotZonePx,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertEquals(-maxSpeed, result)
    }

    // =========================================================================
    // Bottom hot zone — scroll down (positive)
    // =========================================================================

    @Test
    fun `drag just inside bottom hot zone returns small positive`() {
        // dragY = 901 → bottomBoundary = 900, depth = (901-900)/100 = 0.01
        val result = calculateAutoScrollAmount(
            dragY = 901f,
            viewportHeight = viewportHeight,
            hotZonePx = hotZonePx,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertTrue(result > 0f, "Should scroll down (positive)")
        assertTrue(result < maxSpeed, "Should be less than max speed")
    }

    @Test
    fun `drag at midpoint of bottom hot zone returns half max speed`() {
        // dragY = 950 → bottomBoundary = 900, depth = (950-900)/100 = 0.5
        val result = calculateAutoScrollAmount(
            dragY = 950f,
            viewportHeight = viewportHeight,
            hotZonePx = hotZonePx,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertEquals(maxSpeed * 0.5f, result)
    }

    @Test
    fun `drag at bottom edge returns full max speed`() {
        // dragY = 1000 → bottomBoundary = 900, depth = (1000-900)/100 = 1.0
        val result = calculateAutoScrollAmount(
            dragY = 1000f,
            viewportHeight = viewportHeight,
            hotZonePx = hotZonePx,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertEquals(maxSpeed, result)
    }

    @Test
    fun `drag beyond bottom edge clamps to max speed`() {
        // dragY = 1100 → bottomBoundary = 900, depth = (1100-900)/100 = 2.0, clamped to 1.0
        val result = calculateAutoScrollAmount(
            dragY = 1100f,
            viewportHeight = viewportHeight,
            hotZonePx = hotZonePx,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertEquals(maxSpeed, result)
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    fun `zero viewport height returns zero`() {
        val result = calculateAutoScrollAmount(
            dragY = 50f,
            viewportHeight = 0f,
            hotZonePx = hotZonePx,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertEquals(0f, result)
    }

    @Test
    fun `negative viewport height returns zero`() {
        val result = calculateAutoScrollAmount(
            dragY = 50f,
            viewportHeight = -100f,
            hotZonePx = hotZonePx,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertEquals(0f, result)
    }

    @Test
    fun `zero hot zone returns zero`() {
        val result = calculateAutoScrollAmount(
            dragY = 50f,
            viewportHeight = viewportHeight,
            hotZonePx = 0f,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertEquals(0f, result)
    }

    @Test
    fun `negative hot zone returns zero`() {
        val result = calculateAutoScrollAmount(
            dragY = 50f,
            viewportHeight = viewportHeight,
            hotZonePx = -10f,
            maxSpeedPxPerFrame = maxSpeed
        )
        assertEquals(0f, result)
    }

    @Test
    fun `speed increases linearly from boundary to edge in top zone`() {
        // Check several points and verify monotonically increasing magnitude
        val speeds = listOf(90f, 70f, 50f, 30f, 10f).map { dragY ->
            calculateAutoScrollAmount(
                dragY = dragY,
                viewportHeight = viewportHeight,
                hotZonePx = hotZonePx,
                maxSpeedPxPerFrame = maxSpeed
            )
        }
        // All should be negative (scrolling up)
        speeds.forEach { assertTrue(it < 0f) }
        // Magnitude should increase (more negative) as dragY decreases
        for (i in 0 until speeds.size - 1) {
            assertTrue(speeds[i] > speeds[i + 1], "Speed should increase as dragY decreases")
        }
    }

    @Test
    fun `speed increases linearly from boundary to edge in bottom zone`() {
        // Check several points and verify monotonically increasing
        val speeds = listOf(910f, 930f, 950f, 970f, 990f).map { dragY ->
            calculateAutoScrollAmount(
                dragY = dragY,
                viewportHeight = viewportHeight,
                hotZonePx = hotZonePx,
                maxSpeedPxPerFrame = maxSpeed
            )
        }
        // All should be positive (scrolling down)
        speeds.forEach { assertTrue(it > 0f) }
        // Should increase as dragY increases
        for (i in 0 until speeds.size - 1) {
            assertTrue(speeds[i] < speeds[i + 1], "Speed should increase as dragY increases")
        }
    }
}
