package com.dentalwings.approvalbot.dispatch;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class RepositoryDispatchPayloadParser
{

    private final ObjectMapper objectMapper;

    public RepositoryDispatchPayloadParser()
    {
        this(new ObjectMapper());
    }

    RepositoryDispatchPayloadParser(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    public RepositoryDispatchPayload parse(Path jsonFile)
    {
        if (jsonFile == null)
        {
            throw new IllegalArgumentException("jsonFile is required");
        }

        var payload = readPayload(jsonFile);
        var errors = payload.validationErrors();
        if (!errors.isEmpty())
        {
            throw new InvalidRepositoryDispatchPayloadException(errors);
        }
        return payload;
    }

    private RepositoryDispatchPayload readPayload(Path jsonFile)
    {
        try
        {
            return objectMapper.readValue(jsonFile.toFile(), RepositoryDispatchPayload.class);
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Failed to parse repository_dispatch payload file: " + jsonFile, ex);
        }
    }
}

