package com.nochultwi.backend.domain.attendance.repository;

import com.nochultwi.backend.domain.attendance.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findBySessionIdAndStudentId(String sessionId, Long studentId);

    List<AttendanceRecord> findBySessionId(String sessionId);
}
