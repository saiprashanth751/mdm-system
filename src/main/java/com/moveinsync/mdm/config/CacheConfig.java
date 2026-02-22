package com.moveinsync.mdm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis cache configuration with:
 * 1. Custom TTLs per cache name
 * 2. Graceful degradation via CacheErrorHandler — if Redis is unavailable,
 * the app falls through to database reads instead of crashing.
 *
 * Production impact: Without CacheErrorHandler, a Redis outage brings down
 * the entire application even though Redis is used only for performance.
 * With it, the app degrades gracefully — slower but operational.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

        private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

        @Bean
        public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
                RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(
                                                                new GenericJackson2JsonRedisSerializer()))
                                .entryTtl(Duration.ofMinutes(10))
                                .disableCachingNullValues();

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(defaultConfig)
                                .withCacheConfiguration("dashboard",
                                                defaultConfig.entryTtl(Duration.ofMinutes(5)))
                                .withCacheConfiguration("versions",
                                                defaultConfig.entryTtl(Duration.ofMinutes(30)))
                                .withCacheConfiguration("compatibility",
                                                defaultConfig.entryTtl(Duration.ofMinutes(15)))
                                .build();
        }

        @Override
        public CacheErrorHandler errorHandler() {
                return new CacheErrorHandler() {
                        @Override
                        public void handleCacheGetError(RuntimeException e, org.springframework.cache.Cache cache,
                                        Object key) {
                                log.warn("Cache GET failed for cache '{}', key '{}'. Falling through to database. Error: {}",
                                                cache.getName(), key, e.getMessage());
                        }

                        @Override
                        public void handleCachePutError(RuntimeException e, org.springframework.cache.Cache cache,
                                        Object key, Object value) {
                                log.warn("Cache PUT failed for cache '{}', key '{}'. Data saved to database only. Error: {}",
                                                cache.getName(), key, e.getMessage());
                        }

                        @Override
                        public void handleCacheEvictError(RuntimeException e, org.springframework.cache.Cache cache,
                                        Object key) {
                                log.warn("Cache EVICT failed for cache '{}', key '{}'. Error: {}",
                                                cache.getName(), key, e.getMessage());
                        }

                        @Override
                        public void handleCacheClearError(RuntimeException e, org.springframework.cache.Cache cache) {
                                log.warn("Cache CLEAR failed for cache '{}'. Error: {}",
                                                cache.getName(), e.getMessage());
                        }
                };
        }
}
