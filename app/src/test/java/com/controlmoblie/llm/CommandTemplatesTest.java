package com.controlmoblie.llm;

import com.controlmoblie.model.Action;
import com.controlmoblie.model.ScrollDirection;
import com.controlmoblie.model.ScrollDistance;

import org.junit.Test;
import static org.junit.Assert.*;

public class CommandTemplatesTest {
    @Test
    public void testMatchSendMessage() {
        String result = CommandTemplates.match("发消息给张三说你好");
        assertNotNull(result);
        assertTrue(result.contains("sequence"));

        Action action = InstructionParser.parse(result);
        assertEquals(Action.ActionType.SEQUENCE, action.getType());
        java.util.List<Action> steps = ((Action.Sequence) action).getSteps();
        assertEquals(Action.ActionType.WAIT, steps.get(0).getType());
        assertEquals(500, ((Action.Wait) steps.get(0)).getMs());
        assertEquals(Action.ActionType.TYPE, steps.get(5).getType());
        assertEquals("你好", ((Action.Type) steps.get(5)).getText());
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

    @Test
    public void testOpenMiniProgramIncludesValidScrollStep() {
        String result = CommandTemplates.match("打开小程序腾讯文档");
        assertNotNull(result);

        Action action = InstructionParser.parse(result);
        assertEquals(Action.ActionType.SEQUENCE, action.getType());
        java.util.List<Action> steps = ((Action.Sequence) action).getSteps();
        assertEquals(Action.ActionType.SCROLL, steps.get(1).getType());
        assertEquals(ScrollDirection.DOWN, ((Action.Scroll) steps.get(1)).getDirection());
        assertEquals(ScrollDistance.SHORT, ((Action.Scroll) steps.get(1)).getDistance());
    }
}
