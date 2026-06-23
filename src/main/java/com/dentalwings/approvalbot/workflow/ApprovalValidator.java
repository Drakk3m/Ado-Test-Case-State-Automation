package com.dentalwings.approvalbot.workflow;

import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.identity.EmailNormalizer;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ApprovalValidator {

    private final ApprovalValueParser approvalValueParser;
    private final EmailNormalizer emailNormalizer;

    public ApprovalValidator(ApprovalValueParser approvalValueParser, EmailNormalizer emailNormalizer) {
        this.approvalValueParser = approvalValueParser;
        this.emailNormalizer = emailNormalizer;
    }

    public ApprovalValidation validate(Map<String, Object> fields, ProjectApprovalConfig config) {
        var smeEmail = approvalValueParser.extractEmail(fields.get(config.approvedBySmeField()));
        var sqaEmail = approvalValueParser.extractEmail(fields.get(config.approvedBySqaField()));
        var smeUsers = normalized(config.smeUsers());
        var sqaUsers = normalized(config.sqaUsers());

        return new ApprovalValidation(
                smeEmail,
                sqaEmail,
                smeEmail.filter(smeUsers::contains).isPresent(),
                sqaEmail.filter(sqaUsers::contains).isPresent()
        );
    }

    private Set<String> normalized(Set<String> emails) {
        return emails.stream()
                .flatMap(email -> emailNormalizer.normalize(email).stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
