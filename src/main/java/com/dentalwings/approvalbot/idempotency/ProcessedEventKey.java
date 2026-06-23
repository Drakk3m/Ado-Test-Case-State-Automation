package com.dentalwings.approvalbot.idempotency;

import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import java.util.Locale;

public record ProcessedEventKey(String project, long workItemId, int revision) {

    public ProcessedEventKey {
        project = normalizeProject(project);
    }

    public static ProcessedEventKey from(ProcessWorkItemCommand command) {
        return from(command.workItemKey(), command.revision());
    }

    public static ProcessedEventKey from(AdoWorkItemKey workItemKey, int revision) {
        return new ProcessedEventKey(workItemKey.project(), workItemKey.workItemId(), revision);
    }

    private static String normalizeProject(String project) {
        if (project == null) {
            return "";
        }
        return project.trim().toLowerCase(Locale.ROOT);
    }
}
