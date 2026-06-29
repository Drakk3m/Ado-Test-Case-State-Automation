package com.dentalwings.approvalbot.ui;

import java.util.ArrayList;
import java.util.List;

public class ConfigUiModel {

    private AdoConfig ado = new AdoConfig();
    private BotConfig bot = new BotConfig();
    private WebhookConfig webhook = new WebhookConfig();
    private RetryConfig retry = new RetryConfig();
    private IdempotencyConfig idempotency = new IdempotencyConfig();

    public AdoConfig getAdo() {
        return ado;
    }

    public void setAdo(AdoConfig ado) {
        this.ado = ado == null ? new AdoConfig() : ado;
    }

    public BotConfig getBot() {
        return bot;
    }

    public void setBot(BotConfig bot) {
        this.bot = bot == null ? new BotConfig() : bot;
    }

    public WebhookConfig getWebhook() {
        return webhook;
    }

    public void setWebhook(WebhookConfig webhook) {
        this.webhook = webhook == null ? new WebhookConfig() : webhook;
    }

    public RetryConfig getRetry() {
        return retry;
    }

    public void setRetry(RetryConfig retry) {
        this.retry = retry == null ? new RetryConfig() : retry;
    }

    public IdempotencyConfig getIdempotency() {
        return idempotency;
    }

    public void setIdempotency(IdempotencyConfig idempotency) {
        this.idempotency = idempotency == null ? new IdempotencyConfig() : idempotency;
    }

    public static class AdoConfig {
        private String organization = "";
        private boolean httpClientEnabled;
        private boolean dryRun = true;
        private List<ProjectConfig> projects = new ArrayList<>();

        public String getOrganization() {
            return organization;
        }

        public void setOrganization(String organization) {
            this.organization = organization;
        }

        public boolean isHttpClientEnabled() {
            return httpClientEnabled;
        }

        public void setHttpClientEnabled(boolean httpClientEnabled) {
            this.httpClientEnabled = httpClientEnabled;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public void setDryRun(boolean dryRun) {
            this.dryRun = dryRun;
        }

        public List<ProjectConfig> getProjects() {
            return projects;
        }

        public void setProjects(List<ProjectConfig> projects) {
            this.projects = projects == null ? new ArrayList<>() : projects;
        }
    }

    public static class ProjectConfig {
        private String name = "";
        private boolean enabled = true;
        private List<String> supportedWorkItemTypes = new ArrayList<>();
        private WorkflowStates states = new WorkflowStates();
        private ApprovalFields fields = new ApprovalFields();
        private ApprovalUsers approvals = new ApprovalUsers();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getSupportedWorkItemTypes() {
            return supportedWorkItemTypes;
        }

        public void setSupportedWorkItemTypes(List<String> supportedWorkItemTypes) {
            this.supportedWorkItemTypes = supportedWorkItemTypes == null ? new ArrayList<>() : supportedWorkItemTypes;
        }

        public WorkflowStates getStates() {
            return states;
        }

        public void setStates(WorkflowStates states) {
            this.states = states == null ? new WorkflowStates() : states;
        }

        public ApprovalFields getFields() {
            return fields;
        }

        public void setFields(ApprovalFields fields) {
            this.fields = fields == null ? new ApprovalFields() : fields;
        }

        public ApprovalUsers getApprovals() {
            return approvals;
        }

        public void setApprovals(ApprovalUsers approvals) {
            this.approvals = approvals == null ? new ApprovalUsers() : approvals;
        }
    }

    public static class WorkflowStates {
        private String design = "Design";
        private String inReview = "In Review";
        private String approved = "Approved";

        public String getDesign() {
            return design;
        }

        public void setDesign(String design) {
            this.design = design;
        }

        public String getInReview() {
            return inReview;
        }

        public void setInReview(String inReview) {
            this.inReview = inReview;
        }

        public String getApproved() {
            return approved;
        }

        public void setApproved(String approved) {
            this.approved = approved;
        }
    }

    public static class ApprovalFields {
        private String approvedBySme = "";
        private String approvedBySqa = "";
        private List<String> reversibleBusinessFields = new ArrayList<>();

        public String getApprovedBySme() {
            return approvedBySme;
        }

        public void setApprovedBySme(String approvedBySme) {
            this.approvedBySme = approvedBySme;
        }

        public String getApprovedBySqa() {
            return approvedBySqa;
        }

        public void setApprovedBySqa(String approvedBySqa) {
            this.approvedBySqa = approvedBySqa;
        }

        public List<String> getReversibleBusinessFields() {
            return reversibleBusinessFields;
        }

        public void setReversibleBusinessFields(List<String> reversibleBusinessFields) {
            this.reversibleBusinessFields = reversibleBusinessFields == null ? new ArrayList<>()
                    : reversibleBusinessFields;
        }
    }

    public static class ApprovalUsers {
        private List<String> smeUsers = new ArrayList<>();
        private List<String> sqaUsers = new ArrayList<>();

        public List<String> getSmeUsers() {
            return smeUsers;
        }

        public void setSmeUsers(List<String> smeUsers) {
            this.smeUsers = smeUsers == null ? new ArrayList<>() : smeUsers;
        }

        public List<String> getSqaUsers() {
            return sqaUsers;
        }

        public void setSqaUsers(List<String> sqaUsers) {
            this.sqaUsers = sqaUsers == null ? new ArrayList<>() : sqaUsers;
        }
    }

    public static class BotConfig {
        private String identityEmail = "";

        public String getIdentityEmail() {
            return identityEmail;
        }

        public void setIdentityEmail(String identityEmail) {
            this.identityEmail = identityEmail;
        }
    }

    public static class WebhookConfig {
        private SharedSecretConfig sharedSecret = new SharedSecretConfig();

        public SharedSecretConfig getSharedSecret() {
            return sharedSecret;
        }

        public void setSharedSecret(SharedSecretConfig sharedSecret) {
            this.sharedSecret = sharedSecret == null ? new SharedSecretConfig() : sharedSecret;
        }
    }

    public static class SharedSecretConfig {
        private boolean enabled = true;
        private String headerName = "X-ADO-Webhook-Secret";

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
    }

    public static class RetryConfig {
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

    public static class IdempotencyConfig {
        private String type = "sqlite";
        private String sqlitePath = "./data/approval-bot-sandbox.sqlite";
        private long ttlHours = 24;
        private int maxRecords = 10000;

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
}
