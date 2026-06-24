package com.dentalwings.approvalbot.config.spring;

public class WebhookProperties {

    private SharedSecretProperties sharedSecret = new SharedSecretProperties();

    public SharedSecretProperties getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(SharedSecretProperties sharedSecret) {
        this.sharedSecret = sharedSecret == null ? new SharedSecretProperties() : sharedSecret;
    }

    public static class SharedSecretProperties {

        private boolean enabled = true;
        private String headerName = "X-ADO-Webhook-Secret";
        private String value;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
