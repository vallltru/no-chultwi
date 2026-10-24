package com.nochultwi.backend.domain.attendance.entity;

import com.nochultwi.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(name = "uq_session_student", columnNames = {"session_id", "student_id"}),
        indexes = {
                @Index(name = "idx_att_session_status", columnList = "session_id, status")
        }
)
public class AttendanceRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private AttendanceSource source;

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    @Column(name = "is_manual_override", nullable = false)
    private boolean isManualOverride;

    @Column(name = "override_reason", length = 255)
    private String overrideReason;

    @Builder
    public AttendanceRecord(String sessionId, Long studentId, AttendanceStatus status,
                            AttendanceSource source, OffsetDateTime checkedInAt,
                            boolean isManualOverride, String overrideReason) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.status = status;
        this.source = source;
        this.checkedInAt = checkedInAt;
        this.isManualOverride = isManualOverride;
        this.overrideReason = overrideReason;
    }
}
