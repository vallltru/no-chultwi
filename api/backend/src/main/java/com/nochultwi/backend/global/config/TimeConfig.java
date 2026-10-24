package com.nochultwi.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Single source of "server time" (Spec 4: attendance is judged by the server clock in Asia/Seoul).
 * Services must obtain the current time from the injected {@link Clock} instead of calling now() directly,
 * so that boundary rules (openAt / lateFrom / closeAt) can be tested with a fixed clock.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SERVICE_ZONE);
    }
}
