package com.controlmoblie.util

object AppResolver {

    private val aliases = mapOf(
        // ===== 系统应用 =====
        "设置" to "com.android.settings",
        "相机" to "com.android.camera",
        "日历" to "com.android.calendar",
        "时钟" to "com.android.deskclock",
        "闹钟" to "com.android.deskclock",
        "计算器" to "com.miui.calculator",
        "天气" to "com.miui.weather2",
        "相册" to "com.miui.gallery",
        "文件" to "com.android.fileexplorer",
        "浏览器" to "com.android.browser",
        "小米浏览器" to "com.miui.browser",
        "电话" to "com.android.dialer",
        "拨号" to "com.android.dialer",
        "短信" to "com.android.mms",
        "联系人" to "com.android.contacts",
        "邮件" to "com.android.email",
        "邮箱" to "com.android.email",
        "下载" to "com.android.providers.downloads.ui",
        "录音机" to "com.android.soundrecorder",
        "应用商店" to "com.xiaomi.market",
        "米家" to "com.xiaomi.smarthome",
        "扫一扫" to "com.xiaomi.scanner",
        "扫" to "com.xiaomi.scanner",
        "便签" to "com.miui.notes",
        "笔记" to "com.miui.notes",
        "指南针" to "com.miui.compass",
        "手机管家" to "com.miui.cleanmaster",
        "屏幕录制" to "com.miui.screenrecorder",
        "主题商店" to "com.miui.themestore",
        "小米换机" to "com.miui.huanji",
        "小米相册编辑" to "com.miui.mediaeditor",
        "小米云盘" to "com.miui.newmidrive",
        "小米画报" to "com.mfashiongallery.emag",
        "小米运动健康" to "com.mi.health",
        "万能遥控" to "com.duokan.phone.remotecontroller",
        "讯飞输入法" to "com.iflytek.inputmethod.miui",
        "小爱同学" to "com.xiaomi.mibrain.speech",

        // ===== 腾讯系 =====
        "微信" to "com.tencent.mm",
        "qq" to "com.tencent.mobileqq",
        "qq音乐" to "com.tencent.qqmusic",
        "qq邮箱" to "com.tencent.androidqqmail",
        "qq浏览器" to "com.tencent.mtt",
        "腾讯会议" to "com.tencent.wemeet.app",
        "微信读书" to "com.tencent.weread",
        "企业微信" to "com.tencent.wework",
        "企业号" to "com.tencent.wework",

        // ===== 阿里系 =====
        "支付宝" to "com.eg.android.AlipayGphone",
        "淘宝" to "com.taobao.taobao",
        "钉钉" to "com.alibaba.android.rimet",
        "闲鱼" to "com.taobao.idlefish",
        "菜鸟" to "com.cainiao.wireless",

        // ===== 字节系 =====
        "抖音" to "com.ss.android.ugc.aweme",
        "今日头条" to "com.ss.android.article.news",
        "新闻" to "com.ss.android.article.news",
        "飞书" to "com.ss.android.lark",

        // ===== 百度系 =====
        "百度" to "com.baidu.searchbox",
        "百度地图" to "com.baidu.BaiduMap",
        "百度网盘" to "com.baidu.netdisk",

        // ===== 网易系 =====
        "网易云音乐" to "com.netease.cloudmusic",
        "音乐" to "com.netease.cloudmusic",
        "网易有道词典" to "com.youdao.dict",

        // ===== 视频/娱乐 =====
        "哔哩哔哩" to "tv.danmaku.bili",
        "b站" to "tv.danmaku.bili",
        "bili" to "tv.danmaku.bili",
        "快手" to "com.smile.gifmaker",
        "优酷" to "com.youku.phone",
        "小红书" to "com.xingin.xhs",
        "微博" to "com.sina.weibo",
        "即刻" to "com.ruguoapp.jike",
        "豆瓣" to "com.douban.frodo",
        "看理想" to "com.kanlixiang.android",
        "西西弗" to "com.sisyphe.mobile",

        // ===== 出行 =====
        "高德地图" to "com.autonavi.minimap",
        "地图" to "com.autonavi.minimap",
        "滴滴" to "com.sdu.didi.psnger",
        "打车" to "com.sdu.didi.psnger",
        "美团" to "com.sankuai.meituan",
        "外卖" to "com.sankuai.meituan",
        "饿了么" to "me.ele",
        "12306" to "com.MobileTicket",
        "火车票" to "com.MobileTicket",
        "铁路" to "com.MobileTicket",
        "交管12123" to "com.tmri.app.main",
        "日产智联" to "com.szlanyou.nissaniov",

        // ===== 购物 =====
        "京东" to "com.jingdong.app.mall",
        "拼多多" to "com.xunmeng.pinduoduo",

        // ===== 金融 =====
        "招商银行" to "cmb.pb",
        "工商银行" to "com.icbc",
        "中信银行" to "com.ecitic.bank.mobile",
        "个人所得税" to "cn.gov.tax.its",

        // ===== 工具/效率 =====
        "知乎" to "com.zhihu.android",
        "WPS" to "cn.wps.moffice_eng",
        "wps" to "cn.wps.moffice_eng",
        "向日葵" to "com.oray.sunlogin",
        "夸克扫描" to "com.quark.scanking",
        "V2RayNG" to "com.v2ray.ang",
        "DeepSeek" to "com.example.deepseekchat",
        "ChatGPT" to "com.openai.chatgpt",
        "GitHub" to "com.github.android",
        "Bing" to "com.microsoft.bing",
        "CSDN" to "net.csdn.csdnplus",
        "力扣" to "com.lingkou.leetcode",
        "Coursera" to "org.coursera.android",
        "维基百科" to "org.wikipedia",
        "YouTube" to "com.google.android.youtube",
        "Instagram" to "com.instagram.android",
        "Threads" to "com.instagram.barcelona",

        // ===== 招聘 =====
        "Boss直聘" to "com.hpbr.bosszhipin",

        // ===== 运营商 =====
        "中国联通" to "com.sinovatech.unicom.ui",
    )

    fun resolve(name: String): String {
        return aliases[name] ?: name
    }
}
