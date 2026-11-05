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
import com.nochultwi.backend.infra.school.UosWiseApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class KioskCourseService {

    private static final LocalDate SEMESTER_START = LocalDate.of(2026, 9, 1);

    private final SchoolApiPort schoolApiPort;
    private final SessionRepository sessionRepository;
    private final Clock clock;

    public CurrentSessionResponse getCurrentSession(String courseIdOrClassroom) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        // 1. 단일 강의 ID(예: "30000-03")로 직접 조회되는 경우
        Optional<SchoolCourseDto> directCourseOpt = schoolApiPort.getCourseInfo(courseIdOrClassroom);
        if (directCourseOpt.isPresent()) {
            SchoolCourseDto schoolCourse = directCourseOpt.get();
            KioskCourseDto courseDto = new KioskCourseDto(
                    courseIdOrClassroom,
                    schoolCourse.courseCode(),
                    schoolCourse.name()
            );

            List<Session> activeSessions = sessionRepository.findActiveSessions(courseIdOrClassroom, now);
            KioskSessionDto sessionDto;
            if (!activeSessions.isEmpty()) {
                sessionDto = KioskSessionDto.from(activeSessions.get(0));
            } else if (schoolApiPort.isCourseScheduledAt(schoolCourse, now)) {
                sessionDto = KioskSessionDto.from(getOrCreateScheduledSession(schoolCourse, now));
            } else {
                sessionDto = null;
            }

            log.debug("Current session queried by courseId: courseId={}, hasSession={}, serverTime={}",
                    courseIdOrClassroom, sessionDto != null, now);

            return new CurrentSessionResponse(courseDto, now, sessionDto);
        }

        // 2. 강의실 번호(예: "37-B601", "1-105")로 조회하는 경우 -> 해당 강의실의 현재 시간대 수업 자동 매칭
        List<SchoolCourseDto> classroomCourses = schoolApiPort.getCoursesByClassroom(courseIdOrClassroom);
        if (classroomCourses.isEmpty()) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "강의 정보를 찾을 수 없습니다.");
        }

        Map<String, SchoolCourseDto> courseMap = classroomCourses.stream()
                .collect(Collectors.toMap(SchoolCourseDto::courseId, Function.identity(), (a, b) -> a));

        // 2-1. DB에 현재 시각으로 열려 있는 세션이 존재하는 경우 반환
        List<Session> activeClassroomSessions = sessionRepository.findActiveSessionsByCourseIds(courseMap.keySet(), now);
        if (!activeClassroomSessions.isEmpty()) {
            Session currentSession = activeClassroomSessions.get(0);
            SchoolCourseDto activeCourse = courseMap.getOrDefault(currentSession.getCourseId(), classroomCourses.get(0));
            KioskCourseDto courseDto = new KioskCourseDto(
                    activeCourse.courseId(),
                    activeCourse.courseCode(),
                    activeCourse.name()
            );
            return new CurrentSessionResponse(courseDto, now, KioskSessionDto.from(currentSession));
        }

        // 2-2. 학교 시간표(요일·교시)상 현재 시각에 해당 강의실에서 진행 중인 수업이 있으면 당일 Session 자동 생성 및 반환
        for (SchoolCourseDto course : classroomCourses) {
            if (schoolApiPort.isCourseScheduledAt(course, now)) {
                Session autoSession = getOrCreateScheduledSession(course, now);
                KioskCourseDto courseDto = new KioskCourseDto(
                        course.courseId(),
                        course.courseCode(),
                        course.name()
                );
                return new CurrentSessionResponse(courseDto, now, KioskSessionDto.from(autoSession));
            }
        }

        // 2-3. 공강 시간대: 오늘(현재 요일) 이 강의실에서 다음에 시작할 수업을 우선 선택하여 session: null 과 함께 반환
        SchoolCourseDto representCourse = selectUpcomingOrTodayCourse(classroomCourses, now);
        KioskCourseDto courseDto = new KioskCourseDto(
                representCourse.courseId(),
                representCourse.courseCode(),
                representCourse.name()
        );
        return new CurrentSessionResponse(courseDto, now, null);
    }

    /**
     * 공강 시간대에 표시할 대표 강의를 선정합니다.
     * 1순위: 오늘(현재 요일) 이 강의실에서 현재 시각 이후 가장 빨리 시작하는 다음 수업
     * 2순위: 오늘 이 강의실에서 열렸던 수업 중 가장 늦은 수업
     * 3순위: 강의실 첫 번째 수업
     */
    private SchoolCourseDto selectUpcomingOrTodayCourse(List<SchoolCourseDto> classroomCourses, OffsetDateTime now) {
        String todayDay = now.getDayOfWeek().name();

        List<SchoolCourseDto> todayCourses = classroomCourses.stream()
                .filter(c -> UosWiseApiClient.getEffectiveSchedules(c).stream()
                        .anyMatch(s -> todayDay.equalsIgnoreCase(s.dayOfWeek())))
                .toList();

        if (!todayCourses.isEmpty()) {
            // 오늘 앞으로 시작할 다음 수업 중 가장 빠른 수업 찾기
            Optional<SchoolCourseDto> nextUpcoming = todayCourses.stream()
                    .filter(c -> UosWiseApiClient.computeScheduleWindow(c, now).openAt().isAfter(now))
                    .min(Comparator.comparing(c -> UosWiseApiClient.computeScheduleWindow(c, now).openAt()));
            if (nextUpcoming.isPresent()) {
                return nextUpcoming.get();
            }

            // 오늘 수업이 모두 끝난 경우 오늘 마지막 수업 반환
            return todayCourses.stream()
                    .max(Comparator.comparing(c -> UosWiseApiClient.computeScheduleWindow(c, now).closeAt()))
                    .orElse(todayCourses.get(0));
        }

        return classroomCourses.get(0);
    }

    private Session getOrCreateScheduledSession(SchoolCourseDto course, OffsetDateTime now) {
        LocalDate today = now.toLocalDate();
        String autoSessionId = "session-" + course.courseId() + "-" + today;

        return sessionRepository.findBySessionId(autoSessionId).orElseGet(() -> {
            UosWiseApiClient.ScheduleWindow window = UosWiseApiClient.computeScheduleWindow(course, now);
            int week = (int) Math.max(1, Math.min(16, ChronoUnit.WEEKS.between(SEMESTER_START, today) + 1));

            Session newSession = Session.builder()
                    .sessionId(autoSessionId)
                    .courseId(course.courseId())
                    .date(today)
                    .week(week)
                    .sessionNumber(1)
                    .openAt(window.openAt())
                    .lateFrom(window.lateFrom())
                    .closeAt(window.closeAt())
                    .build();
            log.info("Auto-created scheduled session for classroom={} courseId={} sessionId={}",
                    course.classroom(), course.courseId(), autoSessionId);
            return sessionRepository.save(newSession);
        });
    }
}
