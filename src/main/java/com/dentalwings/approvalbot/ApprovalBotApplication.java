package com.dentalwings.approvalbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ApprovalBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApprovalBotApplication.class, args);
    }
}
