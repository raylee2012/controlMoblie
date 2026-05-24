package com.controlmoblie.model;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ModelTest {
    @Test
    public void testInstructionResult() {
        InstructionResult r = new InstructionResult(true, "已点击", new Action.Click("test"));
        assertTrue(r.isSuccess());
        assertEquals("已点击", r.getMessage());
        assertNotNull(r.getAction());
    }

    @Test
    public void testScreenState() {
        ScreenState s = new ScreenState("com.tencent.mm", Arrays.asList("微信", "通讯录"));
        assertEquals("com.tencent.mm", s.getPackageName());
        assertEquals(2, s.getTexts().size());
    }

    @Test
    public void testVoiceState() {
        VoiceState v = new VoiceState();
        assertFalse(v.isRecording());
        v.setRecording(true);
        assertTrue(v.isRecording());
        v.setProcessing(true);
        assertTrue(v.isProcessing());
    }

    @Test
    public void testDownloadProgress() {
        DownloadProgress p = new DownloadProgress(0.5f);
        assertTrue(p.isInProgress());
        assertEquals(0.5f, p.getProgress(), 0.01f);

        DownloadProgress complete = new DownloadProgress(1.0f);
        assertFalse(complete.isInProgress());
    }
}
