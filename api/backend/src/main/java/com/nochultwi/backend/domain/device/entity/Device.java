package com.nochultwi.backend.domain.device.entity;

import com.nochultwi.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "devices")
public class Device extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, unique = true, length = 64)
    private String deviceId;

    @Column(name = "device_key_hash", nullable = false, length = 100)
    private String deviceKeyHash;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "course_id", nullable = false, length = 64)
    private String courseId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Builder
    public Device(String deviceId, String deviceKeyHash, String name, String courseId, Boolean isActive) {
        this.deviceId = deviceId;
        this.deviceKeyHash = deviceKeyHash;
        this.name = name;
        this.courseId = courseId;
        this.isActive = isActive != null ? isActive : true;
    }

    public void updateCourse(String courseId) {
        this.courseId = courseId;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }
}
