package com.controlmoblie.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.controlmoblie.model.Action;
import com.controlmoblie.model.InstructionResult;
import com.controlmoblie.model.NavType;
import com.controlmoblie.model.ScreenState;
import com.controlmoblie.model.ScrollDirection;
import com.controlmoblie.model.ScrollDistance;
import com.controlmoblie.ocr.ScreenCaptureManager;
import com.controlmoblie.ocr.ScreenOcr;
import com.controlmoblie.resolver.AppResolver;
import com.controlmoblie.service.binder.AccessibilityBinder;
import com.controlmoblie.util.ScreenReader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ControlAccessibilityService extends AccessibilityService {

    private static final String TAG = "ControlAccessibility";
    private static final long SCROLL_GESTURE_DURATION_MS = 200L;
    private static final long SEQUENCE_STEP_DELAY_MS = 200L;
    private static final int SCROLL_SHORT_OFFSET_PX = 200;

    private static ControlAccessibilityService instance;

    private ScreenState lastScreenState = new ScreenState("", new java.util.ArrayList<>());
    private final AccessibilityBinder binder = new AccessibilityBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    static final Map<String, String> WECHAT_SCHEMES = new HashMap<String, String>() {{
        put("公众号", "weixin://dl/officialaccounts");
        put("订阅号", "weixin://dl/officialaccounts");
        put("朋友圈", "weixin://dl/moments");
        put("扫一扫", "weixin://dl/scan");
        put("设置", "weixin://dl/settings");
    }};

    private static final Map<String, Integer> WECHAT_TAB_POSITIONS = new HashMap<String, Integer>() {{
        put("微信", 0);
        put("聊天", 0);
        put("通讯录", 1);
        put("联系人", 1);
        put("发现", 2);
        put("我", 3);
        put("我的", 3);
    }};

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        ScreenOcr.init();
        Log.d(TAG, "Accessibility service connected, OCR isReady=" + ScreenOcr.isReady());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.d(TAG, "Accessibility service destroyed");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
            updateScreenState();
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Service interrupted");
    }

    private void updateScreenState() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.w(TAG, "updateScreenState: root is null");
            return;
        }
        lastScreenState = ScreenReader.readScreen(root);
        root.recycle();
        binder.setScreenState(lastScreenState);
        Log.d(TAG, "updateScreenState: collected " + lastScreenState.getTexts().size() + " texts");
    }

    public ScreenState getLastScreenState() {
        return lastScreenState;
    }

    public static ControlAccessibilityService getInstance() {
        return instance;
    }

    public AccessibilityBinder getBinder() {
        return binder;
    }

    public void executeAction(Action action, OnActionResultCallback callback) {
        Log.d(TAG, "executeAction: " + action.getType());
        switch (action.getType()) {
            case CLICK:
                executeClick((Action.Click) action, callback);
                break;
            case OPEN_APP:
                executeOpenApp((Action.OpenApp) action, callback);
                break;
            case NAVIGATE:
                executeNavigate((Action.Navigate) action, callback);
                break;
            case SCROLL:
                executeScroll((Action.Scroll) action, callback);
                break;
            case TYPE:
                executeType((Action.Type) action, callback);
                break;
            case WAIT:
                Action.Wait waitAction = (Action.Wait) action;
                Log.d(TAG, "executeAction: wait " + waitAction.getMs() + "ms");
                mainHandler.postDelayed(() -> {
                    if (callback != null) callback.onResult(true, "waited " + waitAction.getMs() + "ms");
                }, waitAction.getMs());
                break;
            case OPEN_WECHAT_PAGE:
                executeOpenWeChatPage((Action.OpenWeChatPage) action, callback);
                break;
            case SEQUENCE:
                executeSequence((Action.Sequence) action, callback);
                break;
            default:
                if (callback != null) callback.onResult(false, "未知操作类型");
        }
    }

    private AccessibilityNodeInfo findClickableByText(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        Log.d(TAG, "findClickableByText: '" + text + "' found " + nodes.size() + " text matches");

        AccessibilityNodeInfo result = null;
        for (AccessibilityNodeInfo node : nodes) {
            AccessibilityNodeInfo current = node;
            while (current != null) {
                if (current.isClickable()) {
                    result = current;
                    break;
                }
                AccessibilityNodeInfo parent = current.getParent();
                if (current != node) {
                    current.recycle();
                }
                current = parent;
            }
            if (result != null) break;
        }

        // Strategy 2: MIUI fallback — return the text node itself even if not clickable
        if (result == null && !nodes.isEmpty()) {
            result = nodes.get(0);
            Log.d(TAG, "findClickableByText: MIUI fallback, returning non-clickable text node for '" + text + "'");
        }

        // Recycle other nodes
        for (AccessibilityNodeInfo node : nodes) {
            if (node != result) {
                node.recycle();
            }
        }

        // Fallback: try contentDescription search
        if (result == null) {
            result = findClickableByContentDesc(root, text);
        }

        return result;
    }

    private AccessibilityNodeInfo findClickableByContentDesc(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        if (nodes.isEmpty()) {
            Log.w(TAG, "findClickableByContentDesc: '" + text + "' - no text or contentDesc match at all");
            return null;
        }
        for (AccessibilityNodeInfo node : nodes) {
            AccessibilityNodeInfo current = node;
            while (current != null) {
                if (current.isClickable()) {
                    for (AccessibilityNodeInfo n : nodes) {
                        if (n != current) n.recycle();
                    }
                    return current;
                }
                AccessibilityNodeInfo parent = current.getParent();
                if (current != node) current.recycle();
                current = parent;
            }
        }
        for (AccessibilityNodeInfo node : nodes) {
            node.recycle();
        }
        return null;
    }

    private void performCoordinateClick(int x, int y, OnCoordinateClickResultCallback callback) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 100))
            .build();
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (callback != null) callback.onResult(true);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                if (callback != null) callback.onResult(false);
            }
        }, null);
    }

    private void executeClick(Action.Click action, OnActionResultCallback callback) {
        String target = action.getTarget();
        Log.d(TAG, "executeClick: target=" + target);

        // Strategy 0: bypass MIUI entirely by opening WeChat internal page via Intent
        String scheme = WECHAT_SCHEMES.get(target);
        if (scheme != null) {
            Log.d(TAG, "executeClick: using WeChat scheme fallback '" + scheme + "'");
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(scheme));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Log.d(TAG, "executeClick: scheme launch success");
                binder.setActionResult(new InstructionResult(true, "已打开 " + target, action));
                if (callback != null) callback.onResult(true, "已打开 " + target);
                return;
            } catch (Exception e) {
                Log.w(TAG, "executeClick: scheme launch failed", e);
                // continue to normal click flow
            }
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.w(TAG, "executeClick: no window root");
            binder.setActionResult(new InstructionResult(false, "no window root", action));
            if (callback != null) callback.onResult(false, "no window root");
            return;
        }

        AccessibilityNodeInfo clickable = findClickableByText(root, target);
        if (clickable != null) {
            boolean clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            clickable.recycle();
            Log.d(TAG, "executeClick: target=" + target + " text-match success=" + clicked);
            if (clicked) {
                root.recycle();
                binder.setActionResult(new InstructionResult(true, "已点击 " + target, action));
                if (callback != null) callback.onResult(true, "已点击 " + target);
                return;
            }
            Log.w(TAG, "executeClick: performAction returned false, continuing to fallbacks");
        }

        // Fallback 2: coordinate-based click for WeChat bottom tabs (MIUI blocks text)
        Integer tabIndex = WECHAT_TAB_POSITIONS.get(target);
        if (tabIndex != null) {
            root.recycle();
            android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
            android.graphics.Point appSize = new android.graphics.Point();
            android.graphics.Point realSize = new android.graphics.Point();
            if (wm != null && wm.getDefaultDisplay() != null) {
                wm.getDefaultDisplay().getSize(appSize);
                wm.getDefaultDisplay().getRealSize(realSize);
            }
            int navBarHeight = realSize.y - appSize.y;
            float density = getResources().getDisplayMetrics().density;
            int tabW = realSize.x / 4;
            int x = tabW * tabIndex + tabW / 2;
            int y = realSize.y - navBarHeight - (int) (4 * density);
            Log.d(TAG, "executeClick: target=" + target + " coordinate fallback x=" + x + " y=" + y);
            performCoordinateClick(x, y, clicked -> {
                binder.setActionResult(new InstructionResult(clicked, clicked ? "已点击 " + target : "无法点击 " + target, action));
                if (callback != null) callback.onResult(clicked, clicked ? "已点击 " + target : "无法点击 " + target);
            });
            return;
        }

        // Fallback 3: OCR screenshot
        root.recycle();
        if (!ScreenOcr.isReady()) {
            ScreenOcr.init();
            Log.d(TAG, "OCR lazy init: isReady=" + ScreenOcr.isReady());
        }
        if (!ScreenCaptureManager.isReady()) {
            Log.w(TAG, "ScreenCaptureManager not ready, skipping OCR fallback");
        }
        if (ScreenOcr.isReady() && ScreenCaptureManager.isReady()) {
            Log.d(TAG, "executeClick: trying OCR fallback for '" + target + "'");
            Bitmap bitmap = ScreenCaptureManager.capture();
            if (bitmap == null) {
                binder.setActionResult(new InstructionResult(false, "截屏失败", action));
                if (callback != null) callback.onResult(false, "截屏失败");
                return;
            }
            ScreenOcr.recognize(bitmap, results -> {
                ScreenOcr.OcrTextBlock match = null;
                for (ScreenOcr.OcrTextBlock block : results) {
                    if (block.text.contains(target)) {
                        match = block;
                        break;
                    }
                }
                if (match != null) {
                    Log.d(TAG, "executeClick: OCR match '" + target + "' at (" + match.x + ", " + match.y + ")");
                    performCoordinateClick((int) match.x, (int) match.y, clicked -> {
                        binder.setActionResult(new InstructionResult(clicked, clicked ? "已点击 " + target : "无法点击 " + target, action));
                        if (callback != null) callback.onResult(clicked, clicked ? "已点击 " + target : "无法点击 " + target);
                    });
                } else {
                    Log.w(TAG, "executeClick: OCR found no match for '" + target + "'");
                    binder.setActionResult(new InstructionResult(false, "未找到 " + target, action));
                    if (callback != null) callback.onResult(false, "未找到 " + target);
                }
            });
            return;
        }

        Log.w(TAG, "executeClick: target '" + target + "' not found");
        binder.setActionResult(new InstructionResult(false, "未找到 " + target, action));
        if (callback != null) callback.onResult(false, "未找到 " + target);
    }

    private void executeOpenApp(Action.OpenApp action, OnActionResultCallback callback) {
        String packageName = AppResolver.resolve(action.getPackageName());
        Log.d(TAG, "executeOpenApp: name=" + action.getPackageName() + " resolved=" + packageName);
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            Log.w(TAG, "executeOpenApp: package not found " + packageName);
            String displayName = action.getDisplayName() != null && !action.getDisplayName().isEmpty()
                ? action.getDisplayName() : action.getPackageName();
            binder.setActionResult(new InstructionResult(false, "未找到应用: " + displayName, action));
            if (callback != null) callback.onResult(false, "未找到应用: " + displayName);
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        String displayName = action.getDisplayName() != null && !action.getDisplayName().isEmpty()
            ? action.getDisplayName() : action.getPackageName();
        binder.setActionResult(new InstructionResult(true, "已打开 " + displayName, action));
        if (callback != null) callback.onResult(true, "已打开 " + displayName);
    }

    private void executeNavigate(Action.Navigate action, OnActionResultCallback callback) {
        Log.d(TAG, "executeNavigate: type=" + action.getNavType());
        int globalAction;
        switch (action.getNavType()) {
            case BACK:
                globalAction = GLOBAL_ACTION_BACK;
                break;
            case HOME:
                globalAction = GLOBAL_ACTION_HOME;
                break;
            case RECENTS:
                globalAction = GLOBAL_ACTION_RECENTS;
                break;
            default:
                globalAction = GLOBAL_ACTION_BACK;
        }
        boolean success = performGlobalAction(globalAction);
        Log.d(TAG, "executeNavigate: success=" + success);
        String msg;
        switch (action.getNavType()) {
            case BACK:
                msg = success ? "已返回" : "返回失败";
                break;
            case HOME:
                msg = success ? "已回桌面" : "回桌面失败";
                break;
            case RECENTS:
                msg = success ? "最近任务" : "切换失败";
                break;
            default:
                msg = "导航完成";
        }
        binder.setActionResult(new InstructionResult(success, msg, action));
        if (callback != null) callback.onResult(success, msg);
    }

    private void executeScroll(Action.Scroll action, OnActionResultCallback callback) {
        Log.d(TAG, "executeScroll: direction=" + action.getDirection() + " distance=" + action.getDistance());
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.w(TAG, "executeScroll: no window root");
            if (callback != null) callback.onResult(false, "no window root");
            return;
        }

        android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null || wm.getDefaultDisplay() == null) {
            Log.w(TAG, "executeScroll: no window service");
            root.recycle();
            if (callback != null) callback.onResult(false, "no window service");
            return;
        }
        android.graphics.Point size = new android.graphics.Point();
        wm.getDefaultDisplay().getSize(size);

        int fromX, fromY, toX, toY;
        int midX = size.x / 2;
        int midY = size.y / 2;
        boolean isHorizontal = action.getDirection() == ScrollDirection.LEFT || action.getDirection() == ScrollDirection.RIGHT;
        int baseSize = isHorizontal ? size.x : size.y;
        int offset;
        switch (action.getDistance()) {
            case SHORT:
                offset = SCROLL_SHORT_OFFSET_PX;
                break;
            case HALF:
                offset = baseSize / 3;
                break;
            case FULL:
                offset = baseSize * 2 / 3;
                break;
            default:
                offset = SCROLL_SHORT_OFFSET_PX;
        }

        switch (action.getDirection()) {
            case UP:
                fromX = midX; fromY = midY + offset / 2; toX = midX; toY = midY - offset / 2;
                break;
            case DOWN:
                fromX = midX; fromY = midY - offset / 2; toX = midX; toY = midY + offset / 2;
                break;
            case LEFT:
                fromX = midX + offset / 2; fromY = midY; toX = midX - offset / 2; toY = midY;
                break;
            case RIGHT:
                fromX = midX - offset / 2; fromY = midY; toX = midX + offset / 2; toY = midY;
                break;
            default:
                fromX = midX; fromY = midY; toX = midX; toY = midY;
        }

        Path path = new Path();
        path.moveTo(fromX, fromY);
        path.lineTo(toX, toY);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, SCROLL_GESTURE_DURATION_MS))
            .build();
        root.recycle();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d(TAG, "executeScroll: completed " + action.getDirection());
                String directionText;
                switch (action.getDirection()) {
                    case UP: directionText = "上滑"; break;
                    case DOWN: directionText = "下滑"; break;
                    case LEFT: directionText = "左滑"; break;
                    case RIGHT: directionText = "右滑"; break;
                    default: directionText = "滑动"; break;
                }
                binder.setActionResult(new InstructionResult(true, "已" + directionText, action));
                if (callback != null) callback.onResult(true, "已" + directionText);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.w(TAG, "executeScroll: cancelled");
                binder.setActionResult(new InstructionResult(false, "滑动失败", action));
                if (callback != null) callback.onResult(false, "滑动失败");
            }
        }, null);
    }

    private void executeType(Action.Type action, OnActionResultCallback callback) {
        Log.d(TAG, "executeType: text=" + action.getText());
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.w(TAG, "executeType: no window root");
            if (callback != null) callback.onResult(false, "no window root");
            return;
        }

        AccessibilityNodeInfo focusNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focusNode == null) {
            Log.w(TAG, "executeType: no focused input field");
            root.recycle();
            if (callback != null) callback.onResult(false, "未找到输入框");
            return;
        }

        android.os.Bundle bundle = new android.os.Bundle();
        bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.getText());
        boolean success = focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle);
        focusNode.recycle();
        root.recycle();
        Log.d(TAG, "executeType: text=" + action.getText() + " success=" + success);
        binder.setActionResult(new InstructionResult(success, success ? "已输入" : "输入失败", action));
        if (callback != null) callback.onResult(success, success ? "已输入" : "输入失败");
    }

    private void executeOpenWeChatPage(Action.OpenWeChatPage action, OnActionResultCallback callback) {
        String page = action.getPage();
        String scheme = WECHAT_SCHEMES.get(page);
        if (scheme == null) {
            scheme = "weixin://dl/" + page;
        }
        Log.d(TAG, "executeOpenWeChatPage: page=" + page + " scheme=" + scheme);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(scheme));
            intent.setPackage("com.tencent.mm");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            binder.setActionResult(new InstructionResult(true, "已打开 " + page, action));
            if (callback != null) callback.onResult(true, "已打开 " + page);
        } catch (Exception e) {
            Log.e(TAG, "executeOpenWeChatPage failed", e);
            binder.setActionResult(new InstructionResult(false, "打开失败: " + e.getMessage(), action));
            if (callback != null) callback.onResult(false, "打开失败: " + e.getMessage());
        }
    }

    private void executeSequence(Action.Sequence action, OnActionResultCallback callback) {
        List<Action> steps = action.getSteps();
        Log.d(TAG, "executeSequence: " + steps.size() + " steps");
        final java.util.List<String> results = new java.util.ArrayList<>();
        final boolean[] failed = {false};

        runNext(steps, 0, results, failed, callback);
    }

    private void runNext(List<Action> steps, int index, java.util.List<String> results, boolean[] failed, OnActionResultCallback callback) {
        if (failed[0] || index >= steps.size()) {
            String msg = android.text.TextUtils.join("; ", results);
            binder.setActionResult(new InstructionResult(!failed[0], msg, new Action.Sequence(steps)));
            if (callback != null) callback.onResult(!failed[0], msg);
            return;
        }
        executeAction(steps.get(index), (success, msg) -> {
            results.add(msg);
            if (!success) failed[0] = true;
            mainHandler.postDelayed(() -> runNext(steps, index + 1, results, failed, callback), SEQUENCE_STEP_DELAY_MS);
        });
    }

    public interface OnActionResultCallback {
        void onResult(boolean success, String message);
    }

    public interface OnCoordinateClickResultCallback {
        void onResult(boolean success);
    }
}
