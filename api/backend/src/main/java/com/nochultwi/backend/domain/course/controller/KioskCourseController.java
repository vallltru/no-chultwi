package com.nochultwi.backend.domain.course.controller;

import com.nochultwi.backend.domain.course.dto.CurrentSessionResponse;
import com.nochultwi.backend.domain.course.service.KioskCourseService;
import com.nochultwi.backend.global.security.device.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kiosk/courses")
@RequiredArgsConstructor
public class KioskCourseController {

    private final KioskCourseService kioskCourseService;

    @GetMapping("/{courseId}/current-session")
    public ResponseEntity<CurrentSessionResponse> getCurrentSession(
            @PathVariable String courseId,
            @AuthenticationPrincipal DevicePrincipal principal) {
        CurrentSessionResponse response = kioskCourseService.getCurrentSession(courseId);
        return ResponseEntity.ok(response);
    }
}
