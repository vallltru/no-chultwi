package com.nochultwi.backend.domain.course.service;

import com.nochultwi.backend.domain.course.dto.CurrentSessionResponse;
import com.nochultwi.backend.domain.course.dto.KioskCourseDto;
import com.nochultwi.backend.domain.course.dto.KioskSessionDto;
import com.nochultwi.backend.domain.course.entity.Session;
import com.nochultwi.backend.domain.course.repository.SessionRepository;
import com.nochultwi.backend.global.error.BusinessException;
import com.nochultwi.backend.global.error.ErrorCode;
import com.nochultwi.backend.infra.school.SchoolApiPort;
import com.nochultwi.backend.infra.school.SchoolCourseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KioskCourseService {

    private final SchoolApiPort schoolApiPort;
    private final SessionRepository sessionRepository;
    private final Clock clock;

    public CurrentSessionResponse getCurrentSession(String courseId) {
        // 1. 학교 학사정보 포트(SchoolApiPort)를 통해 강의 정보 조회 (시간표 캐시 및 장애 폴백 내장)
        SchoolCourseDto schoolCourse = schoolApiPort.getCourseInfo(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND, "강의 정보를 찾을 수 없습니다."));

        KioskCourseDto courseDto = new KioskCourseDto(
                courseId,
                schoolCourse.courseCode(),
                schoolCourse.name()
        );

        // 2. 서버 시각(Asia/Seoul Clock) 기준 현재 활성화된 출석 차시 조회
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Session> activeSessions = sessionRepository.findActiveSessions(courseId, now);

        KioskSessionDto sessionDto = activeSessions.isEmpty() ? null : KioskSessionDto.from(activeSessions.get(0));

        log.debug("Current session queried: courseId={}, hasSession={}, serverTime={}",
                courseId, sessionDto != null, now);

        return new CurrentSessionResponse(courseDto, now, sessionDto);
    }
}
