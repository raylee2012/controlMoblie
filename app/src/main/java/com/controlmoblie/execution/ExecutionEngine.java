package com.controlmoblie.execution;

import com.controlmoblie.model.Action;
import com.controlmoblie.service.ControlAccessibilityService;

import java.util.ArrayList;
import java.util.List;

public class ExecutionEngine {

    public static class ExecutionResult {
        public final boolean success;
        public final String message;

        public ExecutionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    public interface OnExecutionResultCallback {
        void onResult(ExecutionResult result);
    }

    public interface OnExecutionSequenceCompleteCallback {
        void onComplete(List<ExecutionResult> results);
    }

    public void execute(Action action, OnExecutionResultCallback callback) {
        ControlAccessibilityService service = ControlAccessibilityService.getInstance();
        if (service == null) {
            if (callback != null) {
                callback.onResult(new ExecutionResult(false, "无障碍服务未连接"));
            }
            return;
        }
        service.executeAction(action, new ControlAccessibilityService.OnActionResultCallback() {
            @Override
            public void onResult(boolean success, String msg) {
                if (callback != null) {
                    callback.onResult(new ExecutionResult(success, msg));
                }
            }
        });
    }

    public void executeSequence(List<Action> actions, OnExecutionSequenceCompleteCallback callback) {
        final List<ExecutionResult> results = new ArrayList<>();
        runNext(actions, 0, results, callback);
    }

    private void runNext(List<Action> actions, int index, List<ExecutionResult> results, OnExecutionSequenceCompleteCallback callback) {
        if (index >= actions.size()) {
            if (callback != null) callback.onComplete(results);
            return;
        }
        execute(actions.get(index), result -> {
            results.add(result);
            runNext(actions, index + 1, results, callback);
        });
    }
}
