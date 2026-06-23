package com.company.approvalbot.workflow;

import com.company.approvalbot.identity.EmailNormalizer;
import java.util.Optional;
import java.util.regex.Pattern;

public class ApprovalValueParser {

    private static final Pattern BRACKETED_EMAIL = Pattern.compile(".*<([^<>]+)>.*");

    private final EmailNormalizer emailNormalizer;

    public ApprovalValueParser(EmailNormalizer emailNormalizer) {
        this.emailNormalizer = emailNormalizer;
    }

    public Optional<String> extractEmail(Object approvalValue) {
        if (approvalValue == null) {
            return Optional.empty();
        }
        var text = approvalValue.toString();
        var matcher = BRACKETED_EMAIL.matcher(text);
        if (matcher.matches()) {
            return emailNormalizer.normalize(matcher.group(1));
        }
        return Optional.empty();
    }
}
