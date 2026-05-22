package com.controlmoblie.llm

import org.junit.Assert.*
import org.junit.Test

class CommandTemplatesTest {

    @Test
    fun `发消息给张三说你好 returns sequence JSON`() {
        val json = CommandTemplates.match("发消息给张三说你好")
        assertNotNull("should match 发消息给 template", json)
        assertTrue("should contain 通讯录 step", json!!.contains("通讯录"))
        assertTrue("should contain target name", json.contains("张三"))
        assertTrue("should contain message text", json.contains("你好"))
        assertTrue("should contain 发送 step", json.contains("发送"))
        assertTrue("should be sequence action", json.contains("\"action\":\"sequence\""))
    }

    @Test
    fun `给李四发消息今天开会 matches send template`() {
        val json = CommandTemplates.match("给李四发消息今天开会")
        assertNotNull("should match 给...发消息 pattern", json)
        assertTrue("should contain target 李四", json!!.contains("李四"))
        assertTrue("should contain text 今天开会", json.contains("今天开会"))
    }

    @Test
    fun `看朋友圈 returns sequence JSON`() {
        val json = CommandTemplates.match("看朋友圈")
        assertNotNull("should match 看朋友圈 template", json)
        assertTrue("should contain 发现 step", json!!.contains("发现"))
        assertTrue("should contain 朋友圈 step", json.contains("朋友圈"))
    }

    @Test
    fun `发朋友圈今天天气不错 matches post template`() {
        val json = CommandTemplates.match("发朋友圈今天天气不错")
        assertNotNull(json)
        assertTrue(json!!.contains("发表"))
        assertTrue(json.contains("今天天气不错"))
    }

    @Test
    fun `打开小程序京东 matches mini program template`() {
        val json = CommandTemplates.match("打开小程序京东")
        assertNotNull(json)
        assertTrue(json!!.contains("京东"))
    }

    @Test
    fun `搜索公众号人民日报 matches official account template`() {
        val json = CommandTemplates.match("搜索公众号人民日报")
        assertNotNull(json)
        assertTrue(json!!.contains("人民日报"))
    }

    @Test
    fun `看李四的朋友圈 matches view contact moments template`() {
        val json = CommandTemplates.match("看李四的朋友圈")
        assertNotNull(json)
        assertTrue(json!!.contains("李四"))
    }

    @Test
    fun `返回 command does NOT match templates`() {
        val json = CommandTemplates.match("返回")
        assertNull("should return null for non-WeChat commands", json)
    }

    @Test
    fun `点击设置 does NOT match templates`() {
        val json = CommandTemplates.match("点击设置")
        assertNull("should return null for generic commands", json)
    }

    @Test
    fun `发消息给 with no content returns null`() {
        val json = CommandTemplates.match("发消息给")
        assertNull("should return null when missing parameters", json)
    }
}
