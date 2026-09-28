package com.commercex.category.application;

import com.commercex.common.cache.CacheConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CategoryCacheConfig {

    @Bean
    RedisCacheManagerBuilderCustomizer categoryTreeCache(ObjectMapper objectMapper) {
        return CacheConfig.typedCache(CategoryService.TREE_CACHE, objectMapper,
                objectMapper.getTypeFactory().constructCollectionType(List.class, CategoryNode.class),
                Duration.ofMinutes(10));
    }
}
