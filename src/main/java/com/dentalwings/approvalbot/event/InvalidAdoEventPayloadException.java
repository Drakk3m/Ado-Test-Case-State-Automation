package com.dentalwings.approvalbot.event;

import java.util.List;

public final class InvalidAdoEventPayloadException extends IllegalArgumentException
{

    private final List<String> errors;

    public InvalidAdoEventPayloadException(List<String> errors)
    {
        super("Invalid ADO work item event: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors()
    {
        return errors;
    }
}
