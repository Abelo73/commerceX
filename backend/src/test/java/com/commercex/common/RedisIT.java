package com.commercex.common;

import static com.commercex.catalog.CatalogFixtures.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.commercex.AbstractIntegrationTest;
import com.commercex.common.ratelimit.RateLimit;
import com.commercex.common.ratelimit.RateLimitExceededException;
import com.commercex.common.ratelimit.RateLimiter;
import com.commercex.testsupport.ShopFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class RedisIT extends AbstractIntegrationTest {

    @Autowired
    RateLimiter rateLimiter;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void rateLimitIsSharedThroughRedis() {
        assertThat(rateLimiter.getClass().getSimpleName()).isEqualTo("RedisRateLimiter");
        String key = "it:" + unique("k");
        RateLimit twoPerMinute = new RateLimit(2, Duration.ofMinutes(1));

        rateLimiter.consume(key, twoPerMinute);
        rateLimiter.consume(key, twoPerMinute);
        assertThatThrownBy(() -> rateLimiter.consume(key, twoPerMinute))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.retryAfterSeconds()).isBetween(1L, 60L));
        assertThat(redis.getExpire("rl:" + key + ":2/60")).isPositive();
    }

    @Test
    void categoryTreeIsCachedAndEvictedOnChange() throws Exception {
        ShopFixtures shop = new ShopFixtures(mvc, json, jdbc);
        mvc.perform(get("/api/v1/categories"));
        assertThat(redis.hasKey("cx:categories.tree::all")).isTrue();

        String slug = unique("fresh");
        shop.catalog.category(null, slug); // evicts

        assertThat(redis.hasKey("cx:categories.tree::all")).isFalse();
        mvc.perform(get("/api/v1/categories")).andExpect(jsonPath("$[*].slug").value(hasItem(slug)));
    }
}
