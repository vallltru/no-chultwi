package com.nochultwi.backend.domain.course.repository;

import com.nochultwi.backend.domain.course.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findBySessionId(String sessionId);

    List<Session> findByCourseId(String courseId);

    @Query("SELECT s FROM Session s WHERE s.courseId = :courseId AND :now >= s.openAt AND :now <= s.closeAt ORDER BY s.openAt DESC")
    List<Session> findActiveSessions(@Param("courseId") String courseId, @Param("now") OffsetDateTime now);

    @Query("SELECT s FROM Session s WHERE s.courseId IN :courseIds AND :now >= s.openAt AND :now <= s.closeAt ORDER BY s.openAt DESC")
    List<Session> findActiveSessionsByCourseIds(@Param("courseIds") Collection<String> courseIds, @Param("now") OffsetDateTime now);
}
