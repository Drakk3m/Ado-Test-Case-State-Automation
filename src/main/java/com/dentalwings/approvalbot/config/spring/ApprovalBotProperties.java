package com.dentalwings.approvalbot.config.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public class ApprovalBotProperties {

    private AdoProperties ado = new AdoProperties();
    private BotProperties bot = new BotProperties();
    private WebhookProperties webhook = new WebhookProperties();
    private RetryProperties retry = new RetryProperties();
    private IdempotencyProperties idempotency = new IdempotencyProperties();

    public AdoProperties getAdo() {
        return ado;
    }

    public void setAdo(AdoProperties ado) {
        this.ado = ado == null ? new AdoProperties() : ado;
    }

    public BotProperties getBot() {
        return bot;
    }

    public void setBot(BotProperties bot) {
        this.bot = bot == null ? new BotProperties() : bot;
    }

    public WebhookProperties getWebhook() {
        return webhook;
    }

    public void setWebhook(WebhookProperties webhook) {
        this.webhook = webhook == null ? new WebhookProperties() : webhook;
    }

    public RetryProperties getRetry() {
        return retry;
    }

    public void setRetry(RetryProperties retry) {
        this.retry = retry == null ? new RetryProperties() : retry;
    }

    public IdempotencyProperties getIdempotency() {
        return idempotency;
    }

    public void setIdempotency(IdempotencyProperties idempotency) {
        this.idempotency = idempotency == null ? new IdempotencyProperties() : idempotency;
    }
}
