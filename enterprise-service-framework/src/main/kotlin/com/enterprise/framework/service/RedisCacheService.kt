package com.enterprise.framework.service

import com.enterprise.framework.config.RedisConfig
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisOptions
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Redis cache service for caching operations
 */
class RedisCacheService(
    private val vertx: Vertx,
    private val config: RedisConfig
) {

    private lateinit var redisClient: Redis

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

    suspend fun get(key: String): String? {
        return try {
            val response = redisClient.send(io.vertx.redis.client.Request.cmd(io.vertx.redis.client.Command.GET).arg(key)).await()
            response?.toString()
        } catch (e: Exception) {
            logger.error(e) { "Error getting key from Redis: $key" }
            null
        }
    }

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

    suspend fun delete(key: String) {
        try {
            redisClient.send(io.vertx.redis.client.Request.cmd(io.vertx.redis.client.Command.DEL).arg(key)).await()
            logger.debug { "Deleted key from Redis: $key" }
        } catch (e: Exception) {
            logger.error(e) { "Error deleting key from Redis: $key" }
            throw e
        }
    }

    suspend fun exists(key: String): Boolean {
        return try {
            val response = redisClient.send(io.vertx.redis.client.Request.cmd(io.vertx.redis.client.Command.EXISTS).arg(key)).await()
            response?.toInteger() == 1
        } catch (e: Exception) {
            logger.error(e) { "Error checking key existence in Redis: $key" }
            false
        }
    }

    fun close() {
        logger.info { "Closing Redis client" }
        redisClient.close()
    }
}
