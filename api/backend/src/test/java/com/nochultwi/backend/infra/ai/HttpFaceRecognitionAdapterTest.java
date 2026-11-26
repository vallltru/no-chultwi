package com.nochultwi.backend.infra.ai;

import com.nochultwi.backend.domain.student.dto.EnrolledStudentDto;
import com.nochultwi.backend.domain.student.entity.Student;
import com.nochultwi.backend.global.error.BusinessException;
import com.nochultwi.backend.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpFaceRecognitionAdapterTest {

    private MockRestServiceServer mockServer;
    private HttpFaceRecognitionAdapter adapter;
    private AiProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AiProperties(true, "http://localhost:8000", 0.65f, "");
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new HttpFaceRecognitionAdapter(properties, builder);
    }

    @Test
    @DisplayName("AI 서버가 512차원 벡터를 반환하고 임계값(0.65) 이상 일치하는 학생이 존재하면 해당 학생을 식별 반환한다")
    void recognizeSuccessAboveThreshold() {
        float[] matchedVector = createUniformUnitVector();
        byte[] faceBytes = Student.floatsToBytes(matchedVector);
        EnrolledStudentDto student1 = new EnrolledStudentDto(1L, "2026920001", "테스트학생", "전자전기컴퓨터공학부", 2026, faceBytes);

        mockServer.expect(requestTo("http://localhost:8000/embed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist(HttpFaceRecognitionAdapter.INTERNAL_SECRET_HEADER))
                .andRespond(withSuccess(toEmbedJson(matchedVector), MediaType.APPLICATION_JSON));

        Optional<EnrolledStudentDto> result = adapter.recognize(new byte[]{1, 2, 3}, List.of(student1));

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("테스트학생");
        assertThat(result.get().studentNumber()).isEqualTo("2026920001");
        mockServer.verify();
    }

    @Test
    @DisplayName("internalSecret이 설정된 경우 AI 서버 호출 시 X-Internal-Secret 헤더를 함께 전송한다")
    void recognizeSendsInternalSecretHeaderWhenConfigured() {
        AiProperties securedProps = new AiProperties(true, "http://localhost:8000", 0.42f, "super-secret-token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer securedServer = MockRestServiceServer.bindTo(builder).build();
        HttpFaceRecognitionAdapter securedAdapter = new HttpFaceRecognitionAdapter(securedProps, builder);

        float[] matchedVector = createUniformUnitVector();
        EnrolledStudentDto student = new EnrolledStudentDto(
                1L, "2026920001", "보안학생", "컴퓨터과학부", 2026, Student.floatsToBytes(matchedVector));

        securedServer.expect(requestTo("http://localhost:8000/embed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpFaceRecognitionAdapter.INTERNAL_SECRET_HEADER, "super-secret-token"))
                .andRespond(withSuccess(toEmbedJson(matchedVector), MediaType.APPLICATION_JSON));

        Optional<EnrolledStudentDto> result = securedAdapter.recognize(new byte[]{1, 2, 3}, List.of(student));

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("보안학생");
        securedServer.verify();
    }

    @Test
    @DisplayName("PNG 매직바이트 이미지는 face.png와 image/png 멀티파트 타입으로 전송한다")
    void recognizeDetectsPngContentType() {
        float[] matchedVector = createUniformUnitVector();
        EnrolledStudentDto student = new EnrolledStudentDto(
                1L, "2026920001", "PNG학생", "컴퓨터과학부", 2026, Student.floatsToBytes(matchedVector));

        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};

        mockServer.expect(requestTo("http://localhost:8000/embed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("filename=\"face.png\"")))
                .andExpect(content().string(containsString("Content-Type: image/png")))
                .andRespond(withSuccess(toEmbedJson(matchedVector), MediaType.APPLICATION_JSON));

        Optional<EnrolledStudentDto> result = adapter.recognize(pngBytes, List.of(student));

        assertThat(result).isPresent();
        mockServer.verify();
    }

    @Test
    @DisplayName("AI 서버가 반환한 벡터와의 유사도가 임계값(0.65) 미만인 경우 empty(RETRY 대상)를 반환한다")
    void recognizeBelowThresholdReturnsEmpty() {
        float[] studentVector = new float[512];
        float[] probeVector = new float[512];

        // 서로 직교하는 벡터 (유사도 0.0 < 0.65)
        studentVector[0] = 1.0f;
        probeVector[1] = 1.0f;

        byte[] faceBytes = Student.floatsToBytes(studentVector);
        EnrolledStudentDto student1 = new EnrolledStudentDto(1L, "2026920001", "테스트학생", "전자전기컴퓨터공학부", 2026, faceBytes);

        mockServer.expect(requestTo("http://localhost:8000/embed"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(toEmbedJson(probeVector), MediaType.APPLICATION_JSON));

        Optional<EnrolledStudentDto> result = adapter.recognize(new byte[]{1, 2, 3}, List.of(student1));

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("AI 서버에서 얼굴 미검출(success: false) 응답 시 empty(RETRY 대상)를 반환한다")
    void recognizeNoFaceReturnsEmpty() {
        mockServer.expect(requestTo("http://localhost:8000/embed"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":false,\"message\":\"NO_FACE_DETECTED\"}", MediaType.APPLICATION_JSON));

        EnrolledStudentDto student = new EnrolledStudentDto(1L, "2026920001", "테스트학생", "전자전기컴퓨터공학부", 2026, new byte[512 * 4]);
        Optional<EnrolledStudentDto> result = adapter.recognize(new byte[]{1, 2, 3}, List.of(student));

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("AI 서버 통신 오류(500) 발생 시 503 RECOGNITION_UNAVAILABLE 비즈니스 예외를 던진다")
    void recognizeServerErrorThrows503() {
        mockServer.expect(requestTo("http://localhost:8000/embed"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        EnrolledStudentDto student = new EnrolledStudentDto(1L, "2026920001", "테스트학생", "전자전기컴퓨터공학부", 2026, new byte[512 * 4]);

        assertThatThrownBy(() -> adapter.recognize(new byte[]{1, 2, 3}, List.of(student)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECOGNITION_UNAVAILABLE);

        mockServer.verify();
    }

    @Test
    @DisplayName("DB에 비정상적인 벡터 차원(512차원이 아닌 데이터)을 가진 학생이 있어도 에러 없이 건너뛰고 정상 학생을 식별한다")
    void recognizeSkipsCandidateWithCorruptVectorDimension() {
        float[] matchedVector = createUniformUnitVector();
        byte[] validFaceBytes = Student.floatsToBytes(matchedVector);
        EnrolledStudentDto validStudent = new EnrolledStudentDto(1L, "2026920001", "정상학생", "기계정보공학과", 2026, validFaceBytes);

        byte[] corruptFaceBytes = new byte[128 * 4];
        EnrolledStudentDto corruptStudent = new EnrolledStudentDto(2L, "2026920002", "손상학생", "신소재공학과", 2026, corruptFaceBytes);

        mockServer.expect(requestTo("http://localhost:8000/embed"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(toEmbedJson(matchedVector), MediaType.APPLICATION_JSON));

        Optional<EnrolledStudentDto> result = adapter.recognize(new byte[]{1, 2, 3}, List.of(corruptStudent, validStudent));

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("정상학생");
        mockServer.verify();
    }

    private float[] createUniformUnitVector() {
        float[] vector = new float[512];
        for (int i = 0; i < 512; i++) {
            vector[i] = (float) (1.0 / Math.sqrt(512));
        }
        return vector;
    }

    private String toEmbedJson(float[] vector) {
        StringBuilder jsonBuilder = new StringBuilder("{\"success\":true,\"embedding\":[");
        for (int i = 0; i < vector.length; i++) {
            jsonBuilder.append(vector[i]);
            if (i < vector.length - 1) {
                jsonBuilder.append(",");
            }
        }
        jsonBuilder.append("]}");
        return jsonBuilder.toString();
    }
}
