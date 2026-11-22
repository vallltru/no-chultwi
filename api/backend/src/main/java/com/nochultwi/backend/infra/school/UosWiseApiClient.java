package com.nochultwi.backend.infra.school;

import com.nochultwi.backend.domain.course.dto.SchoolCourseDto;
import com.nochultwi.backend.domain.course.port.SchoolApiPort;
import com.nochultwi.backend.domain.course.service.CourseScheduleCalculator;
import com.nochultwi.backend.infra.school.dto.UosTimeTableItem;
import com.nochultwi.backend.infra.school.dto.UosTimeTableResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@EnableConfigurationProperties(UosWiseProperties.class)
public class UosWiseApiClient implements SchoolApiPort {

    private static final long CACHE_TTL_SECONDS = 86400L; // 24시간 인메모리 캐싱

    private final UosWiseProperties properties;
    private final RestClient restClient;
    private final Clock clock;

    // 인메모리 시간표 캐시 (Key: subjectNo 또는 subjectNo-dvclNo)
    private final Map<String, CacheEntry> timetableCache = new ConcurrentHashMap<>();
    private Instant lastLoadedAt = Instant.MIN;

    @Autowired
    public UosWiseApiClient(UosWiseProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    public UosWiseApiClient(UosWiseProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "application/json, text/plain, */*")
                .build();
    }

    @Override
    public boolean isSameClassroom(String roomA, String roomB) {
        return CourseScheduleCalculator.isSameClassroom(roomA, roomB);
    }

    @Override
    public boolean isCourseScheduledAt(SchoolCourseDto course, OffsetDateTime time) {
        return isCourseScheduledAtInClassroom(course, null, time);
    }

    @Override
    public boolean isCourseScheduledAtInClassroom(SchoolCourseDto course, String classroom, OffsetDateTime time) {
        return CourseScheduleCalculator.isCourseScheduledAtInClassroom(course, classroom, time);
    }

    @Override
    public List<SchoolCourseDto> getCoursesByClassroom(String classroom) {
        if (!StringUtils.hasText(classroom)) {
            return List.of();
        }

        if (Boolean.TRUE.equals(properties.enabled()) && isCacheExpired()) {
            loadTimeTableFromWiseApi();
        }

        Map<String, SchoolCourseDto> matched = new LinkedHashMap<>();

        for (Map.Entry<String, CacheEntry> entry : timetableCache.entrySet()) {
            if (!entry.getKey().contains("-")) {
                continue;
            }
            SchoolCourseDto course = entry.getValue().course();
            if (course != null && CourseScheduleCalculator.usesClassroom(course, classroom)) {
                matched.putIfAbsent(course.courseId(), course);
            }
        }

        for (SchoolCourseDto fallback : getFallback30000Courses()) {
            if (CourseScheduleCalculator.usesClassroom(fallback, classroom)) {
                matched.putIfAbsent(fallback.courseId(), fallback);
            }
        }

        return new ArrayList<>(matched.values());
    }

    @Override
    public Optional<SchoolCourseDto> getCourseInfo(String courseId) {
        if (!StringUtils.hasText(courseId)) {
            return Optional.empty();
        }

        String subjectNo = parseSubjectNo(courseId);
        String dvclNo = parseDvclNo(courseId);
        String exactKey = subjectNo + "-" + dvclNo;

        // 1. 인메모리 캐시 확인
        CacheEntry cachedExact = timetableCache.get(exactKey);
        if (cachedExact != null && clock.instant().isBefore(cachedExact.expiresAt())) {
            return Optional.of(cachedExact.course());
        }

        CacheEntry cachedSubject = timetableCache.get(subjectNo);
        if (cachedSubject != null && clock.instant().isBefore(cachedSubject.expiresAt())) {
            return Optional.of(cachedSubject.course());
        }

        CacheEntry cachedDirect = timetableCache.get(courseId);
        if (cachedDirect != null && clock.instant().isBefore(cachedDirect.expiresAt())) {
            return Optional.of(cachedDirect.course());
        }

        // 2. 외부 API 비활성화 환경이거나 시연용 ID("course-demo") 처리
        if (!Boolean.TRUE.equals(properties.enabled())) {
            if ("course-demo".equals(courseId) || courseId.contains("30000")) {
                SchoolCourseDto demo = createDemoCourseDto(courseId);
                timetableCache.put(courseId, new CacheEntry(demo, clock.instant().plusSeconds(CACHE_TTL_SECONDS)));
                return Optional.of(demo);
            }
            return Optional.empty();
        }

        if ("course-demo".equals(courseId)) {
            SchoolCourseDto demo = createDemoCourseDto(courseId);
            timetableCache.put(courseId, new CacheEntry(demo, clock.instant().plusSeconds(CACHE_TTL_SECONDS)));
            return Optional.of(demo);
        }

        // 3. 캐시가 만료되었거나 비어있으면 서울시립대 WISE 시간표 API (/ApiTimeTable/list.do) 호출
        if (isCacheExpired()) {
            loadTimeTableFromWiseApi();
        }

        // 4. API 로드 후 재확인
        cachedExact = timetableCache.get(exactKey);
        if (cachedExact != null) {
            return Optional.of(cachedExact.course());
        }
        cachedSubject = timetableCache.get(subjectNo);
        if (cachedSubject != null) {
            return Optional.of(cachedSubject.course());
        }

        // 5. 시연 대상인 30000(창의공학기초설계)의 경우 네트워크 장애 시에도 안정적인 동작을 위해 폴백 제공
        if (courseId.contains("30000")) {
            log.info("Providing safe fallback for courseId={}", courseId);
            SchoolCourseDto fallback = createDemoCourseDto(courseId);
            timetableCache.put(courseId, new CacheEntry(fallback, clock.instant().plusSeconds(CACHE_TTL_SECONDS)));
            return Optional.of(fallback);
        }

        return Optional.empty();
    }

