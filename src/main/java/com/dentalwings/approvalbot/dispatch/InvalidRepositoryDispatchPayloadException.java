package com.dentalwings.approvalbot.dispatch;

import java.util.List;

public final class InvalidRepositoryDispatchPayloadException extends IllegalArgumentException
{

    private final List<String> errors;

    public InvalidRepositoryDispatchPayloadException(List<String> errors)
    {
        super("Invalid repository_dispatch payload: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors()
    {
        return errors;
    }
}

