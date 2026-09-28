package com.commercex.cart.application;

import com.commercex.cart.infrastructure.CartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CartProperties.class)
class CartConfig {

    private static final Logger log = LoggerFactory.getLogger(CartConfig.class);

    private final CartRepository carts;

    CartConfig(CartRepository carts) {
        this.carts = carts;
    }

    /** Idempotent, so running on several instances at once is harmless. */
    @Scheduled(cron = "${commercex.cart.cleanup-cron:0 17 * * * *}")
    @Transactional
    void deleteExpiredGuestCarts() {
        int deleted = carts.deleteExpiredGuestCarts();
        if (deleted > 0) {
            log.info("Deleted {} expired guest carts", deleted);
        }
    }
}
