package com.enterprise.framework.service

import com.enterprise.framework.config.RedisConfig
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisOptions
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * A service for interacting with a Redis cache.
 *
 * This class provides a set of methods for performing common cache operations,
 * such as getting, setting, and deleting keys. It also handles the initialization
 * and closing of the Redis client connection.
 *
 * @property vertx The Vert.x instance.
 * @property config The Redis configuration settings.
 */
class RedisCacheService(
    private val vertx: Vertx,
    private val config: RedisConfig
) {

    private lateinit var redisClient: Redis

    /**
     * Initializes the Redis client and establishes a connection to the server.
     *
     * This method should be called before any other methods in this service are used.
     * It configures the Redis client based on the provided [config] and attempts
     * to connect to the Redis server.
     *
     * @throws Exception if the connection to Redis fails.
     */
    suspend fun initialize() {
        logger.info { "Initializing Redis client..." }

        val options = RedisOptions()
            .setConnectionString("redis://${config.host}:${config.port}")

        config.password?.let { password ->
            options.setPassword(password)
        }

        redisClient = Redis.createClient(vertx, options)

        try {
            redisClient.connect().await()
            logger.info { "Redis client connected successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to connect to Redis" }
            throw e
        }
    }

    /**
     * Retrieves the value of a key from the cache.
     *
     * @param key The key to retrieve.
     * @return The value associated with the key, or `null` if the key does not exist or an error occurs.
     */
    suspend fun get(key: String): String? {
        return try {
            val response = redisClient.send(io.vertx.redis.client.Request.cmd(io.vertx.redis.client.Command.GET).arg(key)).await()
            response?.toString()
        } catch (e: Exception) {
            logger.error(e) { "Error getting key from Redis: $key" }
            null
        }
    }

    /**
     * Sets the value of a key in the cache, with an optional time-to-live (TTL).
     *
     * @param key The key to set.
     * @param value The value to associate with the key.
     * @param ttlSeconds The time-to-live for the key, in seconds. If `null`, the key will not expire.
     * @throws Exception if the operation fails.
     */
    suspend fun set(key: String, value: String, ttlSeconds: Long? = null) {
        try {
            if (ttlSeconds != null) {
                redisClient.send(
                    io.vertx.redis.client.Request.cmd(io.vertx.redis.client.Command.SETEX)
                        .arg(key)
                        .arg(ttlSeconds.toString())
                        .arg(value)
                ).await()
            } else {
                redisClient.send(
                    io.vertx.redis.client.Request.cmd(io.vertx.redis.client.Command.SET)
                        .arg(key)
                        .arg(value)
                ).await()
            }
            logger.debug { "Set key in Redis: $key" }
        } catch (e: Exception) {
            logger.error(e) { "Error setting key in Redis: $key" }
            throw e
        }
    }

    /**
     * Deletes a key from the cache.
     *
     * @param key The key to delete.
     * @throws Exception if the operation fails.
     */
    suspend fun delete(key: String) {
        try {
            redisClient.send(io.vertx.redis.client.Request.cmd(io.vertx.redis.client.Command.DEL).arg(key)).await()
            logger.debug { "Deleted key from Redis: $key" }
        } catch (e: Exception) {
            logger.error(e) { "Error deleting key from Redis: $key" }
            throw e
        }
    }

    /**
     * Checks if a key exists in the cache.
     *
     * @param key The key to check.
     * @return `true` if the key exists, `false` otherwise.
     */
    suspend fun exists(key: String): Boolean {
        return try {
            val response = redisClient.send(io.vertx.redis.client.Request.cmd(io.vertx.redis.client.Command.EXISTS).arg(key)).await()
            response?.toInteger() == 1
        } catch (e: Exception) {
            logger.error(e) { "Error checking key existence in Redis: $key" }
            false
        }
    }

    /**
     * Closes the Redis client connection.
     *
     * This method should be called when the service is no longer needed to
     * release the resources used by the client.
     */
    fun close() {
        logger.info { "Closing Redis client" }
        redisClient.close()
    }
}
