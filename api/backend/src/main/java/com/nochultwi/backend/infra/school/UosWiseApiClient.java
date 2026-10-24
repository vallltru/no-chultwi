package com.nochultwi.backend.infra.school;

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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@EnableConfigurationProperties(UosWiseProperties.class)
public class UosWiseApiClient implements SchoolApiPort {

    private static final Pattern SCHEDULE_PATTERN = Pattern.compile("([월화수목금토일])\\[([\\d,]+)\\](?:/([^,\\n]+))?");
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

                    ParsedSchedule schedule = parseSchedule(item.classNm());
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

    public static ParsedSchedule parseSchedule(String classNm) {
        if (!StringUtils.hasText(classNm)) {
            return new ParsedSchedule(null, null, null, null);
        }

        Matcher matcher = SCHEDULE_PATTERN.matcher(classNm);
        if (matcher.find()) {
            String dayKor = matcher.group(1);
            String periodsStr = matcher.group(2);
            String room = matcher.group(3) != null ? matcher.group(3).trim() : null;

            String dayOfWeek = switch (dayKor) {
                case "월" -> "MONDAY";
                case "화" -> "TUESDAY";
                case "수" -> "WEDNESDAY";
                case "목" -> "THURSDAY";
                case "금" -> "FRIDAY";
                case "토" -> "SATURDAY";
                case "일" -> "SUNDAY";
                default -> null;
            };

            Integer startPeriod = null;
            Integer endPeriod = null;
            if (StringUtils.hasText(periodsStr)) {
                String[] periods = periodsStr.split(",");
                try {
                    startPeriod = Integer.parseInt(periods[0].trim());
                    endPeriod = Integer.parseInt(periods[periods.length - 1].trim());
                } catch (NumberFormatException ignored) {
                }
            }
            return new ParsedSchedule(dayOfWeek, room, startPeriod, endPeriod);
        }

        return new ParsedSchedule(null, null, null, null);
    }

    private SchoolCourseDto createDemoCourseDto(String courseId) {
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

    public record ParsedSchedule(
            String dayOfWeek,
            String classroom,
            Integer startPeriod,
            Integer endPeriod
    ) {
    }

    private record CacheEntry(SchoolCourseDto course, Instant expiresAt) {
    }
}
