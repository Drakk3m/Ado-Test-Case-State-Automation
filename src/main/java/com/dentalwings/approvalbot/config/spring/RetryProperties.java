package com.dentalwings.approvalbot.config.spring;

public class RetryProperties {

    private int maxAttempts = 3;
    private long defaultBackoffSeconds = 30;
    private boolean respectRetryAfter = true;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getDefaultBackoffSeconds() {
        return defaultBackoffSeconds;
    }

    public void setDefaultBackoffSeconds(long defaultBackoffSeconds) {
        this.defaultBackoffSeconds = defaultBackoffSeconds;
    }

    public boolean isRespectRetryAfter() {
        return respectRetryAfter;
    }

    public void setRespectRetryAfter(boolean respectRetryAfter) {
        this.respectRetryAfter = respectRetryAfter;
    }
}
