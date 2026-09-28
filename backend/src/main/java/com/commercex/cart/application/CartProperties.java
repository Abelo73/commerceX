package com.commercex.cart.application;

import com.commercex.common.ratelimit.RateLimit;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param guestTtl              inactivity after which a guest cart is deleted
 * @param maxLines              distinct products per cart
 * @param guestCreationsPerIp   limits bots from creating unbounded guest carts
 */
@ConfigurationProperties("commercex.cart")
public record CartProperties(Duration guestTtl, int maxLines, RateLimit guestCreationsPerIp) {
}
