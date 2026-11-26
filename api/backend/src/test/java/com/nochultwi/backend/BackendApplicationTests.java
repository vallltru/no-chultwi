package com.nochultwi.backend;

import com.nochultwi.backend.infra.school.UosWiseProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class BackendApplicationTests {

    @Autowired
    private UosWiseProperties uosWiseProperties;

    @Test
    @DisplayName("테스트 프로필에서는 외부 학교 API 호출이 비활성화된다")
    void contextLoads() {
        assertThat(uosWiseProperties.enabled()).isFalse();
    }

}
