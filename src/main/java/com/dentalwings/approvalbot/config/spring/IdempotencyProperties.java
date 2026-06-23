package com.dentalwings.approvalbot.config.spring;

public class IdempotencyProperties {

    private String type = "sqlite";
    private String sqlitePath = "./data/approval-bot.sqlite";
    private long ttlHours = 24;
    private int maxRecords = 10_000;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSqlitePath() {
        return sqlitePath;
    }

    public void setSqlitePath(String sqlitePath) {
        this.sqlitePath = sqlitePath;
    }

    public long getTtlHours() {
        return ttlHours;
    }

    public void setTtlHours(long ttlHours) {
        this.ttlHours = ttlHours;
    }

    public int getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(int maxRecords) {
        this.maxRecords = maxRecords;
    }
}
