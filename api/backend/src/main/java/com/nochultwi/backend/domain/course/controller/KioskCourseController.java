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
@RequestMapping("/api/v1/kiosk")
@RequiredArgsConstructor
public class KioskCourseController {

    private final KioskCourseService kioskCourseService;

    /**
     * 단말기 헤더(X-Device-Id)에 배정된 강의실(예: 37-B601)을 기반으로
     * 현재 시각에 해당 강의실에서 진행 중인 수업과 활성 세션을 자동 반환합니다.
     */
    @GetMapping("/current-session")
    public ResponseEntity<CurrentSessionResponse> getCurrentSessionByDevice(
            @AuthenticationPrincipal DevicePrincipal principal) {
        CurrentSessionResponse response = kioskCourseService.getCurrentSession(principal.courseId());
        return ResponseEntity.ok(response);
    }

    /**
     * 강의실 번호(예: 37-B601) 또는 특정 강의 코드(예: 30000-03)로 현재 활성 세션을 조회합니다.
     */
    @GetMapping("/courses/{courseId}/current-session")
    public ResponseEntity<CurrentSessionResponse> getCurrentSession(
            @PathVariable String courseId,
            @AuthenticationPrincipal DevicePrincipal principal) {
        CurrentSessionResponse response = kioskCourseService.getCurrentSession(courseId, principal);
        return ResponseEntity.ok(response);
    }
}
