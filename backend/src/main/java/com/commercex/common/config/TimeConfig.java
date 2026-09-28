package com.commercex.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TimeConfig {

    /** Inject this instead of calling {@code Instant.now()} so time-dependent rules are testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