    private synchronized void loadTimeTableFromWiseApi() {
        if (!Boolean.TRUE.equals(properties.enabled()) || !isCacheExpired()) {
            return;
        }

        try {
            log.info("Fetching university timetable from UOS WISE API: year={}, term={}", properties.year(), properties.term());
            UosTimeTableResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ApiTimeTable/list.do")
                            .queryParam("apiKey", properties.apiKey())
                            .queryParam("year", properties.year())
                            .queryParam("term", properties.term())
                            .build())
                    .retrieve()
                    .body(UosTimeTableResponse.class);

            if (response != null && response.info() != null && !response.info().isEmpty()) {
                Instant expiresAt = clock.instant().plusSeconds(CACHE_TTL_SECONDS);
                for (UosTimeTableItem item : response.info()) {
                    if (item == null || !StringUtils.hasText(item.subjectNo())) {
                        continue;
                    }

                    CourseScheduleCalculator.ParsedSchedule schedule = CourseScheduleCalculator.parseSchedule(item.classNm());
                    String courseName = item.subjectNm() + (StringUtils.hasText(item.dvclNo()) ? "(" + item.dvclNo() + ")" : "");
                    SchoolCourseDto dto = new SchoolCourseDto(
                            item.subjectNo() + (StringUtils.hasText(item.dvclNo()) ? "-" + item.dvclNo() : ""),
                            item.subjectNo(),
                            courseName,
                            item.profKorNm(),
                            item.subDept(),
                            item.dvclNo(),
                            item.classNm(),
                            schedule.dayOfWeek(),
                            schedule.classroom(),
                            schedule.startPeriod(),
                            schedule.endPeriod()
                    );

                    timetableCache.put(item.subjectNo() + "-" + item.dvclNo(), new CacheEntry(dto, expiresAt));
                    timetableCache.putIfAbsent(item.subjectNo(), new CacheEntry(dto, expiresAt));
                }
                lastLoadedAt = clock.instant();
                log.info("Successfully cached {} timetable entries from UOS WISE API", response.info().size());
            }
        } catch (Exception ex) {
            log.warn("Failed to load timetable from UOS WISE API: {}", ex.getMessage());
        }
    }

    private boolean isCacheExpired() {
        return clock.instant().isAfter(lastLoadedAt.plusSeconds(CACHE_TTL_SECONDS));
    }

    private List<SchoolCourseDto> getFallback30000Courses() {
        return List.of(
                new SchoolCourseDto("30000-03", "30000", "창의공학기초설계(03)", "노창배", "컴퓨터과학부", "03", "수[10,11,12,13]/37-나601", "WEDNESDAY", "37-나601", 10, 13),
                new SchoolCourseDto("30000-02", "30000", "창의공학기초설계(02)", "홍두선", "신소재공학과", "02", "금[6,7,8,9]/37-나601", "FRIDAY", "37-나601", 6, 9),
                new SchoolCourseDto("30000-04", "30000", "창의공학기초설계(04)", "노창배", "컴퓨터과학부", "04", "금[10,11,12,13]/37-나601", "FRIDAY", "37-나601", 10, 13),
                new SchoolCourseDto("30000-01", "30000", "창의공학기초설계(01)", "정병준", "신소재공학과", "01", "금[6,7,8,9]/1-105", "FRIDAY", "1-105", 6, 9),
                new SchoolCourseDto("30000-05", "30000", "창의공학기초설계(05)", "서원경", "건축공학전공", "05", "월[6,7,8,9]/1-105", "MONDAY", "1-105", 6, 9),
                new SchoolCourseDto("30000-06", "30000", "창의공학기초설계(06)", "김민", "화학공학과", "06", "금[6,7,8,9]/25-401", "FRIDAY", "25-401", 6, 9),
                new SchoolCourseDto("30000-07", "30000", "창의공학기초설계(07)", "최용준", "환경공학부", "07", "화[2,3,4,5]/1-105", "TUESDAY", "1-105", 2, 5),
                new SchoolCourseDto("30000-08", "30000", "창의공학기초설계(08)", "천선정", "환경공학부", "08", "금[1,2,3,4]/25-401", "FRIDAY", "25-401", 1, 4)
        );
    }

    private SchoolCourseDto createDemoCourseDto(String courseId) {
        for (SchoolCourseDto fallback : getFallback30000Courses()) {
            if (fallback.courseId().equalsIgnoreCase(courseId)) {
                return fallback;
            }
        }
        return new SchoolCourseDto(
                courseId,
                "30000",
                "창의공학기초설계(03)",
                "노창배",
                "컴퓨터과학부",
                "03",
                "수[10,11,12,13]/37-나601",
                "WEDNESDAY",
                "37-나601",
                10,
                13
        );
    }

    private String parseSubjectNo(String courseId) {
        if (!StringUtils.hasText(courseId)) return "";
        String[] parts = courseId.split("-");
        for (String part : parts) {
            if (part.matches("\\d{5}")) {
                return part;
            }
        }
        return parts[0];
    }

    private String parseDvclNo(String courseId) {
        if (!StringUtils.hasText(courseId)) return "03";
        String[] parts = courseId.split("-");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d{2}")) {
                return parts[i];
            }
        }
        return "03";
    }

    private record CacheEntry(SchoolCourseDto course, Instant expiresAt) {
    }
}
