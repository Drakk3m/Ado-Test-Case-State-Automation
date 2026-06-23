package com.dentalwings.approvalbot.domain;

public record PatchOperation(String op, String path, Object value) {

    public static PatchOperation replaceField(String fieldReferenceName, Object value) {
        return new PatchOperation("replace", "/fields/" + fieldReferenceName, value);
    }
}
