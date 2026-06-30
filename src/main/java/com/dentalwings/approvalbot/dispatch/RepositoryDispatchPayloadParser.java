package com.dentalwings.approvalbot.dispatch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.dentalwings.approvalbot.event.AdoWorkItemEventParser;
import com.dentalwings.approvalbot.event.InvalidAdoEventPayloadException;
import com.dentalwings.approvalbot.event.NormalizedWorkItemEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

public final class RepositoryDispatchPayloadParser
{

    private final ObjectMapper objectMapper;
    private final AdoWorkItemEventParser canonicalParser;

    public RepositoryDispatchPayloadParser()
    {
        this(new ObjectMapper());
    }

    RepositoryDispatchPayloadParser(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
        this.canonicalParser = new AdoWorkItemEventParser();
    }

    public NormalizedWorkItemEvent parse(Path jsonFile)
    {
        if (jsonFile == null)
        {
            throw new IllegalArgumentException("jsonFile is required");
        }

        var root = readTree(jsonFile);
        if (root.has("ado_event"))
        {
            return parseCanonical(root);
        }

        var payload = readLegacyPayload(root);
        var errors = payload.validationErrors();
        if (!errors.isEmpty())
        {
            throw new InvalidRepositoryDispatchPayloadException(errors);
        }
        return new NormalizedWorkItemEvent(payload.source(), payload.organization(), payload.project(),
                payload.workItemId(), payload.revision(), payload.eventType(),
                payload.changedBy() == null ? null : new NormalizedWorkItemEvent.ChangedBy(
                        payload.changedBy().displayName(), payload.changedBy().uniqueName()),
                payload.resourceUrl(), payload.subscriptionId(), payload.deliveryId(), null, java.util.Set.of());
    }

    private NormalizedWorkItemEvent parseCanonical(JsonNode root)
    {
        try
        {
            return canonicalParser.parse(root);
        }
        catch (InvalidAdoEventPayloadException ex)
        {
            throw new InvalidRepositoryDispatchPayloadException(ex.errors());
        }
    }

    private JsonNode readTree(Path jsonFile)
    {
        try
        {
            return objectMapper.readTree(jsonFile.toFile());
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Failed to parse repository_dispatch payload file: " + jsonFile, ex);
        }
    }

    private RepositoryDispatchPayload readLegacyPayload(JsonNode root)
    {
        try
        {
            return objectMapper.treeToValue(root, RepositoryDispatchPayload.class);
        }
        catch (MismatchedInputException ex)
        {
            var fieldName = ex.getPath().isEmpty() ? "payload" : ex.getPath().getLast().getFieldName();
            throw new InvalidRepositoryDispatchPayloadException(List.of("'" + fieldName + "' has an invalid value"));
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Failed to parse legacy repository_dispatch payload.", ex);
        }
    }
}

