package com.enterprise.framework.service

import com.enterprise.framework.model.RateLimitingConfig
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.redis.client.RedisAPI
import mu.KotlinLogging
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Service for rate limiting authentication endpoints using token bucket algorithm.
 *
 * This service provides per-IP and per-user rate limiting to prevent abuse
 * and brute force attacks.
 *
 * @property redisApi Redis API client for distributed rate limiting
 * @property config Rate limiting configuration
 */
class RateLimitService(
    private val redisApi: RedisAPI,
    private val config: RateLimitingConfig
) {

    companion object {
        private const val RATE_LIMIT_KEY_PREFIX = "ratelimit:"
        private const val BLACKLIST_KEY_PREFIX = "blacklist:"
    }

    // Local cache for buckets (for better performance)
    private val localBuckets = ConcurrentHashMap<String, Bucket>()

    /**
     * Checks if a request should be rate limited.
     *
     * @param identifier Identifier for rate limiting (IP address, user ID, etc.)
     * @param endpoint Endpoint being accessed
     * @return Future with true if request is allowed, false if rate limited
     */
    fun checkRateLimit(identifier: String, endpoint: String): Future<Boolean> {
        if (!config.enabled) {
            return Future.succeededFuture(true)
        }

        val promise = Promise.promise<Boolean>()

        try {
            val key = "$RATE_LIMIT_KEY_PREFIX$endpoint:$identifier"

            // Check if identifier is blacklisted
            isBlacklisted(identifier)
                .compose { blacklisted ->
                    if (blacklisted) {
                        logger.warn { "Request blocked - identifier is blacklisted: $identifier" }
                        return@compose Future.succeededFuture(false)
                    }

                    // Check rate limit using local bucket
                    val bucket = getOrCreateBucket(key)
                    val allowed = bucket.tryConsume(1)

                    if (!allowed) {
                        logger.warn { "Rate limit exceeded for $identifier on $endpoint" }
                        // Consider blacklisting after repeated violations
                        checkAndBlacklist(identifier)
                    }

                    Future.succeededFuture(allowed)
                }
                .onSuccess { allowed ->
                    promise.complete(allowed)
                }
                .onFailure { error ->
                    logger.error(error) { "Failed to check rate limit for $identifier" }
                    // On error, allow the request to avoid false positives
                    promise.complete(true)
                }
        } catch (e: Exception) {
            logger.error(e) { "Failed to check rate limit" }
            promise.complete(true)
        }

        return promise.future()
    }

    /**
     * Records a rate limit violation.
     *
     * @param identifier Identifier that violated the rate limit
     * @param endpoint Endpoint that was accessed
     * @return Future indicating success
     */
    fun recordViolation(identifier: String, endpoint: String): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            val key = "${RATE_LIMIT_KEY_PREFIX}violations:$identifier"

            redisApi.incr(key)
                .compose { count ->
                    val violations = count?.toInteger() ?: 0

                    // Set expiration on first violation
                    if (violations == 1) {
                        redisApi.expire(listOf(key, config.windowSeconds.toString()))
                    } else {
                        Future.succeededFuture()
                    }
                }
                .onSuccess {
                    logger.debug { "Rate limit violation recorded for: $identifier" }
                    promise.complete()
                }
                .onFailure { error ->
                    logger.error(error) { "Failed to record violation for: $identifier" }
                    promise.fail(error)
                }
        } catch (e: Exception) {
            logger.error(e) { "Failed to record violation" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Blacklists an identifier for repeated violations.
     *
     * @param identifier Identifier to blacklist
     * @return Future indicating success
     */
    fun blacklist(identifier: String): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            val key = "$BLACKLIST_KEY_PREFIX$identifier"

            redisApi.setex(key, config.blacklistDurationSeconds.toString(), "1")
                .onSuccess {
                    logger.warn { "Identifier blacklisted: $identifier" }
                    promise.complete()
                }
                .onFailure { error ->
                    logger.error(error) { "Failed to blacklist identifier: $identifier" }
                    promise.fail(error)
                }
        } catch (e: Exception) {
            logger.error(e) { "Failed to blacklist identifier" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Checks if an identifier is blacklisted.
     *
     * @param identifier Identifier to check
     * @return Future with true if blacklisted, false otherwise
     */
    fun isBlacklisted(identifier: String): Future<Boolean> {
        val promise = Promise.promise<Boolean>()

        try {
            val key = "$BLACKLIST_KEY_PREFIX$identifier"

            redisApi.exists(listOf(key))
                .onSuccess { response ->
                    val exists = response?.toInteger() ?: 0
                    promise.complete(exists > 0)
                }
                .onFailure { error ->
                    logger.error(error) { "Failed to check blacklist for: $identifier" }
                    // On error, assume not blacklisted to avoid false positives
                    promise.complete(false)
                }
        } catch (e: Exception) {
            logger.error(e) { "Failed to check blacklist" }
            promise.complete(false)
        }

        return promise.future()
    }

    /**
     * Removes an identifier from the blacklist.
     *
     * @param identifier Identifier to remove
     * @return Future indicating success
     */
    fun removeFromBlacklist(identifier: String): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            val key = "$BLACKLIST_KEY_PREFIX$identifier"

            redisApi.del(listOf(key))
                .onSuccess {
                    logger.info { "Identifier removed from blacklist: $identifier" }
                    promise.complete()
                }
                .onFailure { error ->
                    logger.error(error) { "Failed to remove from blacklist: $identifier" }
                    promise.fail(error)
                }
        } catch (e: Exception) {
            logger.error(e) { "Failed to remove from blacklist" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Checks violations and blacklists if threshold exceeded.
     *
     * @param identifier Identifier to check
     * @return Future indicating success
     */
    private fun checkAndBlacklist(identifier: String): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            val key = "${RATE_LIMIT_KEY_PREFIX}violations:$identifier"

            redisApi.get(key)
                .compose { response ->
                    val violations = response?.toString()?.toIntOrNull() ?: 0

                    // Blacklist after 10 violations within the window
                    if (violations >= 10) {
                        logger.warn { "Blacklisting identifier after $violations violations: $identifier" }
                        blacklist(identifier)
                    } else {
                        Future.succeededFuture()
                    }
                }
                .onSuccess {
                    promise.complete()
                }
                .onFailure { error ->
                    logger.error(error) { "Failed to check and blacklist: $identifier" }
                    promise.fail(error)
                }
        } catch (e: Exception) {
            logger.error(e) { "Failed to check and blacklist" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Gets or creates a token bucket for rate limiting.
     *
     * @param key Bucket key
     * @return Token bucket
     */
    private fun getOrCreateBucket(key: String): Bucket {
        return localBuckets.computeIfAbsent(key) {
            val bandwidth = Bandwidth.classic(
                config.maxRequests.toLong(),
                Refill.intervally(
                    config.maxRequests.toLong(),
                    Duration.ofSeconds(config.windowSeconds)
                )
            )
            Bucket.builder()
                .addLimit(bandwidth)
                .build()
        }
    }

    /**
     * Clears local bucket cache.
     */
    fun clearLocalCache() {
        localBuckets.clear()
        logger.info { "Local rate limit cache cleared" }
    }
}
