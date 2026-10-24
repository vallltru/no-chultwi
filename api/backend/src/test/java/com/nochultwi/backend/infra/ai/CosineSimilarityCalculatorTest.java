package com.nochultwi.backend.infra.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class CosineSimilarityCalculatorTest {

    @Test
    @DisplayName("동일한 두 512차원 벡터 간의 코사인 유사도는 정확히 1.0이다")
    void testIdenticalVectors() {
        float[] v1 = new float[512];
        float[] v2 = new float[512];
        for (int i = 0; i < 512; i++) {
            v1[i] = (float) Math.sin(i);
            v2[i] = v1[i];
        }

        float similarity = CosineSimilarityCalculator.calculate(v1, v2);
        assertThat(similarity).isCloseTo(1.0f, offset(1e-5f));
    }

    @Test
    @DisplayName("서로 직교하는(Orthogonal) 두 벡터의 코사인 유사도는 0.0이다")
    void testOrthogonalVectors() {
        float[] v1 = new float[]{1.0f, 0.0f, 0.0f};
        float[] v2 = new float[]{0.0f, 1.0f, 0.0f};

        float similarity = CosineSimilarityCalculator.calculate(v1, v2);
        assertThat(similarity).isCloseTo(0.0f, offset(1e-5f));
    }

    @Test
    @DisplayName("서로 정반대 방향인 두 벡터의 코사인 유사도는 -1.0이다")
    void testOppositeVectors() {
        float[] v1 = new float[]{1.0f, 2.0f, 3.0f};
        float[] v2 = new float[]{-1.0f, -2.0f, -3.0f};

        float similarity = CosineSimilarityCalculator.calculate(v1, v2);
        assertThat(similarity).isCloseTo(-1.0f, offset(1e-5f));
    }

    @Test
    @DisplayName("null 또는 길이가 다른 벡터는 0.0을 반환한다")
    void testInvalidVectors() {
        assertThat(CosineSimilarityCalculator.calculate(null, new float[512])).isEqualTo(0.0f);
        assertThat(CosineSimilarityCalculator.calculate(new float[512], null)).isEqualTo(0.0f);
        assertThat(CosineSimilarityCalculator.calculate(new float[512], new float[256])).isEqualTo(0.0f);
    }
}
