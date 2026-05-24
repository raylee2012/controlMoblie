package com.controlmoblie.llm;

import com.controlmoblie.model.Action;
import com.controlmoblie.model.NavType;
import com.controlmoblie.model.ScrollDirection;
import com.controlmoblie.model.ScrollDistance;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstructionParser {

    private static final Pattern STRING_FIELD_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern LONG_FIELD_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+)");

    public static Action parse(String rawJson) {
        String trimmed = rawJson.trim();

        if (trimmed.startsWith("<|im_start|>")) {
            int nlIdx = trimmed.indexOf('\n');
            if (nlIdx > 0) {
                trimmed = trimmed.substring(nlIdx + 1).trim();
            }
        }
        if (trimmed.startsWith("assistant")) {
            trimmed = trimmed.substring("assistant".length()).trim();
        }

        int jsonStart = trimmed.indexOf('{');
        if (jsonStart > 0) {
            trimmed = trimmed.substring(jsonStart).trim();
        }
        int jsonEnd = trimmed.lastIndexOf('}');
        if (jsonEnd >= 0 && jsonEnd < trimmed.length() - 1) {
            trimmed = trimmed.substring(0, jsonEnd + 1).trim();
        }

        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline).trim();
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }

        String actionType = extractStringField(trimmed, "action");
        if (actionType.isEmpty() || actionType.equals("error")) {
            return new Action.Click("error");
        }
        Action action = parseAction(trimmed);
        return action != null ? action : new Action.Click("error");
    }

    private static String extractStringField(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static long extractLongField(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : 1000;
    }

    private static List<String> extractStepObjects(String json) {
        List<String> steps = new ArrayList<>();
        int stepsIdx = json.indexOf("\"steps\"");
        if (stepsIdx < 0) return steps;
        int bracketStart = json.indexOf('[', stepsIdx);
        int bracketEnd = json.lastIndexOf(']');
        if (bracketStart < 0 || bracketEnd < bracketStart) return steps;
        String content = json.substring(bracketStart + 1, bracketEnd);
        int depth = 0;
        int objStart = -1;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    steps.add(content.substring(objStart, i + 1));
                    objStart = -1;
                }
            }
        }
        return steps;
    }

    private static Action parseAction(String json) {
        String actionType = extractStringField(json, "action");
        if (actionType.isEmpty()) return null;
        try {
            switch (actionType) {
                case "click":
                    return new Action.Click(extractStringField(json, "target"));
                case "open_app":
                    return new Action.OpenApp(
                        extractStringField(json, "package"),
                        extractStringField(json, "displayName")
                    );
                case "open_wechat_page":
                    return new Action.OpenWeChatPage(extractStringField(json, "page"));
                case "navigate":
                    NavType navType;
                    try {
                        navType = NavType.valueOf(extractStringField(json, "type").toUpperCase());
                    } catch (Exception e) {
                        return null;
                    }
                    return new Action.Navigate(navType);
                case "scroll":
                    ScrollDirection direction;
                    try {
                        direction = ScrollDirection.valueOf(extractStringField(json, "direction").toUpperCase());
                    } catch (Exception e) {
                        return null;
                    }
                    ScrollDistance distance;
                    try {
                        distance = ScrollDistance.valueOf(extractStringField(json, "distance").toUpperCase());
                    } catch (Exception e) {
                        distance = ScrollDistance.HALF;
                    }
                    return new Action.Scroll(direction, distance);
                case "type":
                    return new Action.Type(extractStringField(json, "text"));
                case "wait":
                    return new Action.Wait(extractLongField(json, "ms"));
                case "sequence":
                    List<String> stepJsons = extractStepObjects(json);
                    if (!stepJsons.isEmpty()) {
                        List<Action> actions = new ArrayList<>();
                        for (String stepJson : stepJsons) {
                            Action stepAction = parseAction(stepJson);
                            if (stepAction != null) {
                                actions.add(stepAction);
                            }
                        }
                        return new Action.Sequence(actions);
                    }
                    return null;
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static String buildPrompt(String userText, String screenContext) {
        String systemMsg = screenContext != null && !screenContext.isEmpty()
            ? "你是手机助手，将语音指令转为JSON操作。屏幕:" + screenContext
            : "你是手机助手，将语音指令转为JSON操作。";
        return "<|im_start|>system\n" + systemMsg + "\nп\n<|im_start|>user\n" + userText + "\nп\n<|im_start|>assistant\n";
    }
}
