package com.example.nlsql.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * CacheConfig configures Redis-based caching with sensible defaults. -
 * serializes cached values as JSON - sets a default TTL and allows customizing
 * TTL per-cache (schema -> longer TTL)
 */
@Configuration
public class CacheConfig {

	// Default TTL in milliseconds (can be overridden via application.yml)
	@Value("${spring.cache.redis.time-to-live:600000}")
	private long defaultTtlMs;

	// Configure the default cache behavior (serialization + TTL)
	@Bean
	public RedisCacheConfiguration defaultCacheConfig() {
		return RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMillis(defaultTtlMs))
				// Use GenericJackson2JsonRedisSerializer so cached objects are
				// readable/portable
				.serializeValuesWith(RedisSerializationContext.SerializationPair
						.fromSerializer(new GenericJackson2JsonRedisSerializer()));
	}

	// Create CacheManager and set a specific TTL for the "schema" cache
	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
		RedisCacheConfiguration config = defaultCacheConfig();

		Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
		// Make the schema cache longer lived by default (e.g., 30 minutes)
		cacheConfigs.put("schema", config.entryTtl(Duration.ofMinutes(30)));

		return RedisCacheManager.builder(redisConnectionFactory).cacheDefaults(config)
				.withInitialCacheConfigurations(cacheConfigs).transactionAware().build();
	}

	@Bean
	public LettuceConnectionFactory redisConnectionFactory() {
		return new LettuceConnectionFactory("localhost", 6379);
	}

	@Bean
	public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
		return new StringRedisTemplate(connectionFactory);
	}

}
