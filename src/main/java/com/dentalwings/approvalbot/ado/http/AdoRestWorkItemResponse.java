package com.dentalwings.approvalbot.ado.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdoRestWorkItemResponse(
        long id,
        int rev,
        Map<String, Object> fields
) {

    public AdoRestWorkItemResponse {
        fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
