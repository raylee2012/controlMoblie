package com.controlmoblie.resolver;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppResolverTest {
    @Test
    public void testKnownAliases() {
        assertEquals("com.tencent.mm", AppResolver.resolve("微信"));
        assertEquals("com.zhihu.android", AppResolver.resolve("知乎"));
        assertEquals("com.sankuai.meituan", AppResolver.resolve("美团"));
        assertEquals("com.cainiao.wireless", AppResolver.resolve("菜鸟"));
    }

    @Test
    public void testUnknownReturnsInput() {
        assertEquals("unknown.app", AppResolver.resolve("unknown.app"));
    }

    @Test
    public void testCaseSensitivity() {
        assertEquals("com.tencent.mm", AppResolver.resolve("微信"));
        assertEquals("WeChat", AppResolver.resolve("WeChat"));
    }
}
