package com.controlmoblie.model;

public class InstructionResult {
    private final boolean success;
    private final String message;
    private final Action action;

    public InstructionResult(boolean success, String message, Action action) {
        this.success = success;
        this.message = message;
        this.action = action;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Action getAction() { return action; }
}
