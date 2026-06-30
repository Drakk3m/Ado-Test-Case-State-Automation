package com.dentalwings.approvalbot.dispatch;

import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import com.dentalwings.approvalbot.config.spring.ApprovalBotProperties;

public class ApprovalBotYamlConfigLoader
{

    public ApprovalBotProperties load(Path yamlFile)
    {
        if (yamlFile == null)
        {
            throw new IllegalArgumentException("yamlFile is required");
        }
        if (!Files.exists(yamlFile))
        {
            throw new IllegalArgumentException("YAML config file does not exist: " + yamlFile);
        }

        var environment = new StandardEnvironment();
        for (PropertySource<?> source : loadPropertySources(yamlFile))
        {
            environment.getPropertySources().addFirst(source);
        }
        return Binder.get(environment).bind("", Bindable.of(ApprovalBotProperties.class))
                .orElseGet(ApprovalBotProperties::new);
    }

    private List<PropertySource<?>> loadPropertySources(Path yamlFile)
    {
        try
        {
            return new YamlPropertySourceLoader().load("one-shot-config", new FileSystemResource(yamlFile));
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Failed to load YAML config file: " + yamlFile, ex);
        }
    }
}

