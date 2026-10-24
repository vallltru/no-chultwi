package com.nochultwi.backend.domain.attendance.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckInRequest(
        @NotBlank(message = "인식 토큰(recognitionToken)은 필수입니다.")
        String recognitionToken
) {}
