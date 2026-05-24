package com.controlmoblie.model;

public abstract class Action {
    public enum ActionType { CLICK, SCROLL, OPEN_APP, OPEN_WECHAT_PAGE, NAVIGATE, SEQUENCE, TYPE, WAIT }
    public abstract ActionType getType();

    public static class Click extends Action {
        private final String target;
        public Click(String target) { this.target = target; }
        @Override public ActionType getType() { return ActionType.CLICK; }
        public String getTarget() { return target; }
    }

    public static class Scroll extends Action {
        private final ScrollDirection direction;
        private final ScrollDistance distance;
        public Scroll(ScrollDirection direction, ScrollDistance distance) {
            this.direction = direction;
            this.distance = distance;
        }
        @Override public ActionType getType() { return ActionType.SCROLL; }
        public ScrollDirection getDirection() { return direction; }
        public ScrollDistance getDistance() { return distance; }
    }

    public static class OpenApp extends Action {
        private final String packageName;
        private final String displayName;
        public OpenApp(String packageName, String displayName) {
            this.packageName = packageName;
            this.displayName = displayName;
        }
        @Override public ActionType getType() { return ActionType.OPEN_APP; }
        public String getPackageName() { return packageName; }
        public String getPackage() { return packageName; }
        public String getDisplayName() { return displayName; }
    }

    public static class OpenWeChatPage extends Action {
        private final String page;
        public OpenWeChatPage(String page) { this.page = page; }
        @Override public ActionType getType() { return ActionType.OPEN_WECHAT_PAGE; }
        public String getPage() { return page; }
    }

    public static class Navigate extends Action {
        private final NavType type;
        public Navigate(NavType type) { this.type = type; }
        @Override public ActionType getType() { return ActionType.NAVIGATE; }
        public NavType getNavType() { return type; }
    }

    public static class Sequence extends Action {
        private final java.util.List<Action> actions;
        public Sequence(java.util.List<Action> actions) { this.actions = actions; }
        @Override public ActionType getType() { return ActionType.SEQUENCE; }
        public java.util.List<Action> getActions() { return actions; }
        public java.util.List<Action> getSteps() { return actions; }
    }

    public static class Type extends Action {
        private final String text;
        public Type(String text) { this.text = text; }
        @Override public ActionType getType() { return ActionType.TYPE; }
        public String getText() { return text; }
    }

    public static class Wait extends Action {
        private final long ms;
        public Wait(long ms) { this.ms = ms; }
        @Override public ActionType getType() { return ActionType.WAIT; }
        public long getMs() { return ms; }
    }
}
