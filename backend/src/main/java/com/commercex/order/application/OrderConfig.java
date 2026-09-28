package com.commercex.order.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderProperties.class)
class OrderConfig {

    private static final Logger log = LoggerFactory.getLogger(OrderConfig.class);
    private static final int MAX_PER_RUN = 500;

    private final OrderService orderService;

    OrderConfig(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Cancels unpaid orders whose payment window has closed, one order per transaction.
     * Safe on several instances at once (SKIP LOCKED) and alongside payment callbacks (row lock + state machine).
     */
    @Scheduled(fixedDelayString = "${commercex.orders.expiry-check-interval-ms:60000}")
    void cancelOverdueOrders() {
        int cancelled = 0;
        while (cancelled < MAX_PER_RUN && orderService.cancelNextOverdueOrder()) {
            cancelled++;
        }
        if (cancelled > 0) {
            log.info("Cancelled {} unpaid orders past their payment window", cancelled);
        }
    }
}
