package com.dentalwings.approvalbot.webhook.spring.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdoServiceHookWorkItemUpdatedRequest(String eventType, String eventName, String organization,
                                                   Resource resource)
{

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resource(@JsonAlias({
            "workItemId" }) Long id, @JsonAlias({ "revision" }) Integer rev, String project, String workItemType,
                           Identity revisedBy, Revision revision, Map<String, Object> fields)
    {
        public Resource
        {
            fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Revision(Integer rev, Map<String, Object> fields)
    {
        public Revision
        {
            fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Identity(String displayName, String uniqueName, String email, String mailAddress)
    {
    }
}
