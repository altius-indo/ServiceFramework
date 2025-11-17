package com.enterprise.framework.cli

import com.enterprise.framework.authz.repositories.*
import com.enterprise.framework.cli.auth.CLIAuthenticator
import com.enterprise.framework.config.ConfigLoader
import com.enterprise.framework.config.DatabaseConfig
import com.enterprise.framework.repository.UserRepository
import com.enterprise.framework.service.PasswordHashingService
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import java.io.File
import java.net.URI

/**
 * CLI Context for managing CLI state and services
 */
class CLIContext(
    val vertx: Vertx,
    val serverUrl: String,
    val configDir: File,
    val authenticator: CLIAuthenticator,
    val bootstrapManager: BootstrapManager
) {
    companion object {
        private var instance: CLIContext? = null

        fun initialize(serverUrl: String = "http://localhost:8080"): CLIContext {
            if (instance == null) {
                val vertx = Vertx.vertx()
                val configDir = File(System.getProperty("user.home"), ".esf")
                configDir.mkdirs()

                // Load config
                val config = runBlocking {
                    try {
                        val configLoader = ConfigLoader(vertx)
                        configLoader.loadConfig()
                    } catch (e: Exception) {
                        JsonObject() // Use empty config if file not found
                    }
                }

                // Initialize repositories
                val dbConfig = DatabaseConfig(
                    dynamoDbEndpoint = config.getJsonObject("database")?.getJsonObject("dynamodb")?.getString("endpoint") ?: "http://localhost:8000",
                    region = config.getJsonObject("database")?.getJsonObject("dynamodb")?.getString("region") ?: "us-east-1",
                    tableName = config.getJsonObject("database")?.getJsonObject("dynamodb")?.getString("tableName") ?: "enterprise-data"
                )
                
                // Create DynamoDB client
                val dynamoDbClient = DynamoDbAsyncClient.builder()
                    .region(Region.of(dbConfig.region))
                    .endpointOverride(URI.create(dbConfig.dynamoDbEndpoint))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build()
                
                val userRepository = UserRepository(vertx, dynamoDbClient, dbConfig.tableName)
                val passwordHashingService = PasswordHashingService()
                val roleRepository = RoleRepository(vertx)
                val permissionRepository = PermissionRepository(vertx)
                val policyRepository = PolicyRepository(vertx)

                // Initialize services
                val authenticator = CLIAuthenticator(vertx, serverUrl, configDir)
                val bootstrapManager = BootstrapManager(
                    vertx,
                    userRepository,
                    passwordHashingService,
                    roleRepository,
                    permissionRepository,
                    policyRepository
                )

                instance = CLIContext(
                    vertx,
                    serverUrl,
                    configDir,
                    authenticator,
                    bootstrapManager
                )
            }
            return instance!!
        }

        fun getInstance(): CLIContext? = instance

        fun close() {
            instance?.vertx?.close()
            instance = null
        }
    }
}

