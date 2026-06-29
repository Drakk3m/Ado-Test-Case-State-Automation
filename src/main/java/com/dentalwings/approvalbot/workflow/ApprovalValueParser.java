package com.dentalwings.approvalbot.workflow;

import com.dentalwings.approvalbot.ado.AdoIdentity;
import com.dentalwings.approvalbot.identity.EmailNormalizer;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class ApprovalValueParser {

    private static final Pattern BRACKETED_EMAIL = Pattern.compile(".*<([^<>]+)>.*");
    private static final Pattern BARE_EMAIL = Pattern.compile("[^\\s<>@]+@[^\\s<>@]+");

    private final EmailNormalizer emailNormalizer;

    public ApprovalValueParser(EmailNormalizer emailNormalizer) {
        this.emailNormalizer = emailNormalizer;
    }

    public Optional<String> extractEmail(Object approvalValue) {
        if (approvalValue == null) {
            return Optional.empty();
        }
        if (approvalValue instanceof AdoIdentity identity) {
            return emailNormalizer.normalize(identity.emailOrLogin());
        }
        if (approvalValue instanceof Map<?, ?> values) {
            return firstExtractableEmail(values.get("uniqueName"), values.get("email"), values.get("mailAddress"));
        }
        var text = approvalValue.toString();
        var matcher = BRACKETED_EMAIL.matcher(text);
        if (matcher.matches()) {
            return emailNormalizer.normalize(matcher.group(1));
        }
        if (BARE_EMAIL.matcher(text.trim()).matches()) {
            return emailNormalizer.normalize(text);
        }
        return Optional.empty();
    }

    private Optional<String> firstExtractableEmail(Object... values) {
        for (Object value : values) {
            var email = value == null ? Optional.<String> empty() : emailNormalizer.normalize(value.toString());
            if (email.isPresent()) {
                return email;
            }
        }
        return Optional.empty();
    }
}
