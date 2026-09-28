package com.commercex.common.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Redis-backed caching for hot, rarely changing reads. Redis is never the source of truth: cache errors are
 * logged and the call falls through to the database.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    /** Typed JSON serialization per cache (records are final, so no polymorphic type info is needed). */
    public static RedisCacheManagerBuilderCustomizer typedCache(String name, ObjectMapper objectMapper,
            JavaType type, Duration ttl) {
        return builder -> builder.withCacheConfiguration(name, RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("cx:")
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(objectMapper, type))));
    }

    @Bean
    RedisCacheConfiguration defaultRedisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig().prefixCacheNameWith("cx:").entryTtl(Duration.ofMinutes(10));
    }
}
