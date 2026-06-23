package com.dentalwings.approvalbot.webhook;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record AdoWebhookResource(
        String project,
        Long workItemId,
        String workItemType,
        Integer revision,
        String changedByDisplayName,
        String changedByEmailOrLogin,
        Set<String> changedFieldNames
) {

    public AdoWebhookResource {
        changedFieldNames = changedFieldNames == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(changedFieldNames));
    }
}
