package com.controlmoblie.llm;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class CommandTemplates {

    private static class Template {
        final List<String> keywords;
        final java.util.function.Function<String, Map<String, String>> extract;
        final java.util.function.Function<Map<String, String>, String> buildJson;

        Template(List<String> keywords,
                 java.util.function.Function<String, Map<String, String>> extract,
                 java.util.function.Function<Map<String, String>, String> buildJson) {
            this.keywords = keywords;
            this.extract = extract;
            this.buildJson = buildJson;
        }
    }

    private static final List<Template> TEMPLATES = Arrays.asList(
        new Template(
            Arrays.asList("发消息给", "发消息"),
            CommandTemplates::extractSendMessage,
            p -> buildSequenceJson(
                waitStep(500), clickStep("通讯录"), waitStep(300),
                clickStep(p.getOrDefault("target", "")), waitStep(300),
                typeStep(p.getOrDefault("text", "")), waitStep(300),
                clickStep("发送")
            )
        ),
        new Template(
            Arrays.asList("看朋友圈"),
            input -> new HashMap<>(),
            p -> "{\"action\":\"open_wechat_page\",\"page\":\"moments\"}"
        ),
        new Template(
            Arrays.asList("发朋友圈"),
            input -> {
                String text = input.substring(input.indexOf("发朋友圈") + 4).trim();
                if (text.startsWith("说")) text = text.substring(1).trim();
                if (text.isEmpty()) return null;
                Map<String, String> map = new HashMap<>();
                map.put("text", text);
                return map;
            },
            p -> buildSequenceJson(
                waitStep(500), clickStep("发现"), waitStep(300),
                clickStep("朋友圈"), waitStep(500),
                clickStep("拍照分享"), waitStep(300),
                typeStep(p.getOrDefault("text", "")), waitStep(300),
                clickStep("发表")
            )
        ),
        new Template(
            Arrays.asList("打开小程序"),
            input -> {
                String name = input.substring(input.indexOf("打开小程序") + 4).trim();
                if (name.isEmpty()) return null;
                Map<String, String> map = new HashMap<>();
                map.put("name", name);
                return map;
            },
            p -> buildSequenceJson(
                waitStep(500),
                scrollStep("down", "short"),
                waitStep(300), clickStep(p.getOrDefault("name", ""))
            )
        ),
        new Template(
            Arrays.asList("搜索公众号"),
            input -> {
                String name = input.substring(input.indexOf("搜索公众号") + 4).trim();
                if (name.isEmpty()) return null;
                Map<String, String> map = new HashMap<>();
                map.put("name", name);
                return map;
            },
            p -> buildSequenceJson(
                waitStep(500), clickStep("通讯录"), waitStep(300),
                clickStep("公众号"), waitStep(300),
                typeStep(p.getOrDefault("name", "")), waitStep(500),
                clickStep(p.getOrDefault("name", ""))
            )
        ),
        new Template(
            Arrays.asList("的朋友圈"),
            input -> {
                int idx = input.indexOf("的朋友圈");
                if (idx < 0) return null;
                String target = input.substring(0, idx).trim();
                if (target.startsWith("看")) target = target.substring(1).trim();
                if (target.isEmpty()) return null;
                Map<String, String> map = new HashMap<>();
                map.put("target", target);
                return map;
            },
            p -> buildSequenceJson(
                waitStep(500), clickStep("通讯录"), waitStep(300),
                clickStep(p.getOrDefault("target", "")), waitStep(300),
                clickStep("朋友圈")
            )
        )
    );

    private static Map<String, String> extractSendMessage(String input) {
        if (input.contains("发消息给") && input.contains("说")) {
            String after = input.substring(input.indexOf("发消息给") + 4).trim();
            int sayIdx = after.indexOf("说");
            if (sayIdx < 0) return null;
            String target = after.substring(0, sayIdx).trim();
            String text = after.substring(sayIdx + 1).trim();
            if (target.isEmpty() || text.isEmpty()) return null;
            Map<String, String> map = new HashMap<>();
            map.put("target", target);
            map.put("text", text);
            return map;
        }
        if (input.contains("给") && input.contains("发消息")) {
            String afterGei = input.substring(input.indexOf("给") + 1).trim();
            int msgIdx = afterGei.indexOf("发消息");
            if (msgIdx < 0) return null;
            String target = afterGei.substring(0, msgIdx).trim();
            String text = afterGei.substring(msgIdx + 3).trim();
            if (target.isEmpty() || text.isEmpty()) return null;
            Map<String, String> map = new HashMap<>();
            map.put("target", target);
            map.put("text", text);
            return map;
        }
        return null;
    }

    public static String match(String userText) {
        for (Template tpl : TEMPLATES) {
            boolean matched = false;
            for (String kw : tpl.keywords) {
                if (userText.contains(kw)) {
                    matched = true;
                    break;
                }
            }
            if (matched) {
                Map<String, String> params = tpl.extract.apply(userText);
                if (params == null) continue;
                return tpl.buildJson.apply(params);
            }
        }
        return null;
    }

    private static String clickStep(String target) {
        return "{\"action\":\"click\",\"target\":\"" + escapeJson(target) + "\"}";
    }

    private static String typeStep(String text) {
        return "{\"action\":\"type\",\"text\":\"" + escapeJson(text) + "\"}";
    }

    private static String waitStep(long ms) {
        return "{\"action\":\"wait\",\"ms\":" + ms + "}";
    }

    private static String scrollStep(String direction, String distance) {
        return "{\"action\":\"scroll\",\"direction\":\"" + escapeJson(direction)
            + "\",\"distance\":\"" + escapeJson(distance) + "\"}";
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private static String buildSequenceJson(String... steps) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"action\":\"sequence\",\"steps\":[");
        for (int i = 0; i < steps.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(steps[i]);
        }
        sb.append("]}");
        return sb.toString();
    }
}
