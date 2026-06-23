package com.dentalwings.approvalbot.ado;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        changedFieldNames = changedFieldNames == null ? Set.of() : Set.copyOf(changedFieldNames);
    }
}
