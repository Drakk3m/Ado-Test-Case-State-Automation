package com.dentalwings.approvalbot.domain;

public record PatchOperation(String op, String path, Object value) {

    public static PatchOperation testRevision(int revision) {
        return new PatchOperation("test", "/rev", revision);
    }

    public static PatchOperation replaceField(String fieldReferenceName, Object value) {
        return new PatchOperation("replace", "/fields/" + fieldReferenceName, value);
    }
}
