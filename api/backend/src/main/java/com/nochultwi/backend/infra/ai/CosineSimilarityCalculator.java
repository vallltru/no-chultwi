package com.nochultwi.backend.infra.ai;

/**
 * 512차원 얼굴 임베딩 벡터 간의 코사인 유사도(Cosine Similarity)를 계산하는 수학 유틸리티.
 * cos(theta) = (u . v) / (||u|| * ||v||)
 */
public final class CosineSimilarityCalculator {

    private CosineSimilarityCalculator() {}

    /**
     * 두 float 배열 간의 코사인 유사도를 계산합니다.
     * 두 벡터가 L2 정규화(단위 벡터)되어 있는 경우 단순 내적(Dot Product)과 동일합니다.
     *
     * @param v1 첫 번째 벡터 (512차원)
     * @param v2 두 번째 벡터 (512차원)
     * @return 코사인 유사도 [-1.0 ~ 1.0] (길이 불일치 또는 영벡터 시 0.0)
     */
    public static float calculate(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length == 0 || v1.length != v2.length) {
            return 0.0f;
        }

        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }

        if (norm1 <= 0.0f || norm2 <= 0.0f) {
            return 0.0f;
        }

        return (float) (dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2)));
    }
}
