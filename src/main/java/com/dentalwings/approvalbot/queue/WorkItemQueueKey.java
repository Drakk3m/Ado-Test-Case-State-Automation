package com.dentalwings.approvalbot.queue;

import com.dentalwings.approvalbot.ado.AdoWorkItemKey;
import com.dentalwings.approvalbot.processing.ProcessWorkItemCommand;
import java.util.Locale;

public record WorkItemQueueKey(String project, long workItemId) {

    public WorkItemQueueKey {
        project = normalizeProject(project);
    }

    public static WorkItemQueueKey from(ProcessWorkItemCommand command) {
        return from(command.workItemKey());
    }

    public static WorkItemQueueKey from(AdoWorkItemKey workItemKey) {
        return new WorkItemQueueKey(workItemKey.project(), workItemKey.workItemId());
    }

    private static String normalizeProject(String project) {
        if (project == null) {
            return "";
        }
        return project.trim().toLowerCase(Locale.ROOT);
    }
}
