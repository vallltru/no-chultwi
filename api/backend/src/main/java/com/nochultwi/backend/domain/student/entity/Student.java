package com.nochultwi.backend.domain.student.entity;

import com.nochultwi.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "students")
public class Student extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_number", nullable = false, unique = true, length = 30)
    private String studentNumber;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "admission_year", nullable = false)
    private int admissionYear;

    @Lob
    @Column(name = "face_vector")
    private byte[] faceVector;

    @Builder
    public Student(String studentNumber, String name, String department, int admissionYear, byte[] faceVector) {
        this.studentNumber = studentNumber;
        this.name = name;
        this.department = department;
        this.admissionYear = admissionYear;
        this.faceVector = faceVector;
    }

    public void updateFaceVector(byte[] faceVector) {
        this.faceVector = faceVector;
    }

    /**
     * byte[] 형식의 얼굴 특징 벡터를 512차원 float[] 배열로 변환합니다.
     */
    public float[] getFaceVectorAsFloats() {
        if (faceVector == null || faceVector.length == 0) {
            return new float[0];
        }
        ByteBuffer buffer = ByteBuffer.wrap(faceVector).order(ByteOrder.LITTLE_ENDIAN);
        float[] floats = new float[faceVector.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    /**
     * 512차원 float[] 배열을 byte[] 형식으로 변환합니다.
     */
    public static byte[] floatsToBytes(float[] floats) {
        if (floats == null || floats.length == 0) {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }
}
