package com.nochultwi.backend.infra.school;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "school.uos")
public record UosWiseProperties(
        @DefaultValue("202609522YHL84640") String apiKey,
        @DefaultValue("https://wise.uos.ac.kr/COM") String baseUrl,
        @DefaultValue("2026") String year,
        @DefaultValue("20") String term,
        @DefaultValue("true") Boolean enabled
) {
}
