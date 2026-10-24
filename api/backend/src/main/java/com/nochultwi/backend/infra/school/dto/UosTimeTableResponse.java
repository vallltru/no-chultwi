package com.nochultwi.backend.infra.school.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UosTimeTableResponse(
        @JsonProperty("INFO") @JsonAlias("info") List<UosTimeTableItem> info
) {
}
