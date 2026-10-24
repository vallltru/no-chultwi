package com.nochultwi.backend.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Technical row-level audit timestamps.
 * <p>
 * Deliberately named {@code modifiedAt} (not {@code updatedAt}): in the API spec, {@code updatedAt} of an
 * attendance means "time of an admin correction" and must be {@code null} for face check-ins.
 * Domain timestamps like that must be separate fields and must never be mapped from these columns.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "modified_at")
    private OffsetDateTime modifiedAt;
}
