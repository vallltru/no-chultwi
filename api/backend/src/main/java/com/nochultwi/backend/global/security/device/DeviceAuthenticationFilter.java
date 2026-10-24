package com.nochultwi.backend.global.security.device;

import com.nochultwi.backend.domain.device.entity.Device;
import com.nochultwi.backend.domain.device.repository.DeviceRepository;
import com.nochultwi.backend.global.error.BusinessException;
import com.nochultwi.backend.global.error.ErrorCode;
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
    private final PasswordEncoder passwordEncoder;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public DeviceAuthenticationFilter(
            DeviceRepository deviceRepository,
            PasswordEncoder passwordEncoder,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.deviceRepository = deviceRepository;
        this.passwordEncoder = passwordEncoder;
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

        // 특정 강의 경로(/api/v1/kiosk/courses/{courseId}/**) 호출 시 배정된 강의 일치 여부 검증
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

        log.debug("Device authenticated successfully: deviceId={}, courseId={}", device.getDeviceId(), device.getCourseId());
    }

    private void verifyCoursePermission(String uri, Device device) {
        int idx = uri.indexOf(COURSES_PATH_SEGMENT);
        if (idx == -1) {
            return;
        }

        String remaining = uri.substring(idx + COURSES_PATH_SEGMENT.length());
        int slashIdx = remaining.indexOf('/');
        String pathCourseId = (slashIdx == -1) ? remaining : remaining.substring(0, slashIdx);

        if (StringUtils.hasText(pathCourseId) && !pathCourseId.equals(device.getCourseId())) {
            throw new BusinessException(ErrorCode.COURSE_FORBIDDEN, "해당 강의에 대한 단말기 접근 권한이 없습니다.");
        }
    }
}
