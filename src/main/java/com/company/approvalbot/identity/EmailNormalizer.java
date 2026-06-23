package com.company.approvalbot.identity;

import java.util.Locale;
import java.util.Optional;

public class EmailNormalizer {

    public Optional<String> normalize(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(email.trim().toLowerCase(Locale.ROOT));
    }
}
