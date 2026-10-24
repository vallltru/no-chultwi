package com.nochultwi.backend.infra.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiEmbedResponse(
        boolean success,
        List<Float> embedding,
        String message
) {}
