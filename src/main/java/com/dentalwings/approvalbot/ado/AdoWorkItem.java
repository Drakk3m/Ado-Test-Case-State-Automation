package com.dentalwings.approvalbot.ado;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AdoWorkItem(
        long id,
        String project,
        String workItemType,
        int revision,
        String state,
        Map<String, Object> fields
) {

    public AdoWorkItem {
        fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
