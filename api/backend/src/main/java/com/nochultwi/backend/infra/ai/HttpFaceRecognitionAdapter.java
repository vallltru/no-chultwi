package com.nochultwi.backend.infra.ai;

import com.nochultwi.backend.domain.attendance.port.FaceRecognitionPort;
import com.nochultwi.backend.domain.student.dto.EnrolledStudentDto;
import com.nochultwi.backend.global.error.BusinessException;
import com.nochultwi.backend.global.error.ErrorCode;
import com.nochultwi.backend.infra.ai.dto.AiEmbedResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;

/**
 * 실제 ArcFace 딥러닝 AI 추론 서버(FastAPI / ONNX)와 연동하여
 * 512차원 특징 벡터를 추출하고 수강생 간의 코사인 유사도를 연산하는 어댑터.
 * ai.face.enabled=true 설정 시 활성화됩니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.face.enabled", havingValue = "true")
@EnableConfigurationProperties(AiProperties.class)
public class HttpFaceRecognitionAdapter implements FaceRecognitionPort {

    static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final AiProperties properties;
    private final RestClient restClient;

    public HttpFaceRecognitionAdapter(AiProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.serverUrl())
                .build();
    }

    @Override
    public Optional<EnrolledStudentDto> recognize(byte[] imageBytes, List<EnrolledStudentDto> candidates) {
        if (imageBytes == null || imageBytes.length == 0 || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        // 1. AI 서버로 얼굴 이미지 전송 및 512차원 특징 벡터 추출 요청
        float[] probeVector = extractFaceEmbedding(imageBytes);
        if (probeVector == null || probeVector.length == 0) {
            log.info("No face detected by AI model or extraction failed.");
            return Optional.empty();
        }

        // 2. 강좌 내 수강생 후보군과 코사인 유사도(Cosine Similarity) 전수 대조
        EnrolledStudentDto bestCandidate = null;
        float highestSimilarity = -1.0f;

        for (EnrolledStudentDto candidate : candidates) {
            if (candidate.faceVector() == null || candidate.faceVector().length == 0) {
                continue;
            }

            float[] candidateVector = bytesToFloats(candidate.faceVector());
            if (candidateVector.length != probeVector.length) {
                log.warn("Skipping candidate with mismatched face vector dimension: studentNumber={}, expected={}, actual={}",
                        candidate.studentNumber(), probeVector.length, candidateVector.length);
                continue;
            }

            float similarity = CosineSimilarityCalculator.calculate(probeVector, candidateVector);

            if (similarity > highestSimilarity) {
                highestSimilarity = similarity;
                bestCandidate = candidate;
            }
        }

        // 3. 임계값(Threshold, 기본 0.42) 검증
        float threshold = properties.similarityThreshold();
        if (highestSimilarity >= threshold && bestCandidate != null) {
            log.info("Face recognized successfully: studentNumber={}, name={}, similarity={}/threshold={}",
                    bestCandidate.studentNumber(), bestCandidate.name(), highestSimilarity, threshold);
            return Optional.of(bestCandidate);
        }

        log.info("Face match rejected: highestSimilarity={} below threshold={}", highestSimilarity, threshold);
        return Optional.empty();
    }

    private float[] extractFaceEmbedding(byte[] imageBytes) {
        try {
            boolean isPng = isPngImage(imageBytes);
            String filename = isPng ? "face.png" : "face.jpg";
            MediaType imageMediaType = isPng ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            HttpHeaders partHeaders = new HttpHeaders();
            partHeaders.setContentType(imageMediaType);
            HttpEntity<ByteArrayResource> fileEntity = new HttpEntity<>(resource, partHeaders);
            body.add("file", fileEntity);

            RestClient.RequestBodySpec requestSpec = restClient.post().uri("/embed");
            if (StringUtils.hasText(properties.internalSecret())) {
                requestSpec = requestSpec.header(INTERNAL_SECRET_HEADER, properties.internalSecret().trim());
            }

            AiEmbedResponse response = requestSpec
                    .body(body)
                    .retrieve()
                    .body(AiEmbedResponse.class);

            if (response == null || !response.success() || response.embedding() == null || response.embedding().isEmpty()) {
                return null;
            }

            List<Float> list = response.embedding();
            float[] floats = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                floats[i] = list.get(i);
            }
            return floats;

        } catch (Exception e) {
            log.error("AI face recognition service communication failed: url={}", properties.serverUrl(), e);
            throw new BusinessException(ErrorCode.RECOGNITION_UNAVAILABLE, "얼굴 인식 서비스를 일시적으로 사용할 수 없습니다.");
        }
    }

    private boolean isPngImage(byte[] bytes) {
        if (bytes.length < PNG_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (bytes[i] != PNG_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private float[] bytesToFloats(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }
}
