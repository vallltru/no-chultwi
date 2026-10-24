package com.nochultwi.backend.domain.course.entity;

import com.nochultwi.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "sessions",
        indexes = {
                @Index(name = "idx_sessions_course_active", columnList = "course_id, open_at, close_at")
        }
)
public class Session extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(name = "course_id", nullable = false, length = 64)
    private String courseId;

    @Column(name = "session_date", nullable = false)
    private LocalDate date;

    @Column(name = "week", nullable = false)
    private int week;

    @Column(name = "session_number", nullable = false)
    private int sessionNumber;

    @Column(name = "open_at", nullable = false)
    private OffsetDateTime openAt;

    @Column(name = "late_from", nullable = false)
    private OffsetDateTime lateFrom;

    @Column(name = "close_at", nullable = false)
    private OffsetDateTime closeAt;

    @Builder
    public Session(String sessionId, String courseId, LocalDate date, int week, int sessionNumber,
                   OffsetDateTime openAt, OffsetDateTime lateFrom, OffsetDateTime closeAt) {
        this.sessionId = sessionId;
        this.courseId = courseId;
        this.date = date;
        this.week = week;
        this.sessionNumber = sessionNumber;
        this.openAt = openAt;
        this.lateFrom = lateFrom;
        this.closeAt = closeAt;
    }

    public boolean isActiveAt(OffsetDateTime time) {
        return (time.isEqual(openAt) || time.isAfter(openAt)) && (time.isEqual(closeAt) || time.isBefore(closeAt));
    }
}
