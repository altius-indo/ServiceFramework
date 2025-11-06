package com.enterprise.framework.service

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.Response
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Service for managing token revocation using Redis as a backing store.
 *
 * This service maintains a blacklist of revoked tokens in Redis with TTL
 * matching the token expiration time to optimize storage.
 *
 * @property redisApi Redis API client
 */
class TokenRevocationService(private val redisApi: RedisAPI) {

    companion object {
        private const val REVOCATION_KEY_PREFIX = "revoked:token:"
    }

    /**
     * Revokes a token by adding it to the Redis blacklist.
     *
     * @param tokenId Token ID to revoke
     * @param expiresAt When the token would naturally expire
     * @return Future indicating success
     */
    fun revokeToken(tokenId: String, expiresAt: Instant): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            val key = "$REVOCATION_KEY_PREFIX$tokenId"
            val ttl = calculateTtl(expiresAt)

            if (ttl <= 0) {
                // Token already expired, no need to revoke
                logger.debug { "Token $tokenId already expired, skipping revocation" }
                promise.complete()
                return promise.future()
            }

            // Set the token in Redis with TTL
            redisApi.setex(key, ttl.toString(), "1")
                .onSuccess {
                    logger.info { "Token revoked successfully: $tokenId (TTL: ${ttl}s)" }
                    promise.complete()
                }
                .onFailure { error ->
                    logger.error(error) { "Failed to revoke token: $tokenId" }
                    promise.fail(error)
                }
        } catch (e: Exception) {
            logger.error(e) { "Failed to revoke token: $tokenId" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Checks if a token has been revoked.
     *
     * @param tokenId Token ID to check
     * @return Future with true if revoked, false otherwise
     */
    fun isRevoked(tokenId: String): Future<Boolean> {
        val promise = Promise.promise<Boolean>()

        try {
            val key = "$REVOCATION_KEY_PREFIX$tokenId"

            redisApi.exists(listOf(key))
                .onSuccess { response ->
                    val exists = response?.toInteger() ?: 0
                    val isRevoked = exists > 0
                    logger.debug { "Token $tokenId revocation check: $isRevoked" }
                    promise.complete(isRevoked)
                }
                .onFailure { error ->
                    logger.error(error) { "Failed to check token revocation: $tokenId" }
                    // On error, fail closed - assume token is revoked
                    promise.complete(true)
                }
        } catch (e: Exception) {
            logger.error(e) { "Failed to check token revocation: $tokenId" }
            // On error, fail closed - assume token is revoked
            promise.complete(true)
        }

        return promise.future()
    }

    /**
     * Revokes all tokens for a user.
     *
     * This is useful for implementing logout-all-sessions functionality.
     * Note: This requires maintaining a user-to-tokens mapping which should
     * be implemented alongside session management.
     *
     * @param userId User ID
     * @param tokenIds List of token IDs to revoke
     * @param expiresAt When the tokens expire
     * @return Future indicating success
     */
    fun revokeUserTokens(userId: String, tokenIds: List<String>, expiresAt: Instant): Future<Void> {
        val promise = Promise.promise<Void>()

        if (tokenIds.isEmpty()) {
            promise.complete()
            return promise.future()
        }

        val futures = tokenIds.map { tokenId ->
            revokeToken(tokenId, expiresAt)
        }

        Future.all(futures)
            .onSuccess {
                logger.info { "Revoked ${tokenIds.size} tokens for user: $userId" }
                promise.complete()
            }
            .onFailure { error ->
                logger.error(error) { "Failed to revoke tokens for user: $userId" }
                promise.fail(error)
            }

        return promise.future()
    }

    /**
     * Calculates the TTL in seconds from now until the expiration time.
     *
     * @param expiresAt Expiration timestamp
     * @return TTL in seconds
     */
    private fun calculateTtl(expiresAt: Instant): Long {
        val now = Instant.now()
        val duration = Duration.between(now, expiresAt)
        return duration.seconds.coerceAtLeast(0)
    }

    /**
     * Cleans up expired revocation entries.
     *
     * Note: Redis will automatically clean up expired keys, so this method
     * is mainly for manual cleanup if needed.
     *
     * @return Future indicating success
     */
    fun cleanup(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // Redis automatically removes expired keys, so we just log
            logger.debug { "Cleanup called - Redis auto-expires revoked tokens" }
            promise.complete()
        } catch (e: Exception) {
            logger.error(e) { "Failed to cleanup revoked tokens" }
            promise.fail(e)
        }

        return promise.future()
    }
}
