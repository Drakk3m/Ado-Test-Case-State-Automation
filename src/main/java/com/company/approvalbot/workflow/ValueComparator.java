package com.company.approvalbot.workflow;

public class ValueComparator {

    public boolean equivalent(Object previousValue, Object currentValue) {
        return normalize(previousValue).equals(normalize(currentValue));
    }

    private String normalize(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }
}
