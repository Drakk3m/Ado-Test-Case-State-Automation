package com.dentalwings.approvalbot.ado;

import java.util.Map;
import java.util.Set;

public record AdoWorkItemRevision(
        long workItemId,
        int revision,
        AdoIdentity changedBy,
        Map<String, Object> fields,
        Set<String> changedFieldNames
) {

    public AdoWorkItemRevision {
        fields = Map.copyOf(fields);
        changedFieldNames = Set.copyOf(changedFieldNames);
    }
}
