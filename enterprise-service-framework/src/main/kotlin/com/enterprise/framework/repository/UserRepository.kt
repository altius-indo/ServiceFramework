package com.enterprise.framework.repository

import com.enterprise.framework.model.Credential
import com.enterprise.framework.model.User
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import mu.KotlinLogging
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Repository for user and credential data stored in DynamoDB.
 *
 * This repository provides methods for creating, reading, updating, and deleting
 * user records and their associated credentials.
 *
 * @property vertx Vert.x instance
 * @property dynamoDb DynamoDB async client
 * @property tableName Name of the DynamoDB table
 */
class UserRepository(
    private val vertx: Vertx,
    private val dynamoDb: DynamoDbAsyncClient,
    private val tableName: String
) {

    /**
     * Creates a new user with credentials.
     *
     * @param user User to create
     * @param credential User's credentials
     * @return Future with the created user
     */
    fun createUser(user: User, credential: Credential): Future<User> {
        val promise = io.vertx.core.Promise.promise<User>()

        try {
            val item = mapOf(
                "PK" to AttributeValue.builder().s("USER#${user.userId}").build(),
                "SK" to AttributeValue.builder().s("PROFILE").build(),
                "userId" to AttributeValue.builder().s(user.userId).build(),
                "username" to AttributeValue.builder().s(user.username).build(),
                "email" to AttributeValue.builder().s(user.email).build(),
                "roles" to AttributeValue.builder().ss(user.roles.toList()).build(),
                "enabled" to AttributeValue.builder().bool(user.enabled).build(),
                "locked" to AttributeValue.builder().bool(user.locked).build(),
                "mfaEnabled" to AttributeValue.builder().bool(user.mfaEnabled).build(),
                "createdAt" to AttributeValue.builder().s(user.createdAt.toString()).build(),
                "updatedAt" to AttributeValue.builder().s(user.updatedAt.toString()).build(),
                "failedLoginAttempts" to AttributeValue.builder().n(user.failedLoginAttempts.toString()).build(),
                "passwordHash" to AttributeValue.builder().s(credential.passwordHash).build(),
                "algorithm" to AttributeValue.builder().s(credential.algorithm).build(),
                "salt" to AttributeValue.builder().s(credential.salt).build()
            )

            val request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(PK)")
                .build()

            dynamoDb.putItem(request).whenComplete { _, error ->
                if (error != null) {
                    logger.error(error) { "Failed to create user: ${user.username}" }
                    promise.fail(error)
                } else {
                    logger.info { "User created successfully: ${user.username}" }
                    promise.complete(user)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create user item" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Finds a user by user ID.
     *
     * @param userId User ID
     * @return Future with the user, or null if not found
     */
    fun findByUserId(userId: String): Future<User?> {
        val promise = io.vertx.core.Promise.promise<User?>()

        try {
            val key = mapOf(
                "PK" to AttributeValue.builder().s("USER#$userId").build(),
                "SK" to AttributeValue.builder().s("PROFILE").build()
            )

            val request = GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build()

            dynamoDb.getItem(request).whenComplete { response, error ->
                if (error != null) {
                    logger.error(error) { "Failed to find user by ID: $userId" }
                    promise.fail(error)
                } else if (response.hasItem()) {
                    val user = mapItemToUser(response.item())
                    promise.complete(user)
                } else {
                    promise.complete(null)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to query user" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Finds a user by username.
     *
     * @param username Username
     * @return Future with the user and credential, or null if not found
     */
    fun findByUsername(username: String): Future<Pair<User, Credential>?> {
        val promise = io.vertx.core.Promise.promise<Pair<User, Credential>?>()

        try {
            val request = QueryRequest.builder()
                .tableName(tableName)
                .indexName("username-index")
                .keyConditionExpression("username = :username")
                .expressionAttributeValues(
                    mapOf(":username" to AttributeValue.builder().s(username).build())
                )
                .build()

            dynamoDb.query(request).whenComplete { response, error ->
                if (error != null) {
                    logger.error(error) { "Failed to find user by username: $username" }
                    promise.fail(error)
                } else if (response.hasItems() && response.items().isNotEmpty()) {
                    val item = response.items().first()
                    val user = mapItemToUser(item)
                    val credential = mapItemToCredential(item)
                    promise.complete(Pair(user, credential))
                } else {
                    promise.complete(null)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to query user by username" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Updates a user.
     *
     * @param user User to update
     * @return Future with the updated user
     */
    fun updateUser(user: User): Future<User> {
        val promise = io.vertx.core.Promise.promise<User>()

        try {
            val updatedUser = user.copy(updatedAt = Instant.now())

            val request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(
                    mapOf(
                        "PK" to AttributeValue.builder().s("USER#${user.userId}").build(),
                        "SK" to AttributeValue.builder().s("PROFILE").build()
                    )
                )
                .updateExpression(
                    "SET #enabled = :enabled, #locked = :locked, #mfaEnabled = :mfaEnabled, " +
                            "#lastLoginAt = :lastLoginAt, #failedLoginAttempts = :failedLoginAttempts, " +
                            "#lockedUntil = :lockedUntil, #updatedAt = :updatedAt"
                )
                .expressionAttributeNames(
                    mapOf(
                        "#enabled" to "enabled",
                        "#locked" to "locked",
                        "#mfaEnabled" to "mfaEnabled",
                        "#lastLoginAt" to "lastLoginAt",
                        "#failedLoginAttempts" to "failedLoginAttempts",
                        "#lockedUntil" to "lockedUntil",
                        "#updatedAt" to "updatedAt"
                    )
                )
                .expressionAttributeValues(
                    mapOf(
                        ":enabled" to AttributeValue.builder().bool(updatedUser.enabled).build(),
                        ":locked" to AttributeValue.builder().bool(updatedUser.locked).build(),
                        ":mfaEnabled" to AttributeValue.builder().bool(updatedUser.mfaEnabled).build(),
                        ":lastLoginAt" to AttributeValue.builder().s(updatedUser.lastLoginAt?.toString() ?: "").build(),
                        ":failedLoginAttempts" to AttributeValue.builder().n(updatedUser.failedLoginAttempts.toString()).build(),
                        ":lockedUntil" to AttributeValue.builder().s(updatedUser.lockedUntil?.toString() ?: "").build(),
                        ":updatedAt" to AttributeValue.builder().s(updatedUser.updatedAt.toString()).build()
                    )
                )
                .build()

            dynamoDb.updateItem(request).whenComplete { _, error ->
                if (error != null) {
                    logger.error(error) { "Failed to update user: ${user.username}" }
                    promise.fail(error)
                } else {
                    logger.info { "User updated successfully: ${user.username}" }
                    promise.complete(updatedUser)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to update user" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Updates user credentials.
     *
     * @param userId User ID
     * @param credential New credentials
     * @return Future indicating success
     */
    fun updateCredentials(userId: String, credential: Credential): Future<Void> {
        val promise = io.vertx.core.Promise.promise<Void>()

        try {
            val request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(
                    mapOf(
                        "PK" to AttributeValue.builder().s("USER#$userId").build(),
                        "SK" to AttributeValue.builder().s("PROFILE").build()
                    )
                )
                .updateExpression(
                    "SET passwordHash = :passwordHash, algorithm = :algorithm, " +
                            "salt = :salt, passwordChangedAt = :passwordChangedAt"
                )
                .expressionAttributeValues(
                    mapOf(
                        ":passwordHash" to AttributeValue.builder().s(credential.passwordHash).build(),
                        ":algorithm" to AttributeValue.builder().s(credential.algorithm).build(),
                        ":salt" to AttributeValue.builder().s(credential.salt).build(),
                        ":passwordChangedAt" to AttributeValue.builder().s(Instant.now().toString()).build()
                    )
                )
                .build()

            dynamoDb.updateItem(request).whenComplete { _, error ->
                if (error != null) {
                    logger.error(error) { "Failed to update credentials for user: $userId" }
                    promise.fail(error)
                } else {
                    logger.info { "Credentials updated successfully for user: $userId" }
                    promise.complete()
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to update credentials" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Maps a DynamoDB item to a User object.
     */
    private fun mapItemToUser(item: Map<String, AttributeValue>): User {
        return User(
            userId = item["userId"]?.s() ?: "",
            username = item["username"]?.s() ?: "",
            email = item["email"]?.s() ?: "",
            roles = item["roles"]?.ss()?.toSet() ?: emptySet(),
            enabled = item["enabled"]?.bool() ?: true,
            locked = item["locked"]?.bool() ?: false,
            mfaEnabled = item["mfaEnabled"]?.bool() ?: false,
            mfaSecret = item["mfaSecret"]?.s(),
            createdAt = item["createdAt"]?.s()?.let { Instant.parse(it) } ?: Instant.now(),
            updatedAt = item["updatedAt"]?.s()?.let { Instant.parse(it) } ?: Instant.now(),
            lastLoginAt = item["lastLoginAt"]?.s()?.takeIf { it.isNotEmpty() }?.let { Instant.parse(it) },
            failedLoginAttempts = item["failedLoginAttempts"]?.n()?.toIntOrNull() ?: 0,
            lockedUntil = item["lockedUntil"]?.s()?.takeIf { it.isNotEmpty() }?.let { Instant.parse(it) },
            passwordChangedAt = item["passwordChangedAt"]?.s()?.takeIf { it.isNotEmpty() }?.let { Instant.parse(it) }
        )
    }

    /**
     * Maps a DynamoDB item to a Credential object.
     */
    private fun mapItemToCredential(item: Map<String, AttributeValue>): Credential {
        return Credential(
            userId = item["userId"]?.s() ?: "",
            passwordHash = item["passwordHash"]?.s() ?: "",
            algorithm = item["algorithm"]?.s() ?: "argon2id",
            salt = item["salt"]?.s() ?: ""
        )
    }
}
