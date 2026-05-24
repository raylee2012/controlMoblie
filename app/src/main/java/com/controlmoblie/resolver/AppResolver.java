package com.controlmoblie.resolver;

import java.util.Map;
import java.util.HashMap;

public class AppResolver {
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        // ===== 系统应用 =====
        ALIASES.put("设置", "com.android.settings");
        ALIASES.put("相机", "com.android.camera");
        ALIASES.put("日历", "com.android.calendar");
        ALIASES.put("时钟", "com.android.deskclock");
        ALIASES.put("闹钟", "com.android.deskclock");
        ALIASES.put("计算器", "com.miui.calculator");
        ALIASES.put("天气", "com.miui.weather2");
        ALIASES.put("相册", "com.miui.gallery");
        ALIASES.put("文件", "com.android.fileexplorer");
        ALIASES.put("浏览器", "com.android.browser");
        ALIASES.put("小米浏览器", "com.miui.browser");
        ALIASES.put("电话", "com.android.dialer");
        ALIASES.put("拨号", "com.android.dialer");
        ALIASES.put("短信", "com.android.mms");
        ALIASES.put("联系人", "com.android.contacts");
        ALIASES.put("邮件", "com.android.email");
        ALIASES.put("邮箱", "com.android.email");
        ALIASES.put("下载", "com.android.providers.downloads.ui");
        ALIASES.put("录音机", "com.android.soundrecorder");
        ALIASES.put("应用商店", "com.xiaomi.market");
        ALIASES.put("米家", "com.xiaomi.smarthome");
        ALIASES.put("扫一扫", "com.xiaomi.scanner");
        ALIASES.put("扫", "com.xiaomi.scanner");
        ALIASES.put("便签", "com.miui.notes");
        ALIASES.put("笔记", "com.miui.notes");
        ALIASES.put("指南针", "com.miui.compass");
        ALIASES.put("手机管家", "com.miui.cleanmaster");
        ALIASES.put("屏幕录制", "com.miui.screenrecorder");
        ALIASES.put("主题商店", "com.miui.themestore");
        ALIASES.put("小米换机", "com.miui.huanji");
        ALIASES.put("小米相册编辑", "com.miui.mediaeditor");
        ALIASES.put("小米云盘", "com.miui.newmidrive");
        ALIASES.put("小米画报", "com.mfashiongallery.emag");
        ALIASES.put("小米运动健康", "com.mi.health");
        ALIASES.put("万能遥控", "com.duokan.phone.remotecontroller");
        ALIASES.put("讯飞输入法", "com.iflytek.inputmethod.miui");
        ALIASES.put("小爱同学", "com.xiaomi.mibrain.speech");

        // ===== 腾讯系 =====
        ALIASES.put("微信", "com.tencent.mm");
        ALIASES.put("qq", "com.tencent.mobileqq");
        ALIASES.put("qq音乐", "com.tencent.qqmusic");
        ALIASES.put("qq邮箱", "com.tencent.androidqqmail");
        ALIASES.put("qq浏览器", "com.tencent.mtt");
        ALIASES.put("腾讯会议", "com.tencent.wemeet.app");
        ALIASES.put("微信读书", "com.tencent.weread");
        ALIASES.put("企业微信", "com.tencent.wework");
        ALIASES.put("企业号", "com.tencent.wework");

        // ===== 阿里系 =====
        ALIASES.put("支付宝", "com.eg.android.AlipayGphone");
        ALIASES.put("淘宝", "com.taobao.taobao");
        ALIASES.put("钉钉", "com.alibaba.android.rimet");
        ALIASES.put("闲鱼", "com.taobao.idlefish");
        ALIASES.put("菜鸟", "com.cainiao.wireless");

        // ===== 字节系 =====
        ALIASES.put("抖音", "com.ss.android.ugc.aweme");
        ALIASES.put("今日头条", "com.ss.android.article.news");
        ALIASES.put("新闻", "com.ss.android.article.news");
        ALIASES.put("飞书", "com.ss.android.lark");

