package com.company.approvalbot.workflow;

import com.company.approvalbot.config.ProjectApprovalConfig;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ChangeAnalyzer {

    private final ValueComparator valueComparator;

    public ChangeAnalyzer(ValueComparator valueComparator) {
        this.valueComparator = valueComparator;
    }

    public Set<String> changedReversibleFields(Map<String, Object> previousFields, Map<String, Object> currentFields, ProjectApprovalConfig config) {
        var changed = new LinkedHashSet<String>();
        for (String field : config.reversibleBusinessFields()) {
            if ("System.State".equals(field)
                    || field.equals(config.approvedBySmeField())
                    || field.equals(config.approvedBySqaField())) {
                continue;
            }
            if (!valueComparator.equivalent(previousFields.get(field), currentFields.get(field))) {
                changed.add(field);
            }
        }
        return Set.copyOf(changed);
    }
}
