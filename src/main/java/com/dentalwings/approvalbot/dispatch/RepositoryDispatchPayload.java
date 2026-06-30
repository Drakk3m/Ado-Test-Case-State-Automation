package com.dentalwings.approvalbot.dispatch;

import java.util.ArrayList;
import java.util.List;

import com.dentalwings.approvalbot.event.AdoWorkItemEventParser;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RepositoryDispatchPayload(
        String source,
        String organization,
        String project,
        Long workItemId,
        Integer revision,
        String eventType,
        ChangedBy changedBy,
        String resourceUrl,
        String subscriptionId,
        String deliveryId)
{

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChangedBy(String displayName, String uniqueName)
    {
    }

    List<String> validationErrors()
    {
        var errors = new ArrayList<String>();
        requireText(source, "source", errors);
        requireText(organization, "organization", errors);
        requireText(project, "project", errors);
        requirePositive(workItemId, "workItemId", errors);
        requirePositive(revision, "revision", errors);
        requireText(eventType, "eventType", errors);
        if (eventType != null && !eventType.isBlank() && !AdoWorkItemEventParser.WORK_ITEM_UPDATED.equals(eventType))
        {
            errors.add("'eventType' must be '" + AdoWorkItemEventParser.WORK_ITEM_UPDATED + "'");
        }
        return errors;
    }

    private static void requireText(String value, String fieldName, List<String> errors)
    {
        if (value == null || value.isBlank())
        {
            errors.add("'" + fieldName + "' is required");
        }
    }

    private static void requirePositive(Number value, String fieldName, List<String> errors)
    {
        if (value == null)
        {
            errors.add("'" + fieldName + "' is required");
            return;
        }
        if (value.longValue() <= 0)
        {
            errors.add("'" + fieldName + "' must be greater than 0");
        }
    }
}

