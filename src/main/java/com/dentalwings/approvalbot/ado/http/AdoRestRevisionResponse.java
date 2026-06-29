package com.dentalwings.approvalbot.ado.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdoRestRevisionResponse(int rev, Map<String, Object> fields)
{

    public AdoRestRevisionResponse
    {
        fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
