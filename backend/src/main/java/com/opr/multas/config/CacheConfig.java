package com.opr.multas.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.LettuceClientConfigurationBuilder;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String CACHE_MULTAS = "multas";
    public static final String CACHE_FILA_REVISAO = "filaRevisao";
    public static final String CACHE_CASOS_RESOLVIDOS = "casosResolvidos";
    public static final String CACHE_MODERACAO_CASOS = "moderacaoCasos";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Value("${app.cache.redis-url:}")
    private String redisUrl;

    @Value("${app.cache.ttl:30s}")
    private Duration ttl;

    @Value("${app.cache.max-size:10000}")
    private long maxSize;

    /**
     * Fornece o RedisConnectionFactory usado pelo cache. Quando REDIS_URL (Upstash)
     * existir, conecta a ele; caso contrário, deixa um factory inerte de localhost
     * (nunca usado, pois o CacheManager abaixo escolhe Caffeine nesse cenário) para
     * impedir que o Boot configure um com URL vazia e falhe o startup.
     */
    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedisConnectionFactory redisConnectionFactory() {
if (StringUtils.hasText(redisUrl)) {
            log.info("ConfigRedisFactory: Upstash/Vercel KV");
            RedisURI uri = RedisURI.create(redisUrl);
            RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration();
            cfg.setHostName(uri.getHost());
            if (uri.getPort() > 0) {
                cfg.setPort(uri.getPort());
            }
            if (uri.getPassword() != null) {
                cfg.setPassword(uri.getPassword());
            }
            String username = uri.getUsername();
            if (username != null && !username.isEmpty()) {
                cfg.setUsername(username);
            }
            int db = uri.getDatabase();
            if (db != 0) {
                cfg.setDatabase(db);
            }
            // Upstash/Vercel KV pede TLS mesmo quando o link é redis:// (não só rediss://).
            String lower = redisUrl.toLowerCase();
            boolean ssl = lower.startsWith("rediss://") || lower.contains(".upstash.io");
            LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(4));
            if (ssl) {
                builder.useSsl();
            }
            return new LettuceConnectionFactory(cfg, builder.build());
        }
        log.info("RedisConnectionFactory: fallback inerte (localhost - sem uso, cache Caffeine)");
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost"));
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory rcf) {
        if (StringUtils.hasText(redisUrl)) {
            log.info("Cache: Redis (Upstash/Vercel KV) - TTL {}", ttl);
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
            GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(mapper);

            RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .entryTtl(ttl);

            return RedisCacheManager.builder(rcf).cacheDefaults(config).build();
        }
        log.info("Cache: Caffeine em memória (dev) - TTL {}", ttl);
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(maxSize));
        return cacheManager;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.warn("Cache GET falhou em '{}' (chave '{}') - lendo do banco. {}", cache, key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, @Nullable Object value) {
                log.warn("Cache PUT falhou em '{}' (chave '{}') - cache ignorado. {}", cache, key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
                log.warn("Cache EVICT falhou em '{}' (chave '{}'). {}", cache, key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
                log.warn("Cache CLEAR falhou em '{}' (TTL {} cobre a invalidação). {}", cache, ttl, exception.getMessage());
            }
        };
    }
}