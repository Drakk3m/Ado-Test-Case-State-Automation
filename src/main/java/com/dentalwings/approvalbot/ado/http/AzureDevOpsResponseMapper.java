package com.dentalwings.approvalbot.ado.http;

import com.dentalwings.approvalbot.ado.AdoIdentity;
import com.dentalwings.approvalbot.ado.AdoWorkItem;
import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.ado.AdoWorkItemRevision;
import java.util.LinkedHashMap;
import java.util.Map;

class AzureDevOpsResponseMapper {

    private static final String SYSTEM_WORK_ITEM_TYPE = "System.WorkItemType";
    private static final String SYSTEM_STATE = "System.State";
    private static final String SYSTEM_CHANGED_BY = "System.ChangedBy";

    AdoWorkItem toWorkItem(AdoWorkItemKey key, AdoRestWorkItemResponse response) {
        var fields = fields(response.fields());
        return new AdoWorkItem(
                response.id(),
                key.project(),
                stringValue(fields.get(SYSTEM_WORK_ITEM_TYPE)),
                response.rev(),
                stringValue(fields.get(SYSTEM_STATE)),
                fields
        );
    }

    AdoWorkItemRevision toRevision(AdoWorkItemKey key, AdoRestRevisionResponse response) {
        var fields = fields(response.fields());
        return new AdoWorkItemRevision(
                key.workItemId(),
                response.rev(),
                identity(fields.get(SYSTEM_CHANGED_BY)),
                fields,
                fields.keySet()
        );
    }

    private Map<String, Object> fields(Map<String, Object> fields) {
        if (fields == null) {
            return Map.of();
        }
        return new LinkedHashMap<>(fields);
    }

    private AdoIdentity identity(Object rawValue) {
        if (rawValue instanceof Map<?, ?> values) {
            return new AdoIdentity(
                    stringValue(values.get("displayName")),
                    firstNonBlank(
                            stringValue(values.get("uniqueName")),
                            stringValue(values.get("email")),
                            stringValue(values.get("mailAddress"))
                    )
            );
        }
        if (rawValue == null) {
            return null;
        }
        return new AdoIdentity(rawValue.toString(), null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
