package com.dentalwings.approvalbot.identity;

import com.dentalwings.approvalbot.config.ProjectApprovalConfig;
import com.dentalwings.approvalbot.domain.Identity;
import java.util.Set;
import java.util.stream.Collectors;

public class IdentityClassifier {

    private final EmailNormalizer emailNormalizer;

    public IdentityClassifier(EmailNormalizer emailNormalizer) {
        this.emailNormalizer = emailNormalizer;
    }

    public IdentityClassification classify(Identity identity, ProjectApprovalConfig config) {
        if (identity == null) {
            return new IdentityClassification(false, false);
        }
        return emailNormalizer.normalize(identity.email())
                .map(email -> new IdentityClassification(normalized(config.smeUsers()).contains(email),
                        normalized(config.sqaUsers()).contains(email)))
                .orElse(new IdentityClassification(false, false));
    }

    private Set<String> normalized(Set<String> emails) {
        return emails.stream().flatMap(email -> emailNormalizer.normalize(email).stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
