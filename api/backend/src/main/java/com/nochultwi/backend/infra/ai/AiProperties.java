package com.nochultwi.backend.infra.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ai.face")
public record AiProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("http://localhost:8000") String serverUrl,
        @DefaultValue("0.65") float similarityThreshold
) {}
