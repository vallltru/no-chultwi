package com.nochultwi.backend.domain.attendance.controller;

import com.nochultwi.backend.domain.attendance.dto.CheckInRequest;
import com.nochultwi.backend.domain.attendance.dto.CheckInResponse;
import com.nochultwi.backend.domain.attendance.dto.CheckInResult;
import com.nochultwi.backend.domain.attendance.service.KioskCheckInService;
import com.nochultwi.backend.global.security.device.DevicePrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kiosk")
@RequiredArgsConstructor
public class KioskCheckInController {

    private final KioskCheckInService kioskCheckInService;

    @PostMapping("/check-ins")
    public ResponseEntity<CheckInResponse> checkIn(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid CheckInRequest request,
            @AuthenticationPrincipal DevicePrincipal principal) {

        CheckInResult result = kioskCheckInService.checkIn(idempotencyKey, request, principal);
        return ResponseEntity.status(result.status()).body(result.response());
    }
}
