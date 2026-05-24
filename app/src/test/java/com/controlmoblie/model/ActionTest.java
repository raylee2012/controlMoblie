package com.controlmoblie.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class ActionTest {
    @Test
    public void testClick() {
        Action.Click click = new Action.Click("公众号");
        assertEquals(Action.Type.CLICK, click.getType());
        assertEquals("公众号", click.getTarget());
    }

    @Test
    public void testScroll() {
        Action.Scroll scroll = new Action.Scroll(ScrollDirection.UP, ScrollDistance.HALF);
        assertEquals(Action.Type.SCROLL, scroll.getType());
        assertEquals(ScrollDirection.UP, scroll.getDirection());
        assertEquals(ScrollDistance.HALF, scroll.getDistance());
    }

    @Test
    public void testOpenApp() {
        Action.OpenApp open = new Action.OpenApp("com.zhihu.android", "知乎");
        assertEquals(Action.Type.OPEN_APP, open.getType());
        assertEquals("com.zhihu.android", open.getPackageName());
        assertEquals("知乎", open.getDisplayName());
    }

    @Test
    public void testNavigate() {
        Action.Navigate nav = new Action.Navigate(NavType.BACK);
        assertEquals(Action.Type.NAVIGATE, nav.getType());
        assertEquals(NavType.BACK, nav.getNavType());
    }

    @Test
    public void testOpenWeChatPage() {
        Action.OpenWeChatPage page = new Action.OpenWeChatPage("officialaccounts");
        assertEquals(Action.Type.OPEN_WECHAT_PAGE, page.getType());
        assertEquals("officialaccounts", page.getPage());
    }

    @Test
    public void testSequence() {
        java.util.List<Action> actions = java.util.Arrays.asList(
            new Action.Click("test1"),
            new Action.Click("test2")
        );
        Action.Sequence seq = new Action.Sequence(actions);
        assertEquals(Action.Type.SEQUENCE, seq.getType());
        assertEquals(2, seq.getActions().size());
    }
}
