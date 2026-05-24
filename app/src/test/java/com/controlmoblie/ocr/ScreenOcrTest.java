package com.controlmoblie.ocr;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenOcrTest {
    @Test
    public void testInitBypass() {
        ScreenOcr.bypassMlKitInit = true;
        ScreenOcr.init(null);
        assertTrue(ScreenOcr.isReady());
        ScreenOcr.release();
    }

    @Test
    public void testRelease() {
        ScreenOcr.bypassMlKitInit = true;
        ScreenOcr.init(null);
        assertTrue(ScreenOcr.isReady());
        ScreenOcr.release();
        assertFalse(ScreenOcr.isReady());
    }
}
