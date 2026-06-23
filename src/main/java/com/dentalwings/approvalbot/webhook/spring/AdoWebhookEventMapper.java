package com.dentalwings.approvalbot.webhook.spring;

import com.dentalwings.approvalbot.webhook.AdoWebhookEvent;
import com.dentalwings.approvalbot.webhook.spring.dto.AdoServiceHookWorkItemUpdatedRequest;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AdoWebhookEventMapper {

    private static final String SYSTEM_TEAM_PROJECT = "System.TeamProject";
    private static final String SYSTEM_WORK_ITEM_TYPE = "System.WorkItemType";
    private static final String SYSTEM_CHANGED_BY = "System.ChangedBy";

    public AdoWebhookEvent toWebhookEvent(AdoServiceHookWorkItemUpdatedRequest request) {
        var resource = request == null ? null : request.resource();
        var revisionFields = resource == null || resource.revision() == null
                ? Map.<String, Object>of()
                : resource.revision().fields();
        var changedBy = changedBy(resource, revisionFields);

        return AdoWebhookEvent.workItemUpdated(
                stringValue(request == null ? null : request.organization()),
                stringValue(fieldValue(resource == null ? null : resource.project(), revisionFields.get(SYSTEM_TEAM_PROJECT))),
                resource == null ? null : resource.id(),
                stringValue(fieldValue(resource == null ? null : resource.workItemType(), revisionFields.get(SYSTEM_WORK_ITEM_TYPE))),
                revision(resource),
                changedBy.displayName(),
                changedBy.emailOrLogin(),
                changedFieldNames(resource)
        );
    }

    private Integer revision(AdoServiceHookWorkItemUpdatedRequest.Resource resource) {
        if (resource == null) {
            return null;
        }
        if (resource.rev() != null) {
            return resource.rev();
        }
        return resource.revision() == null ? null : resource.revision().rev();
    }

    private IdentityValues changedBy(
            AdoServiceHookWorkItemUpdatedRequest.Resource resource,
            Map<String, Object> revisionFields
    ) {
        var source = resource != null && resource.revisedBy() != null
                ? resource.revisedBy()
                : revisionFields.get(SYSTEM_CHANGED_BY);
        if (source instanceof AdoServiceHookWorkItemUpdatedRequest.Identity identity) {
            return new IdentityValues(identity.displayName(), firstNonBlank(identity.uniqueName(), identity.email(), identity.mailAddress()));
        }
        if (source instanceof Map<?, ?> values) {
            return new IdentityValues(
                    stringValue(values.get("displayName")),
                    firstNonBlank(
                            stringValue(values.get("uniqueName")),
                            stringValue(values.get("email")),
                            stringValue(values.get("mailAddress"))
                    )
            );
        }
        return new IdentityValues(stringValue(source), null);
    }

    private Set<String> changedFieldNames(AdoServiceHookWorkItemUpdatedRequest.Resource resource) {
        if (resource == null || resource.fields() == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(resource.fields().keySet());
    }

    private Object fieldValue(Object directValue, Object revisionFieldValue) {
        if (directValue != null) {
            return directValue;
        }
        return revisionFieldValue;
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
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private record IdentityValues(String displayName, String emailOrLogin) {
    }
}
