package com.dentalwings.approvalbot.workflow;

import java.util.Optional;

public record ApprovalValidation(Optional<String> smeEmail, Optional<String> sqaEmail, boolean validSme,
                                 boolean validSqa)
{

    public boolean fullyApprovedByDifferentUsers()
    {
        return validSme && validSqa && smeEmail.isPresent() && sqaEmail.isPresent()
                && !smeEmail.get().equals(sqaEmail.get());
    }
}
