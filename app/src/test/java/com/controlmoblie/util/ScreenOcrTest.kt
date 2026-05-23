package com.controlmoblie.util

import org.junit.Assert.*
import org.junit.Test

class ScreenOcrTest {

    @Test
    fun `isReady returns false before init`() {
        assertFalse("should not be ready before init", ScreenOcr.isReady)
    }

    @Test
    fun `init sets isReady to true`() {
        ScreenOcr.release()
        ScreenOcr.init()
        assertTrue("isReady should be true after init", ScreenOcr.isReady)
    }

    @Test
    fun `release clears state`() {
        ScreenOcr.init()
        ScreenOcr.release()
        assertFalse("isReady should be false after release", ScreenOcr.isReady)
    }

    @Test
    fun `OcrResult stores coordinates correctly`() {
        val r = OcrResult("test", 10f, 20f, 100f, 50f)
        assertEquals("text", "test", r.text)
        assertEquals("x", 10f, r.x)
        assertEquals("y", 20f, r.y)
        assertEquals("width", 100f, r.width)
        assertEquals("height", 50f, r.height)
    }
}
