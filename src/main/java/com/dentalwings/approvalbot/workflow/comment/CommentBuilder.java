package com.dentalwings.approvalbot.workflow.comment;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

import com.dentalwings.approvalbot.domain.Identity;
import com.dentalwings.approvalbot.domain.ProcessingResult;
import com.dentalwings.approvalbot.domain.WorkflowDecision;

public class CommentBuilder
{

    public Optional<String> fromDecision(WorkflowDecision decision)
    {
        if (decision.result() == ProcessingResult.SKIPPED || !decision.patchRequired() || decision.comment() == null
                || decision.comment().isBlank())
        {
            return Optional.empty();
        }
        return Optional.of(decision.comment());
    }

    public String approvedAutomatically(String smeApprovalValue, String sqaApprovalValue)
    {
        return """
                Test Case approved automatically.
                
                Approved by SME:
                %s
                
                Approved by SQA:
                %s""".formatted(smeApprovalValue, sqaApprovalValue);
    }

    public String proposedChanges(Identity changedBy, Collection<ChangedField> changedFields)
    {
        var verifiedEmail = changedBy != null && changedBy.email() != null && !changedBy.email().isBlank();
        var identityLine = verifiedEmail ? "Proposed changes by %s [%s](mailto:%s).".formatted(displayName(changedBy),
                changedBy.email().trim(), changedBy.email().trim())
                : "Proposed changes by %s.".formatted(displayName(changedBy));
        var reasonLine = verifiedEmail
                ? "The original Test Case field values were automatically restored because this user is not an SME/SQA approver."
                : "The original Test Case field values were automatically restored because this user could not be verified as an SME/SQA approver.";

        return """
                %s
                
                %s
                
                Changed fields:
                
                %s""".formatted(identityLine, reasonLine, changedFieldsText(changedFields));
    }

    public String manualApprovalFieldEditCorrected()
    {
        return """
                Manual approval field edit corrected.
                
                Approval fields are controlled by the approval automation.
                The manually entered approval value was ignored and approval fields were recalculated from the actual reviewer identity.""";
    }

    public String unauthorizedApprovalActionRejected()
    {
        return """
                The approval action was rejected because the user identity could not be verified as an SME/SQA approver.
                
                The Test Case was returned to In Review because approval requires verified SME and SQA approvers.""";
    }

    public String invalidApprovedCorrection()
    {
        return "The Test Case was returned to In Review because it does not have valid SME and SQA approvals from different users.";
    }

    private String changedFieldsText(Collection<ChangedField> changedFields)
    {
        return changedFields.stream().sorted(Comparator.comparing(ChangedField::fieldReferenceName)).map(field -> """
                * %s:
                  Previous:
                  %s
                
                  Proposed:
                  %s""".formatted(field.fieldReferenceName(), renderValue(field.previousValue()),
                renderValue(field.proposedValue()))).collect(Collectors.joining("\n\n"));
    }

    private String displayName(Identity identity)
    {
        if (identity == null || identity.displayName() == null || identity.displayName().isBlank())
        {
            return "Unknown user";
        }
        return identity.displayName().trim();
    }

    private String renderValue(Object value)
    {
        return value == null ? "(null)" : value.toString();
    }

    public record ChangedField(String fieldReferenceName, Object previousValue, Object proposedValue)
    {
    }
}
