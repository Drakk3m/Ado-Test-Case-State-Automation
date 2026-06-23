package com.dentalwings.approvalbot.config.spring;

import com.dentalwings.approvalbot.webhook.EventClassifier;
import com.dentalwings.approvalbot.workflow.WorkflowEngine;
import com.dentalwings.approvalbot.workflow.comment.CommentBuilder;
import com.dentalwings.approvalbot.workflow.patch.PatchBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApprovalBotBeanConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventClassifier eventClassifier() {
        return new EventClassifier();
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkflowEngine workflowEngine() {
        return new WorkflowEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    public PatchBuilder patchBuilder() {
        return new PatchBuilder();
    }

    @Bean
    @ConditionalOnMissingBean
    public CommentBuilder commentBuilder() {
        return new CommentBuilder();
    }
}
