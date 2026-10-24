package com.nochultwi.backend.infra.school;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UosWiseApiClientTest {

    private Clock clock;
    private UosWiseProperties properties;
    private UosWiseApiClient client;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneId.of("Asia/Seoul"));
        // API 자동 호출 방지(단위테스트 격리)를 위해 enabled=false 로 설정 (더미 API 키 사용)
        properties = new UosWiseProperties("test-dummy-api-key", "https://wise.uos.ac.kr/COM", "2026", "20", false);
        client = new UosWiseApiClient(properties, clock);
    }

    @Test
    @DisplayName("수업시간표 조회 응답의 CLASS_NM 포맷을 요일, 교시, 강의실로 올바르게 파싱한다")
    void parseScheduleFormat() {
        // 단일 요일 및 교시, 강의실 (예: 수[10,11,12,13]/37-나601)
        UosWiseApiClient.ParsedSchedule s1 = UosWiseApiClient.parseSchedule("수[10,11,12,13]/37-나601");
        assertThat(s1.dayOfWeek()).isEqualTo("WEDNESDAY");
        assertThat(s1.startPeriod()).isEqualTo(10);
        assertThat(s1.endPeriod()).isEqualTo(13);
        assertThat(s1.classroom()).isEqualTo("37-나601");

        // 다른 요일 형식 (금[6,7,8,9]/1-105)
        UosWiseApiClient.ParsedSchedule s2 = UosWiseApiClient.parseSchedule("금[6,7,8,9]/1-105");
        assertThat(s2.dayOfWeek()).isEqualTo("FRIDAY");
        assertThat(s2.startPeriod()).isEqualTo(6);
        assertThat(s2.endPeriod()).isEqualTo(9);
        assertThat(s2.classroom()).isEqualTo("1-105");

        // 다중 요일 형식 (화[5,6]/14-414,15,목[5,6]/14-430)
        UosWiseApiClient.ParsedSchedule s3 = UosWiseApiClient.parseSchedule("화[5,6]/14-414,15,목[5,6]/14-430");
        assertThat(s3.dayOfWeek()).isEqualTo("TUESDAY");
        assertThat(s3.startPeriod()).isEqualTo(5);
        assertThat(s3.endPeriod()).isEqualTo(6);

        // 빈 문자열 또는 null
        UosWiseApiClient.ParsedSchedule sEmpty = UosWiseApiClient.parseSchedule(null);
        assertThat(sEmpty.dayOfWeek()).isNull();
        assertThat(sEmpty.classroom()).isNull();
    }

    @Test
    @DisplayName("시연용 mock 강의 ID(course-demo) 요청 시 2026년도 2학기 기준 강의 정보(노창배 교수님 분반)를 즉시 반환한다")
    void getCourseInfoMockDemo() {
        Optional<SchoolCourseDto> courseOpt = client.getCourseInfo("course-demo");

        assertThat(courseOpt).isPresent();
        SchoolCourseDto course = courseOpt.get();
        assertThat(course.courseId()).isEqualTo("course-demo");
        assertThat(course.courseCode()).isEqualTo("30000");
        assertThat(course.name()).isEqualTo("창의공학기초설계(03)");
        assertThat(course.professorName()).isEqualTo("노창배");
        assertThat(course.classTimeAndRoom()).isEqualTo("수[10,11,12,13]/37-나601");
        assertThat(course.dayOfWeek()).isEqualTo("WEDNESDAY");
        assertThat(course.classroom()).isEqualTo("37-나601");
        assertThat(course.startPeriod()).isEqualTo(10);
        assertThat(course.endPeriod()).isEqualTo(13);
    }

    @Test
    @DisplayName("조회된 강의 정보는 인메모리 캐시되어 재호출 시 캐시 값을 즉시 반환한다")
    void getCourseInfoCaching() {
        Optional<SchoolCourseDto> first = client.getCourseInfo("course-demo");
        Optional<SchoolCourseDto> second = client.getCourseInfo("course-demo");

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.get()).isSameAs(second.get());
    }

    @Test
    @DisplayName("빈 강의 ID이거나 존재하지 않는 과목일 경우 Optional.empty()를 반환한다")
    void getCourseInfoEmptyOrUnknown() {
        assertThat(client.getCourseInfo(null)).isEmpty();
        assertThat(client.getCourseInfo("")).isEmpty();
        assertThat(client.getCourseInfo("unknown-course-999")).isEmpty();
    }

    @Test
    @DisplayName("시연 대상인 30000 강의는 오프라인 폴백을 통해서도 2026년도 2학기 시간표 정보가 완벽히 채워진 강의 정보를 반환한다")
    void getCourseInfoFallbackFor30000() {
        Optional<SchoolCourseDto> courseOpt = client.getCourseInfo("30000-03");

        assertThat(courseOpt).isPresent();
        SchoolCourseDto course = courseOpt.get();
        assertThat(course.courseCode()).isEqualTo("30000");
        assertThat(course.name()).contains("창의공학기초설계");
        assertThat(course.dayOfWeek()).isEqualTo("WEDNESDAY");
        assertThat(course.classroom()).isEqualTo("37-나601");
        assertThat(course.startPeriod()).isEqualTo(10);
        assertThat(course.endPeriod()).isEqualTo(13);
    }
}
