package com.commercex.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs. Jobs must be idempotent and safe to run on several instances at
 * once until a distributed lock (e.g. ShedLock) is introduced.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}
