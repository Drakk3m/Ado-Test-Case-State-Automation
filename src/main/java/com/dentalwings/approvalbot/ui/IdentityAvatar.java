package com.dentalwings.approvalbot.ui;

import java.util.Arrays;

public record IdentityAvatar(byte[] bytes, boolean cacheHit) {
    public IdentityAvatar {
        bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
    }
}
