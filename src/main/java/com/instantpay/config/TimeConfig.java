package com.instantpay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {
    @Bean
    public Clock systemClock() {
        // Use UTC to avoid time zone surprises across envs/tests
        return Clock.systemUTC();
    }
}
