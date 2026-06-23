package com.dentalwings.approvalbot.config.spring;

import java.util.LinkedHashSet;
import java.util.Set;

public class ApprovalUsersProperties {

    private Set<String> smeUsers = new LinkedHashSet<>();
    private Set<String> sqaUsers = new LinkedHashSet<>();

    public Set<String> getSmeUsers() {
        return smeUsers;
    }

    public void setSmeUsers(Set<String> smeUsers) {
        this.smeUsers = smeUsers == null ? new LinkedHashSet<>() : new LinkedHashSet<>(smeUsers);
    }

    public Set<String> getSqaUsers() {
        return sqaUsers;
    }

    public void setSqaUsers(Set<String> sqaUsers) {
        this.sqaUsers = sqaUsers == null ? new LinkedHashSet<>() : new LinkedHashSet<>(sqaUsers);
    }
}
