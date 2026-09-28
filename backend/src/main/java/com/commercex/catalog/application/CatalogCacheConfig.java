package com.commercex.catalog.application;

import com.commercex.catalog.application.CatalogViews.BrandView;
import com.commercex.common.cache.CacheConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CatalogCacheConfig {

    @Bean
    RedisCacheManagerBuilderCustomizer activeBrandsCache(ObjectMapper objectMapper) {
        return CacheConfig.typedCache(BrandService.ACTIVE_CACHE, objectMapper,
                objectMapper.getTypeFactory().constructCollectionType(List.class, BrandView.class),
                Duration.ofMinutes(10));
    }
}
