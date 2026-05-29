package com.controlmoblie.llm;

import com.controlmoblie.model.Action;
import com.controlmoblie.model.NavType;
import com.controlmoblie.model.ScrollDirection;
import com.controlmoblie.model.ScrollDistance;

import org.junit.Test;
import static org.junit.Assert.*;

public class InstructionParserTest {
    @Test
    public void testParseClick() {
        String json = "{\"action\":\"click\",\"target\":\"公众号\"}";
        Action action = InstructionParser.parse(json);
        assertEquals(Action.ActionType.CLICK, action.getType());
        assertEquals("公众号", ((Action.Click) action).getTarget());
    }

    @Test
    public void testParseOpenApp() {
        String json = "{\"action\":\"open_app\",\"package\":\"com.zhihu.android\",\"displayName\":\"知乎\"}";
        Action action = InstructionParser.parse(json);
        assertEquals(Action.ActionType.OPEN_APP, action.getType());
        assertEquals("com.zhihu.android", ((Action.OpenApp) action).getPackageName());
    }

    @Test
    public void testParseScroll() {
        String json = "{\"action\":\"scroll\",\"direction\":\"up\",\"distance\":\"half\"}";
        Action action = InstructionParser.parse(json);
        assertEquals(Action.ActionType.SCROLL, action.getType());
        assertEquals(ScrollDirection.UP, ((Action.Scroll) action).getDirection());
        assertEquals(ScrollDistance.HALF, ((Action.Scroll) action).getDistance());
    }

    @Test
    public void testParseNavigate() {
        String json = "{\"action\":\"navigate\",\"type\":\"back\"}";
        Action action = InstructionParser.parse(json);
        assertEquals(Action.ActionType.NAVIGATE, action.getType());
        assertEquals(NavType.BACK, ((Action.Navigate) action).getNavType());
    }

    @Test
    public void testParseType() {
        String json = "{\"action\":\"type\",\"text\":\"你好\"}";
        Action action = InstructionParser.parse(json);
        assertEquals(Action.ActionType.TYPE, action.getType());
        assertEquals("你好", ((Action.Type) action).getText());
    }

    @Test
    public void testParseWait() {
        String json = "{\"action\":\"wait\",\"ms\":500}";
        Action action = InstructionParser.parse(json);
        assertEquals(Action.ActionType.WAIT, action.getType());
        assertEquals(500, ((Action.Wait) action).getMs());
    }

    @Test
    public void testParseSequence() {
        String json = "{\"action\":\"sequence\",\"steps\":[{\"action\":\"click\",\"target\":\"a\"},{\"action\":\"click\",\"target\":\"b\"}]}";
        Action action = InstructionParser.parse(json);
        assertEquals(Action.ActionType.SEQUENCE, action.getType());
        assertEquals(2, ((Action.Sequence) action).getActions().size());
    }

    @Test
    public void testParseError() {
        String json = "{\"action\":\"error\",\"message\":\"无法识别\"}";
        Action action = InstructionParser.parse(json);
        assertNotNull(action);
        assertEquals(Action.ActionType.CLICK, action.getType());
        assertEquals("error", ((Action.Click) action).getTarget());
    }

    @Test
    public void testParseInvalidJson() {
        Action action = InstructionParser.parse("not json");
        assertNotNull(action);
        assertEquals(Action.ActionType.CLICK, action.getType());
        assertEquals("error", ((Action.Click) action).getTarget());
    }

    @Test
    public void testBuildPrompt() {
        String prompt = InstructionParser.buildPrompt("打开微信", "com.tencent.mm");
        assertTrue(prompt.contains("打开微信"));
        assertTrue(prompt.contains("com.tencent.mm"));
        assertTrue(prompt.contains("<|im_end|>"));
        assertFalse(prompt.contains("\nп\n"));
    }
}
