package com.dentalwings.approvalbot.event;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public final class AdoWorkItemEventParser
{

    public static final String WORK_ITEM_UPDATED = "workitem.updated";

    private static final String DEFAULT_SOURCE = "ado-service-hook";
    private static final String SYSTEM_TEAM_PROJECT = "System.TeamProject";
    private static final String SYSTEM_WORK_ITEM_TYPE = "System.WorkItemType";

    public NormalizedWorkItemEvent parse(JsonNode payload)
    {
        if (payload == null || payload.isNull())
        {
            throw new InvalidAdoEventPayloadException(List.of("'ado_event' is required"));
        }

        var event = payload.has("ado_event") ? payload.path("ado_event") : payload;
        if (!event.isObject())
        {
            throw new InvalidAdoEventPayloadException(List.of("'ado_event' must be an object"));
        }

        var resource = event.path("resource");
        var revision = resource.path("revision");
        var revisionFields = revision.path("fields");
        var revisedBy = resource.path("revisedBy");
        var normalized = new NormalizedWorkItemEvent(source(payload), organization(event),
                text(revisionFields, SYSTEM_TEAM_PROJECT, text(resource, "project", null)),
                positiveNumberCandidate(resource.get("workItemId"), revision.get("id")),
                integerCandidate(resource.get("rev"), revision.get("rev")), text(event, "eventType", null),
                new NormalizedWorkItemEvent.ChangedBy(text(revisedBy, "displayName", null),
                        firstText(revisedBy, "uniqueName", "email", "mailAddress")),
                text(resource, "url", null), text(event, "subscriptionId", null), text(event, "id", null),
                text(revisionFields, SYSTEM_WORK_ITEM_TYPE, text(resource, "workItemType", null)),
                changedFieldNames(resource.path("fields")));

        var errors = validationErrors(normalized);
        if (!errors.isEmpty())
        {
            throw new InvalidAdoEventPayloadException(errors);
        }
        return normalized;
    }

    private List<String> validationErrors(NormalizedWorkItemEvent event)
    {
        var errors = new ArrayList<String>();
        requireText(event.organization(), "organization", errors);
        requireText(event.project(), "project", errors);
        requirePositive(event.workItemId(), "workItemId", errors);
        requirePositive(event.revision(), "revision", errors);
        requireText(event.eventType(), "eventType", errors);
        if (event.eventType() != null && !event.eventType().isBlank()
                && !WORK_ITEM_UPDATED.equals(event.eventType()))
        {
            errors.add("'eventType' must be '" + WORK_ITEM_UPDATED + "'");
        }
        return errors;
    }

    private String source(JsonNode payload)
    {
        return text(payload, "source", DEFAULT_SOURCE);
    }

    private String organization(JsonNode event)
    {
        var baseUrl = text(event.path("resourceContainers").path("account"), "baseUrl", null);
        if (baseUrl != null)
        {
            try
            {
                var path = URI.create(baseUrl).getPath();
                if (path != null)
                {
                    for (String segment : path.split("/"))
                    {
                        if (!segment.isBlank())
                        {
                            return segment;
                        }
                    }
                }
            }
            catch (IllegalArgumentException ignored)
            {
                // Validation below reports the missing organization without echoing the URL.
            }
        }
        return text(event, "organization", null);
    }

    private Long positiveNumberCandidate(JsonNode primary, JsonNode fallback)
    {
        var candidate = usableNumber(primary) ? primary : fallback;
        return candidate != null && candidate.canConvertToLong() ? candidate.longValue() : null;
    }

    private Integer integerCandidate(JsonNode primary, JsonNode fallback)
    {
        var candidate = usableNumber(primary) ? primary : fallback;
        return candidate != null && candidate.canConvertToInt() ? candidate.intValue() : null;
    }

    private boolean usableNumber(JsonNode value)
    {
        return value != null && value.isIntegralNumber();
    }

    private String firstText(JsonNode node, String... names)
    {
        for (String name : names)
        {
            var value = text(node, name, null);
            if (value != null && !value.isBlank())
            {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String name, String fallback)
    {
        if (node == null || !node.isObject())
        {
            return fallback;
        }
        var value = node.get(name);
        return value != null && value.isTextual() ? value.textValue() : fallback;
    }

    private LinkedHashSet<String> changedFieldNames(JsonNode fields)
    {
        var names = new LinkedHashSet<String>();
        if (fields != null && fields.isObject())
        {
            fields.fieldNames().forEachRemaining(names::add);
        }
        return names;
    }

    private void requireText(String value, String fieldName, List<String> errors)
    {
        if (value == null || value.isBlank())
        {
            errors.add("'" + fieldName + "' is required");
        }
    }

    private void requirePositive(Number value, String fieldName, List<String> errors)
    {
        if (value == null)
        {
            errors.add("'" + fieldName + "' is required");
        }
        else if (value.longValue() <= 0)
        {
            errors.add("'" + fieldName + "' must be greater than 0");
        }
    }
}
