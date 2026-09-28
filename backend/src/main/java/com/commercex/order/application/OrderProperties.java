package com.commercex.order.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param paymentWindow how long an unpaid order holds its stock before it is cancelled */
@ConfigurationProperties("commercex.orders")
public record OrderProperties(Duration paymentWindow) {
}
