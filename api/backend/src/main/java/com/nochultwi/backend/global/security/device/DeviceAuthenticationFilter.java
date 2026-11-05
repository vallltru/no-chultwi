package com.nochultwi.backend.global.security.device;

import com.nochultwi.backend.domain.course.repository.SessionRepository;
import com.nochultwi.backend.domain.device.entity.Device;
import com.nochultwi.backend.domain.device.repository.DeviceRepository;
import com.nochultwi.backend.global.error.BusinessException;
import com.nochultwi.backend.global.error.ErrorCode;
import com.nochultwi.backend.infra.school.SchoolApiPort;
import com.nochultwi.backend.infra.school.SchoolCourseDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Component
public class DeviceAuthenticationFilter extends OncePerRequestFilter {

    public static final String DEVICE_ID_HEADER = "X-Device-Id";
    public static final String DEVICE_KEY_HEADER = "X-Device-Key";
    private static final String KIOSK_PATH_PREFIX = "/api/v1/kiosk";
    private static final String COURSES_PATH_SEGMENT = "/courses/";

    // 타이밍 공격 방지용 더미 해시
    private static final String DUMMY_KEY_HASH =
            "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ0123456";

    private final DeviceRepository deviceRepository;
    private final SessionRepository sessionRepository;
    private final SchoolApiPort schoolApiPort;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public DeviceAuthenticationFilter(
            DeviceRepository deviceRepository,
            SessionRepository sessionRepository,
            SchoolApiPort schoolApiPort,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.deviceRepository = deviceRepository;
        this.sessionRepository = sessionRepository;
        this.schoolApiPort = schoolApiPort;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();

        // 키오스크 전용 경로가 아닌 경우 통과
        if (!uri.startsWith(KIOSK_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authenticateDevice(request);
            filterChain.doFilter(request, response);
        } catch (BusinessException ex) {
            log.warn("Device authentication failed: uri={}, code={}, message={}",
                    uri, ex.getErrorCode(), ex.getMessage());
            handlerExceptionResolver.resolveException(request, response, null, ex);
        } catch (Exception ex) {
            log.error("Unexpected error in device authentication: uri={}", uri, ex);
            handlerExceptionResolver.resolveException(request, response, null, ex);
        }
    }

    private void authenticateDevice(HttpServletRequest request) {
        String deviceId = request.getHeader(DEVICE_ID_HEADER);
        String deviceKey = request.getHeader(DEVICE_KEY_HEADER);

        if (!StringUtils.hasText(deviceId) || !StringUtils.hasText(deviceKey)) {
            throw new BusinessException(ErrorCode.DEVICE_UNAUTHORIZED, "단말기 인증 헤더(X-Device-Id, X-Device-Key)가 누락되었습니다.");
        }

        deviceId = deviceId.trim();
        deviceKey = deviceKey.trim();

        Device device = deviceRepository.findByDeviceId(deviceId).orElse(null);

        // 타이밍 공격 방지: 단말기가 존재하지 않아도 더미 해시 비교 수행
        String hashToCheck = (device != null) ? device.getDeviceKeyHash() : DUMMY_KEY_HASH;
        boolean matches = passwordEncoder.matches(deviceKey, hashToCheck);

        if (device == null || !device.isActive() || !matches) {
            throw new BusinessException(ErrorCode.DEVICE_UNAUTHORIZED, "등록되지 않았거나 비활성화된 단말기입니다.");
        }

        // 특정 강의/강의실 경로(/api/v1/kiosk/courses/{courseId}/**) 호출 시 배정된 강의실 및 현재 수업 시간 일치 여부 검증
        verifyCoursePermission(request.getRequestURI(), device);

        DevicePrincipal principal = new DevicePrincipal(
                device.getId(),
                device.getDeviceId(),
                device.getName(),
                device.getCourseId()
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Device authenticated successfully: deviceId={}, assigned={}", device.getDeviceId(), device.getCourseId());
    }

    private void verifyCoursePermission(String uri, Device device) {
        int idx = uri.indexOf(COURSES_PATH_SEGMENT);
        if (idx == -1) {
            return;
        }

        String remaining = uri.substring(idx + COURSES_PATH_SEGMENT.length());
        int slashIdx = remaining.indexOf('/');
        String pathTarget = (slashIdx == -1) ? remaining : remaining.substring(0, slashIdx);

        if (!StringUtils.hasText(pathTarget)) {
            return;
        }

        if (!isAuthorizedForTarget(device.getCourseId(), pathTarget)) {
            throw new BusinessException(ErrorCode.COURSE_FORBIDDEN, "해당 강의에 대한 단말기 접근 권한이 없습니다.");
        }
    }

    private boolean isAuthorizedForTarget(String deviceAssigned, String target) {
        if (!StringUtils.hasText(deviceAssigned) || !StringUtils.hasText(target)) {
            return false;
        }

        // 1. 단말기에 직접 배정된 값과 요청 대상이 정확히 일치하는 경우 (강의 코드 1:1 배정 또는 동일 강의실 코드 조회)
        if (deviceAssigned.trim().equalsIgnoreCase(target.trim())) {
            return true;
        }

        // 2. 강의실 번호 간 일치 (예: "37-B601" == "37-나601")
        if (schoolApiPort.isSameClassroom(deviceAssigned, target)
                && !schoolApiPort.getCoursesByClassroom(target).isEmpty()) {
            return true;
        }

        // 3. 단말기가 강의실(예: "37-B601")로 등록되어 있고, 특정 강의(예: "30000-03")에 접근하는 경우:
        //    ① 해당 강의의 강의실이 일치해야 하며, ② 현재 시각이 그 강의의 활성 수업 시간이어야만 접근 허용 (시간 외 타 강의 접근 차단)
        Optional<SchoolCourseDto> courseOpt = schoolApiPort.getCourseInfo(target);
        if (courseOpt.isEmpty()) {
            return false;
        }

        SchoolCourseDto course = courseOpt.get();
        if (!schoolApiPort.isSameClassroom(deviceAssigned, course.classroom())) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean hasActiveSession = !sessionRepository.findActiveSessions(target, now).isEmpty();
        boolean isScheduledNow = schoolApiPort.isCourseScheduledAt(course, now);
        return hasActiveSession || isScheduledNow;
    }
}
