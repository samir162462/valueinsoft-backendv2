package com.example.valueinsoftbackend.Config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CATEGORY_JSON_FLAT = "categoryJsonFlat";
    public static final String CATEGORY_PAIRS = "categoryPairs";
    public static final String MAIN_MAJORS = "mainMajors";
    public static final String COMPANY_BY_OWNER = "companyByOwner";
    public static final String COMPANY_BRANCHES_BY_USER = "companyBranchesByUser";
    public static final String COMPANY_BY_ID = "companyById";
    public static final String BRANCHES_BY_COMPANY = "branchesByCompany";
    public static final String BRANCH_BY_ID = "branchById";
    public static final String BRANCH_SETTINGS_BUNDLE = "branchSettingsBundle";
    public static final String DASHBOARD_BRANCH_SUMMARY = "dashboardBranchSummary";
    public static final String DASHBOARD_COMPANY_SUMMARY = "dashboardCompanySummary";

    private static final List<String> CACHE_NAMES = List.of(
            CATEGORY_JSON_FLAT,
            CATEGORY_PAIRS,
            MAIN_MAJORS,
            COMPANY_BY_OWNER,
            COMPANY_BRANCHES_BY_USER,
            COMPANY_BY_ID,
            BRANCHES_BY_COMPANY,
            BRANCH_BY_ID,
            BRANCH_SETTINGS_BUNDLE,
            DASHBOARD_BRANCH_SUMMARY,
            DASHBOARD_COMPANY_SUMMARY
    );

    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory,
                                     RedisCacheConfiguration redisCacheConfiguration,
                                     @Value("${vls.cache.redis.enabled:false}") boolean redisEnabled) {
        if (!redisEnabled) {
            return new ConcurrentMapCacheManager(CACHE_NAMES.toArray(String[]::new));
        }

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .withInitialCacheConfigurations(Map.ofEntries(
                        Map.entry(CATEGORY_JSON_FLAT, redisCacheConfiguration.entryTtl(Duration.ofMinutes(5))),
                        Map.entry(CATEGORY_PAIRS, redisCacheConfiguration.entryTtl(Duration.ofMinutes(5))),
                        Map.entry(MAIN_MAJORS, redisCacheConfiguration.entryTtl(Duration.ofMinutes(10))),
                        Map.entry(COMPANY_BY_OWNER, redisCacheConfiguration.entryTtl(Duration.ofMinutes(5))),
                        Map.entry(COMPANY_BRANCHES_BY_USER, redisCacheConfiguration.entryTtl(Duration.ofMinutes(5))),
                        Map.entry(COMPANY_BY_ID, redisCacheConfiguration.entryTtl(Duration.ofMinutes(5))),
                        Map.entry(BRANCHES_BY_COMPANY, redisCacheConfiguration.entryTtl(Duration.ofMinutes(5))),
                        Map.entry(BRANCH_BY_ID, redisCacheConfiguration.entryTtl(Duration.ofMinutes(10))),
                        Map.entry(BRANCH_SETTINGS_BUNDLE, redisCacheConfiguration.entryTtl(Duration.ofMinutes(5))),
                        Map.entry(DASHBOARD_BRANCH_SUMMARY, redisCacheConfiguration.entryTtl(Duration.ofSeconds(60))),
                        Map.entry(DASHBOARD_COMPANY_SUMMARY, redisCacheConfiguration.entryTtl(Duration.ofSeconds(60)))
                ))
                .build();
    }

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(
            ObjectMapper objectMapper,
            @Value("${vls.cache.ttl-minutes:5}") long ttlMinutes
    ) {
        ObjectMapper cacheMapper = objectMapper.copy();
        cacheMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(Math.max(1, ttlMinutes)))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(cacheMapper)
                ));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> builder.enableStatistics();
    }
}
