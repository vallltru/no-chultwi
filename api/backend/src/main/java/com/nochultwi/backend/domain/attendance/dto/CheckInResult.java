package com.nochultwi.backend.domain.attendance.dto;

import org.springframework.http.HttpStatus;

public record CheckInResult(
        HttpStatus status,
        CheckInResponse response
) {}
