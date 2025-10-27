package com.enterprise.framework.config

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Configuration loader for the application
 * Loads configuration from multiple sources with the following precedence:
 * 1. Environment variables (highest priority)
 * 2. System properties
 * 3. application.conf file (lowest priority)
 */
class ConfigLoader(private val vertx: Vertx) {

    suspend fun loadConfig(): JsonObject {
        logger.info { "Loading application configuration..." }

        val fileStore = ConfigStoreOptions()
            .setType("file")
            .setFormat("json")
            .setConfig(JsonObject().put("path", "application.json"))

        val sysPropsStore = ConfigStoreOptions()
            .setType("sys")

        val envStore = ConfigStoreOptions()
            .setType("env")

        val options = ConfigRetrieverOptions()
            .addStore(fileStore)
            .addStore(sysPropsStore)
            .addStore(envStore)

        val retriever = ConfigRetriever.create(vertx, options)

        return try {
            val config = retriever.config.await()
            logger.info { "Configuration loaded successfully" }
            logger.debug { "Configuration: ${config.encodePrettily()}" }
            config
        } catch (e: Exception) {
            logger.error(e) { "Failed to load configuration" }
            throw e
        }
    }

    fun getServerConfig(config: JsonObject): ServerConfig {
        val serverConfig = config.getJsonObject("server") ?: JsonObject()
        return ServerConfig(
            host = serverConfig.getString("host", "0.0.0.0"),
            port = serverConfig.getInteger("port", 8080),
            ssl = serverConfig.getBoolean("ssl", false)
        )
    }

    fun getDatabaseConfig(config: JsonObject): DatabaseConfig {
        val dbConfig = config.getJsonObject("database")?.getJsonObject("dynamodb") ?: JsonObject()
        return DatabaseConfig(
            dynamoDbEndpoint = dbConfig.getString("endpoint", "http://localhost:8000"),
            region = dbConfig.getString("region", "us-east-1"),
            tableName = dbConfig.getString("tableName", "enterprise-data")
        )
    }

    fun getRedisConfig(config: JsonObject): RedisConfig {
        val redisConfig = config.getJsonObject("redis") ?: JsonObject()
        return RedisConfig(
            host = redisConfig.getString("host", "localhost"),
            port = redisConfig.getInteger("port", 6379),
            password = redisConfig.getString("password")
        )
    }
}
