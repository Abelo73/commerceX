package com.commercex.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables {@code @Async} / {@code @ApplicationModuleListener}; Boot's executor uses virtual threads. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfig {
}
