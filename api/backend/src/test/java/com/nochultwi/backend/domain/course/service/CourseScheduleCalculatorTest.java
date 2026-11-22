package com.nochultwi.backend.domain.course.service;

import com.nochultwi.backend.domain.course.dto.SchoolCourseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseScheduleCalculatorTest {

    private static final ZoneOffset KST = ZoneOffset.of("+09:00");

    @Test
    @DisplayName("단일·다중 요일 CLASS_NM 포맷을 요일, 교시, 강의실로 올바르게 파싱한다")
    void parseScheduleAndAllSchedules() {
        CourseScheduleCalculator.ParsedSchedule s1 = CourseScheduleCalculator.parseSchedule("수[10,11,12,13]/37-나601");
        assertThat(s1.dayOfWeek()).isEqualTo("WEDNESDAY");
        assertThat(s1.startPeriod()).isEqualTo(10);
        assertThat(s1.endPeriod()).isEqualTo(13);
        assertThat(s1.classroom()).isEqualTo("37-나601");

        List<CourseScheduleCalculator.ParsedSchedule> multi =
                CourseScheduleCalculator.parseAllSchedules("화[5,6]/14-414,15,목[5,6]/14-430");
        assertThat(multi).hasSize(2);
        assertThat(multi.get(0).dayOfWeek()).isEqualTo("TUESDAY");
        assertThat(multi.get(0).classroom()).isEqualTo("14-414");
        assertThat(multi.get(1).dayOfWeek()).isEqualTo("THURSDAY");
        assertThat(multi.get(1).classroom()).isEqualTo("14-430");

        // 앞 세그먼트 강의실 생략 시 뒤 세그먼트 강의실로 보완
        List<CourseScheduleCalculator.ParsedSchedule> sharedRoom =
                CourseScheduleCalculator.parseAllSchedules("월[1,2],수[1,2]/19-203");
        assertThat(sharedRoom).hasSize(2);
        assertThat(sharedRoom.get(0).classroom()).isEqualTo("19-203");
        assertThat(sharedRoom.get(1).classroom()).isEqualTo("19-203");

        CourseScheduleCalculator.ParsedSchedule empty = CourseScheduleCalculator.parseSchedule(null);
        assertThat(empty.dayOfWeek()).isNull();
        assertThat(empty.classroom()).isNull();
    }

    @Test
    @DisplayName("강의실 한글 동 구분(가/나/다)을 영문(A/B/C)으로 정규화하여 동등성을 비교한다")
    void normalizeAndCompareClassroom() {
        assertThat(CourseScheduleCalculator.normalizeClassroomToAscii("37-가101")).isEqualTo("37-A101");
        assertThat(CourseScheduleCalculator.normalizeClassroomToAscii("37-나601")).isEqualTo("37-B601");
        assertThat(CourseScheduleCalculator.normalizeClassroomToAscii("37-다402")).isEqualTo("37-C402");

        assertThat(CourseScheduleCalculator.isSameClassroom("37-B601", "37-나601")).isTrue();
        assertThat(CourseScheduleCalculator.isSameClassroom("37-b601", " 37-나601 ")).isTrue();
        assertThat(CourseScheduleCalculator.isSameClassroom("37-B601", "1-105")).isFalse();
        assertThat(CourseScheduleCalculator.isSameClassroom(null, "37-B601")).isFalse();
    }

    @Test
    @DisplayName("교시 정보를 기반으로 출석 시작(-10분), 지각(+10분), 마감(종료교시 50분) 시간 창을 계산한다")
    void computeScheduleWindowForClassroom() {
        SchoolCourseDto course = new SchoolCourseDto(
                "30000-03", "30000", "창의공학기초설계(03)", "노창배", "컴퓨터과학부", "03",
                "수[10,11,12,13]/37-나601", "WEDNESDAY", "37-나601", 10, 13
        );
        // 2026-10-21(수) 18:00 KST (10교시 시작 = 8 + 10 = 18시, 13교시 종료 = 8 + 13 = 21:50)
        OffsetDateTime wednesday = OffsetDateTime.of(2026, 10, 21, 18, 0, 0, 0, KST);

        CourseScheduleCalculator.ScheduleWindow window =
                CourseScheduleCalculator.computeScheduleWindowForClassroom(course, "37-B601", wednesday);

        assertThat(window.openAt()).isEqualTo(OffsetDateTime.of(2026, 10, 21, 17, 50, 0, 0, KST));
        assertThat(window.lateFrom()).isEqualTo(OffsetDateTime.of(2026, 10, 21, 18, 10, 0, 0, KST));
        assertThat(window.closeAt()).isEqualTo(OffsetDateTime.of(2026, 10, 21, 21, 50, 0, 0, KST));
    }

    @Test
    @DisplayName("다중 요일·강의실 수업에서는 해당 요일에 배정된 강의실과 수업 시간 창이 모두 일치해야 true를 반환한다")
    void isCourseScheduledAtInClassroomChecksBothDayAndRoom() {
        SchoolCourseDto multiRoomCourse = new SchoolCourseDto(
                "40000-01", "40000", "다중강의실수업(01)", "홍길동", "컴퓨터과학부", "01",
                "화[5,6]/14-414,목[5,6]/14-430", "TUESDAY", "14-414", 5, 6
        );
        // 2026-10-20(화) 13:10 KST (5교시 = 13:00, openAt 12:50 ~ closeAt 14:50)
        OffsetDateTime tuesdayClassTime = OffsetDateTime.of(2026, 10, 20, 13, 10, 0, 0, KST);
        OffsetDateTime tuesdayOutsideTime = OffsetDateTime.of(2026, 10, 20, 10, 0, 0, 0, KST);

        assertThat(CourseScheduleCalculator.isCourseScheduledAtInClassroom(multiRoomCourse, "14-414", tuesdayClassTime)).isTrue();
        assertThat(CourseScheduleCalculator.isCourseScheduledAtInClassroom(multiRoomCourse, "14-430", tuesdayClassTime)).isFalse();
        assertThat(CourseScheduleCalculator.isCourseScheduledAtInClassroom(multiRoomCourse, "14-414", tuesdayOutsideTime)).isFalse();
    }
}
