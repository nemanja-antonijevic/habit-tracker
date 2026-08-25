package com.nantonijevic.habits.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.nantonijevic.habits.config.RedisCacheConfig.API_CLIENT_TIERS_CACHE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=false",
    "spring.cache.type=redis"
})
class ClientTierCacheIT {

    private static final int REDIS_PORT = 6379;

    private static final Instant CREATED_AT =
        Instant.parse("2026-08-21T08:00:00Z");

    @Container
    static final GenericContainer<?> redis =
        new GenericContainer<>(
            DockerImageName.parse(
                "redis:7.2.5-alpine"
            )
        ).withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(
        DynamicPropertyRegistry registry
    ) {
        registry.add(
            "spring.data.redis.host",
            redis::getHost
        );

        registry.add(
            "spring.data.redis.port",
            () -> redis.getMappedPort(REDIS_PORT)
        );
    }

    @Autowired
    private ClientTierResolver resolver;

    @Autowired
    private ApiClientRepository repository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApiKeyHasher hasher;

    @BeforeEach
    void clearStateBeforeTest() {
        clearState();
    }

    @AfterEach
    void clearStateAfterTest() {
        clearState();
    }

    @Test
    void cachedTierIsServedFromRedisAfterTheRowIsDeleted() {
        repository.saveAndFlush(
            client(
                "internal-key",
                ClientTier.INTERNAL
            )
        );

        assertThat(resolve("internal-key"))
            .isEqualTo(ClientTier.INTERNAL);

        repository.deleteAll();

        assertThat(resolve("internal-key"))
            .as("second lookup must come from Redis")
            .isEqualTo(ClientTier.INTERNAL);
    }

    @Test
    void redisKeyIsTheHashAndThePlaintextKeyIsAbsent() {
        String rawApiKey = "internal-key";

        repository.saveAndFlush(
            client(
                rawApiKey,
                ClientTier.INTERNAL
            )
        );

        assertThat(resolve(rawApiKey))
            .isEqualTo(ClientTier.INTERNAL);

        String key = onlyApiClientTierCacheKey();

        assertThat(key)
            .doesNotContain(rawApiKey)
            .isEqualTo(cacheKey(rawApiKey));

        assertThat(
            redisTemplate.hasKey(
                plaintextCacheKey(rawApiKey)
            )
        ).isFalse();
    }

    @Test
    void cachedTierExpiresWithinTheOneMinuteTtl() {
        repository.saveAndFlush(
            client(
                "internal-key",
                ClientTier.INTERNAL
            )
        );

        assertThat(resolve("internal-key"))
            .isEqualTo(ClientTier.INTERNAL);

        String key = onlyApiClientTierCacheKey();

        assertThat(
            redisTemplate.getExpire(
                key,
                TimeUnit.SECONDS
            )
        ).isBetween(1L, 60L);
    }

    @Test
    void differentKeysKeepIndependentRedisEntries() {
        repository.saveAndFlush(
            client(
                "internal-key",
                ClientTier.INTERNAL
            )
        );

        repository.saveAndFlush(
            client(
                "trusted-key",
                ClientTier.TRUSTED
            )
        );

        assertThat(resolve("internal-key"))
            .isEqualTo(ClientTier.INTERNAL);

        assertThat(resolve("trusted-key"))
            .isEqualTo(ClientTier.TRUSTED);

        assertThat(apiClientTierCacheKeys())
            .hasSize(2);
    }

    @Test
    void unknownKeyIsNotStoredInRedis() {
        assertThatThrownBy(
            () -> resolve("provisioned-later")
        ).isInstanceOf(InvalidApiKeyException.class);

        assertThat(
            redisTemplate.hasKey(
                cacheKey("provisioned-later")
            )
        ).isFalse();

        repository.saveAndFlush(
            client(
                "provisioned-later",
                ClientTier.TRUSTED
            )
        );

        assertThat(resolve("provisioned-later"))
            .as(
                "newly provisioned key must work "
                    + "without waiting for a negative TTL"
            )
            .isEqualTo(ClientTier.TRUSTED);
    }

    private ClientTier resolve(String apiKey) {
        return resolver.resolve(
            Optional.of(apiKey)
        );
    }

    private ApiClient client(
        String rawApiKey,
        ClientTier tier
    ) {
        return new ApiClient(
            hasher.hash(rawApiKey),
            tier,
            "Cache test client",
            CREATED_AT
        );
    }

    private String cacheKey(String rawApiKey) {
        return API_CLIENT_TIERS_CACHE
            + "::"
            + hasher.hash(rawApiKey);
    }

    private String onlyApiClientTierCacheKey() {
        Set<String> keys = apiClientTierCacheKeys();

        assertThat(keys)
            .hasSize(1);

        return keys.iterator().next();
    }

    private void clearState() {
        cacheManager
            .getCache(API_CLIENT_TIERS_CACHE)
            .clear();

        repository.deleteAll();
    }

    private static String plaintextCacheKey(
        String rawApiKey
    ) {
        return API_CLIENT_TIERS_CACHE
            + "::"
            + rawApiKey;
    }

    private Set<String> apiClientTierCacheKeys() {
        return redisTemplate.keys(
            API_CLIENT_TIERS_CACHE + "::*"
        );
    }

    @Test
    void storesOnlySha256HashInDatabase() {
        String rawApiKey = "database-secret-key";
        String expectedHash = hasher.hash(rawApiKey);

        ApiClient saved = repository.saveAndFlush(
            client(
                rawApiKey,
                ClientTier.INTERNAL
            )
        );

        assertThat(resolve(rawApiKey))
            .isEqualTo(ClientTier.INTERNAL);

        String storedHash = jdbcTemplate.queryForObject(
            """
            SELECT api_key_hash
            FROM api_clients
            WHERE id = ?
            """,
            String.class,
            saved.getId()
        );

        assertThat(storedHash)
            .isEqualTo(expectedHash)
            .isNotEqualTo(rawApiKey);
    }

    @Test
    void databaseRejectsValueThatIsNotSha256Length() {
        assertThatThrownBy(
            () -> jdbcTemplate.update(
                """
                INSERT INTO api_clients (
                    api_key_hash,
                    tier,
                    name,
                    created_at
                )
                VALUES (
                    'abc',
                    'PUBLIC',
                    'Invalid short hash',
                    CURRENT_TIMESTAMP
                )
                """
            )
        ).isInstanceOf(
            DataIntegrityViolationException.class
        );
    }
}
