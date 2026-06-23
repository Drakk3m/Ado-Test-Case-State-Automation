package com.dentalwings.approvalbot.ado;

public record AdoPatchResult(boolean successful, int revision, String message) {

    public static AdoPatchResult success(int revision) {
        return new AdoPatchResult(true, revision, null);
    }

    public static AdoPatchResult failure(String message) {
        return new AdoPatchResult(false, -1, message);
    }
}
