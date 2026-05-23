package com.controlmoblie.util

import org.junit.Assert.*
import org.junit.Test

class ScreenCaptureManagerTest {

    @Test
    fun `isReady returns false before init`() {
        ScreenCaptureManager.release()
        assertFalse("should not be ready before init", ScreenCaptureManager.isReady)
    }

    @Test
    fun `isReady true after init with bypass`() {
        ScreenCaptureManager.release()
        ScreenCaptureManager.bypassInit = true
        ScreenCaptureManager.init(null, 1080, 2400, 480)
        assertTrue("isReady should be true after init", ScreenCaptureManager.isReady)
    }

    @Test
    fun `release clears state`() {
        ScreenCaptureManager.release()
        ScreenCaptureManager.bypassInit = true
        ScreenCaptureManager.init(null, 1080, 2400, 480)
        ScreenCaptureManager.release()
        assertFalse("isReady should be false after release", ScreenCaptureManager.isReady)
    }

    @Test
    fun `capture returns null when not ready`() {
        ScreenCaptureManager.release()
        assertNull("capture should return null when not ready", ScreenCaptureManager.capture())
    }
}