        // ===== 百度系 =====
        ALIASES.put("百度", "com.baidu.searchbox");
        ALIASES.put("百度地图", "com.baidu.BaiduMap");
        ALIASES.put("百度网盘", "com.baidu.netdisk");

        // ===== 网易系 =====
        ALIASES.put("网易云音乐", "com.netease.cloudmusic");
        ALIASES.put("音乐", "com.netease.cloudmusic");
        ALIASES.put("网易有道词典", "com.youdao.dict");

        // ===== 视频/娱乐 =====
        ALIASES.put("哔哩哔哩", "tv.danmaku.bili");
        ALIASES.put("b站", "tv.danmaku.bili");
        ALIASES.put("bili", "tv.danmaku.bili");
        ALIASES.put("快手", "com.smile.gifmaker");
        ALIASES.put("优酷", "com.youku.phone");
        ALIASES.put("小红书", "com.xingin.xhs");
        ALIASES.put("微博", "com.sina.weibo");
        ALIASES.put("即刻", "com.ruguoapp.jike");
        ALIASES.put("豆瓣", "com.douban.frodo");
        ALIASES.put("看理想", "com.kanlixiang.android");
        ALIASES.put("西西弗", "com.sisyphe.mobile");

        // ===== 出行 =====
        ALIASES.put("高德地图", "com.autonavi.minimap");
        ALIASES.put("地图", "com.autonavi.minimap");
        ALIASES.put("滴滴", "com.sdu.didi.psnger");
        ALIASES.put("打车", "com.sdu.didi.psnger");
        ALIASES.put("美团", "com.sankuai.meituan");
        ALIASES.put("外卖", "com.sankuai.meituan");
        ALIASES.put("饿了么", "me.ele");
        ALIASES.put("12306", "com.MobileTicket");
        ALIASES.put("火车票", "com.MobileTicket");
        ALIASES.put("铁路", "com.MobileTicket");
        ALIASES.put("交管12123", "com.tmri.app.main");
        ALIASES.put("日产智联", "com.szlanyou.nissaniov");

        // ===== 购物 =====
        ALIASES.put("京东", "com.jingdong.app.mall");
        ALIASES.put("拼多多", "com.xunmeng.pinduoduo");

        // ===== 金融 =====
        ALIASES.put("招商银行", "cmb.pb");
        ALIASES.put("工商银行", "com.icbc");
        ALIASES.put("中信银行", "com.ecitic.bank.mobile");
        ALIASES.put("个人所得税", "cn.gov.tax.its");

        // ===== 工具/效率 =====
        ALIASES.put("知乎", "com.zhihu.android");
        ALIASES.put("WPS", "cn.wps.moffice_eng");
        ALIASES.put("wps", "cn.wps.moffice_eng");
        ALIASES.put("向日葵", "com.oray.sunlogin");
        ALIASES.put("夸克扫描", "com.quark.scanking");
        ALIASES.put("V2RayNG", "com.v2ray.ang");
        ALIASES.put("DeepSeek", "com.example.deepseekchat");
        ALIASES.put("ChatGPT", "com.openai.chatgpt");
        ALIASES.put("GitHub", "com.github.android");
        ALIASES.put("Bing", "com.microsoft.bing");
        ALIASES.put("CSDN", "net.csdn.csdnplus");
        ALIASES.put("力扣", "com.lingkou.leetcode");
        ALIASES.put("Coursera", "org.coursera.android");
        ALIASES.put("维基百科", "org.wikipedia");
        ALIASES.put("YouTube", "com.google.android.youtube");
        ALIASES.put("Instagram", "com.instagram.android");
        ALIASES.put("Threads", "com.instagram.barcelona");

        // ===== 招聘 =====
        ALIASES.put("Boss直聘", "com.hpbr.bosszhipin");

        // ===== 运营商 =====
        ALIASES.put("中国联通", "com.sinovatech.unicom.ui");
    }

    public static String resolve(String name) {
        String result = ALIASES.get(name);
        return result != null ? result : name;
    }
}
