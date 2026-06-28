package com.dentalwings.approvalbot.ui;

import java.util.Arrays;

public record IdentityAvatar(byte[] bytes, String contentType, boolean cacheHit) {
    public IdentityAvatar {
        bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
        contentType = contentType == null || contentType.isBlank() ? "image/png" : contentType;
    }

    public IdentityAvatar(byte[] bytes, boolean cacheHit) {
        this(bytes, "image/png", cacheHit);
    }
}
