package com.dentalwings.approvalbot.ado;

public record AdoCommentResult(boolean successful, String commentId, String message) {

    public static AdoCommentResult success(String commentId) {
        return new AdoCommentResult(true, commentId, null);
    }

    public static AdoCommentResult failure(String message) {
        return new AdoCommentResult(false, null, message);
    }
}
