package com.controlmoblie.llm;

import org.junit.Test;
import static org.junit.Assert.*;

public class CommandTemplatesTest {
    @Test
    public void testMatchSendMessage() {
        String result = CommandTemplates.match("发消息给张三说你好");
        assertNotNull(result);
        assertTrue(result.contains("sequence"));
    }

    @Test
    public void testMatchMoments() {
        String result = CommandTemplates.match("看朋友圈");
        assertNotNull(result);
        assertTrue(result.contains("moments"));
    }

    @Test
    public void testMatchNoTemplate() {
        String result = CommandTemplates.match("随便说句话");
        assertNull(result);
    }

    @Test
    public void testMatchPostMoment() {
        String result = CommandTemplates.match("发朋友圈说今天天气不错");
        assertNotNull(result);
        assertTrue(result.contains("sequence"));
    }
}
