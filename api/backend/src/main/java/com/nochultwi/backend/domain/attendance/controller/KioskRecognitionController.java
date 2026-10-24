package com.nochultwi.backend.domain.attendance.controller;

import com.nochultwi.backend.domain.attendance.dto.RecognitionResponse;
import com.nochultwi.backend.domain.attendance.service.KioskRecognitionService;
import com.nochultwi.backend.global.security.device.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/kiosk")
@RequiredArgsConstructor
public class KioskRecognitionController {

    private final KioskRecognitionService kioskRecognitionService;

    @PostMapping(value = "/recognitions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecognitionResponse> recognize(
            @RequestParam("courseId") String courseId,
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "capturedAt", required = false) String capturedAt,
            @RequestPart("image") MultipartFile image,
            @AuthenticationPrincipal DevicePrincipal principal) {

        RecognitionResponse response = kioskRecognitionService.recognize(
                courseId, sessionId, capturedAt, image, principal
        );
        return ResponseEntity.ok(response);
    }
}
