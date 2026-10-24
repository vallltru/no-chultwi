package com.nochultwi.backend;

import com.nochultwi.backend.global.config.TimeConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class BackendApplication {

    // Runs when the class is loaded, so it also applies to @SpringBootTest contexts (not only main()).
    static {
        TimeZone.setDefault(TimeZone.getTimeZone(TimeConfig.SERVICE_ZONE));
    }

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
