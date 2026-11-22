package com.nochultwi.backend.domain.course.service;

import com.nochultwi.backend.domain.course.dto.SchoolCourseDto;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 학교 시간표 문자열 파싱, 강의실 표기 정규화(가/나/다 -> A/B/C),
 * 교시 기반 출석/지각/마감 시간 창 계산을 담당하는 순수 계산 유틸리티.
 */
public final class CourseScheduleCalculator {

    private static final Pattern SCHEDULE_PATTERN = Pattern.compile("([월화수목금토일])\\[([\\d,]+)\\](?:/([^,\\n]+))?");

    private CourseScheduleCalculator() {
    }

    /**
     * 강의실 번호의 한글 동 구분(가/나/다)을 ASCII 영문(A/B/C)으로 정규화합니다.
     * 예: "37-나601" -> "37-B601", "37-다402" -> "37-C402", "1-105" -> "1-105"
     */
    public static String normalizeClassroomToAscii(String classroom) {
        if (!StringUtils.hasText(classroom)) {
            return "";
        }
        return classroom.trim()
                .replace("가", "A")
                .replace("나", "B")
                .replace("다", "C")
                .toUpperCase(Locale.ROOT);
    }

    public static boolean isSameClassroom(String roomA, String roomB) {
        if (!StringUtils.hasText(roomA) || !StringUtils.hasText(roomB)) {
            return false;
        }
        return normalizeClassroomToAscii(roomA).equals(normalizeClassroomToAscii(roomB));
    }

