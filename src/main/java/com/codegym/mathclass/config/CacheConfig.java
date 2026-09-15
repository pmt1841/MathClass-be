package com.codegym.mathclass.config;

import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String ROLE_PERMISSIONS_CACHE = "rolePermissions";
    public static final String AI_PROVIDERS_CACHE = "ai_providers_cache";
    public static final String AI_TASK_CONFIGS_CACHE = "ai_task_configs_cache";
    public static final String SYSTEM_PROMPTS_RENDER_CACHE = "systemPromptsRender";
    public static final String AI_CREDIT_CONFIGS_CACHE = "ai_credit_configs_cache";
    public static final String AI_CREDIT_DEFAULTS_CACHE = "ai_credit_defaults_cache";
    public static final String CREDIT_PACKAGES_CACHE = "credit_packages_cache";

    @Bean
    public CacheManager cacheManager(RedissonClient redissonClient) {
        Map<String, org.redisson.spring.cache.CacheConfig> config = new HashMap<>();

        // rolePermissions: TTL 30 phút, idle 15 phút
        config.put(ROLE_PERMISSIONS_CACHE, new org.redisson.spring.cache.CacheConfig(30 * 60 * 1000, 15 * 60 * 1000));

        // Cấu hình AI & Providers: TTL 1 giờ, idle 30 phút
        config.put(AI_PROVIDERS_CACHE, new org.redisson.spring.cache.CacheConfig(60 * 60 * 1000, 30 * 60 * 1000));
        config.put(AI_TASK_CONFIGS_CACHE, new org.redisson.spring.cache.CacheConfig(60 * 60 * 1000, 30 * 60 * 1000));
        config.put(AI_CREDIT_CONFIGS_CACHE, new org.redisson.spring.cache.CacheConfig(60 * 60 * 1000, 30 * 60 * 1000));
        config.put(AI_CREDIT_DEFAULTS_CACHE, new org.redisson.spring.cache.CacheConfig(60 * 60 * 1000, 30 * 60 * 1000));
        config.put(CREDIT_PACKAGES_CACHE, new org.redisson.spring.cache.CacheConfig(60 * 60 * 1000, 30 * 60 * 1000));

        // System Prompts: TTL 2 giờ, idle 1 giờ
        config.put(SYSTEM_PROMPTS_RENDER_CACHE, new org.redisson.spring.cache.CacheConfig(2 * 60 * 60 * 1000, 60 * 60 * 1000));

        return new RedissonSpringCacheManager(redissonClient, config);
    }
}

