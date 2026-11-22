package com.nochultwi.backend.infra.ai;

import com.nochultwi.backend.domain.attendance.port.FaceRecognitionPort;
import com.nochultwi.backend.domain.student.dto.EnrolledStudentDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 로컬 개발, 테스트 및 모의 시연 환경을 위한 모의 AI 얼굴인식 어댑터.
 * ai.face.enabled=false (기본값)일 때 활성화되어 단위 테스트 및 빌드가 독립적으로 동작합니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.face.enabled", havingValue = "false", matchIfMissing = true)
public class StubFaceRecognitionAdapter implements FaceRecognitionPort {

    @Override
    public Optional<EnrolledStudentDto> recognize(byte[] imageBytes, List<EnrolledStudentDto> candidates) {
        if (imageBytes == null || imageBytes.length == 0 || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        // 테스트 시뮬레이션용: 1바이트 0x00 더미 이미지 전송 시 인식 실패(RETRY) 재현
        if (imageBytes.length == 1 && imageBytes[0] == 0) {
            log.info("StubFaceRecognition: Simulating unrecognized face (RETRY response)");
            return Optional.empty();
        }

        // 기본 모의 동작: 후보 수강생 중 첫 번째 학생(데모 1번 학생)으로 매칭 성공 반환
        EnrolledStudentDto matched = candidates.get(0);
        log.info("StubFaceRecognition: Matched enrolled candidate: studentNumber={}, name={}",
                matched.studentNumber(), matched.name());
        return Optional.of(matched);
    }
}