    public static boolean usesClassroom(SchoolCourseDto course, String targetClassroom) {
        if (course == null || !StringUtils.hasText(targetClassroom)) {
            return false;
        }
        if (isSameClassroom(targetClassroom, course.classroom())) {
            return true;
        }
        for (ParsedSchedule schedule : parseAllSchedules(course.classTimeAndRoom())) {
            if (isSameClassroom(targetClassroom, schedule.classroom())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 다중 요일/강의실 수업(예: "화[5,6]/14-414,15,목[5,6]/14-430")에서
     * 현재 시각의 요일·교시뿐만 아니라 해당 요일의 강의실(classroom)까지 일치하는지 검사합니다.
     */
    public static boolean isCourseScheduledAtInClassroom(
            SchoolCourseDto course, String classroom, OffsetDateTime time) {
        if (course == null || time == null) {
            return false;
        }
        String targetDay = time.getDayOfWeek().name();
        List<ParsedSchedule> schedules = getEffectiveSchedules(course);

        for (ParsedSchedule schedule : schedules) {
            if (!targetDay.equalsIgnoreCase(schedule.dayOfWeek())
                    || schedule.startPeriod() == null || schedule.endPeriod() == null) {
                continue;
            }
            if (StringUtils.hasText(classroom) && !isSameClassroom(classroom, schedule.classroom())) {
                continue;
            }
            ScheduleWindow window = computeWindowForPeriods(schedule.startPeriod(), schedule.endPeriod(), time);
            if ((time.isEqual(window.openAt()) || time.isAfter(window.openAt()))
                    && (time.isEqual(window.closeAt()) || time.isBefore(window.closeAt()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 특정 요일(referenceTime) 및 강의실에 해당하는 강의의 출석 시간 창(openAt, lateFrom, closeAt)을 계산합니다.
     */
    public static ScheduleWindow computeScheduleWindow(SchoolCourseDto course, OffsetDateTime referenceTime) {
        return computeScheduleWindowForClassroom(course, null, referenceTime);
    }

    public static ScheduleWindow computeScheduleWindowForClassroom(
            SchoolCourseDto course, String classroom, OffsetDateTime referenceTime) {
        String targetDay = referenceTime.getDayOfWeek().name();
        List<ParsedSchedule> schedules = getEffectiveSchedules(course);

        for (ParsedSchedule schedule : schedules) {
            if (!targetDay.equalsIgnoreCase(schedule.dayOfWeek())
                    || schedule.startPeriod() == null || schedule.endPeriod() == null) {
                continue;
            }
            if (StringUtils.hasText(classroom) && !isSameClassroom(classroom, schedule.classroom())) {
                continue;
            }
            return computeWindowForPeriods(schedule.startPeriod(), schedule.endPeriod(), referenceTime);
        }

        int startP = course.startPeriod() != null ? course.startPeriod() : 1;
        int endP = course.endPeriod() != null ? course.endPeriod() : startP;
        return computeWindowForPeriods(startP, endP, referenceTime);
    }

    private static ScheduleWindow computeWindowForPeriods(int startPeriod, int endPeriod, OffsetDateTime referenceTime) {
        LocalDate date = referenceTime.toLocalDate();
        int startHour = Math.min(23, Math.max(0, 8 + startPeriod));
        int endHour = Math.min(23, Math.max(startHour, 8 + endPeriod));

        OffsetDateTime classStart = date.atTime(startHour, 0).atOffset(referenceTime.getOffset());
        OffsetDateTime openAt = classStart.minusMinutes(10);
        OffsetDateTime lateFrom = classStart.plusMinutes(10);
        OffsetDateTime closeAt = date.atTime(endHour, 50).atOffset(referenceTime.getOffset());
        return new ScheduleWindow(openAt, lateFrom, closeAt);
    }

    public static List<ParsedSchedule> getEffectiveSchedules(SchoolCourseDto course) {
        if (course == null) {
            return List.of();
        }
        List<ParsedSchedule> parsed = parseAllSchedules(course.classTimeAndRoom());
        if (!parsed.isEmpty()) {
            return parsed;
        }
        if (StringUtils.hasText(course.dayOfWeek()) && course.startPeriod() != null && course.endPeriod() != null) {
            return List.of(new ParsedSchedule(course.dayOfWeek(), course.classroom(), course.startPeriod(), course.endPeriod()));
        }
        return List.of();
    }

    public static ParsedSchedule parseSchedule(String classNm) {
        List<ParsedSchedule> all = parseAllSchedules(classNm);
        if (all.isEmpty()) {
            return new ParsedSchedule(null, null, null, null);
        }
        return all.get(0);
    }

    /**
     * CLASS_NM 문자열(예: "화[5,6]/14-414,15,목[5,6]/14-430" 또는 "월[1,2],수[1,2]/19-203")에서
     * 모든 요일·교시·강의실 세그먼트를 추출합니다.
     */
    public static List<ParsedSchedule> parseAllSchedules(String classNm) {
        if (!StringUtils.hasText(classNm)) {
            return List.of();
        }

        List<ParsedSchedule> rawList = new ArrayList<>();
        Matcher matcher = SCHEDULE_PATTERN.matcher(classNm);
        String fallbackRoom = null;

        while (matcher.find()) {
            String dayKor = matcher.group(1);
            String periodsStr = matcher.group(2);
            String room = matcher.group(3) != null ? matcher.group(3).trim() : null;
            if (StringUtils.hasText(room)) {
                fallbackRoom = room;
            }

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
            rawList.add(new ParsedSchedule(dayOfWeek, room, startPeriod, endPeriod));
        }

        if (rawList.isEmpty()) {
            return List.of();
        }

        // 앞 세그먼트에 강의실이 생략된 경우(예: "월[1,2],수[1,2]/19-203") 뒤 세그먼트의 강의실로 보완
        final String resolvedFallbackRoom = fallbackRoom;
        if (resolvedFallbackRoom != null) {
            List<ParsedSchedule> normalized = new ArrayList<>(rawList.size());
            for (ParsedSchedule s : rawList) {
                String room = StringUtils.hasText(s.classroom()) ? s.classroom() : resolvedFallbackRoom;
                normalized.add(new ParsedSchedule(s.dayOfWeek(), room, s.startPeriod(), s.endPeriod()));
            }
            return normalized;
        }

        return rawList;
    }

    public record ParsedSchedule(
            String dayOfWeek,
            String classroom,
            Integer startPeriod,
            Integer endPeriod
    ) {
    }

    public record ScheduleWindow(
            OffsetDateTime openAt,
            OffsetDateTime lateFrom,
            OffsetDateTime closeAt
    ) {
    }
}
