package com.nochultwi.backend.domain.attendance.port;

import com.nochultwi.backend.domain.student.dto.EnrolledStudentDto;

import java.util.List;
import java.util.Optional;

/**
 * AI 얼굴 인식 엔진 연동을 위한 도메인 포트 인터페이스 (Hexagonal Architecture).
 */
public interface FaceRecognitionPort {

    /**
     * 촬영된 얼굴 이미지 바이너리에서 특징 벡터를 추출하고 수강생 후보군과 대조하여
     * 가장 유사도가 높은 학생을 식별합니다.
     *
     * @param imageBytes 업로드된 얼굴 이미지 바이트 배열
     * @param candidates 해당 강좌의 등록 수강생 목록
     * @return 매칭된 학생 정보 (얼굴 미검출, 식별 기준 미달 시 empty)
     */
    Optional<EnrolledStudentDto> recognize(byte[] imageBytes, List<EnrolledStudentDto> candidates);
}
