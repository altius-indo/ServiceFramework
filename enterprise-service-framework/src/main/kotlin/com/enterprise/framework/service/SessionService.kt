package com.enterprise.framework.service

import com.enterprise.framework.model.Session
import com.enterprise.framework.model.SessionConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.redis.client.RedisAPI
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Service for managing user sessions with Redis as the backing store.
 *
 * This service provides distributed session management with support for
 * session expiration, concurrent session limits, and session lifecycle management.
 *
 * @property redisApi Redis API client
 * @property config Session configuration
 */
class SessionService(
    private val redisApi: RedisAPI,
    private val config: SessionConfig
) {

    companion object {
        private const val SESSION_KEY_PREFIX = "session:"
        private const val USER_SESSIONS_KEY_PREFIX = "user:sessions:"
    }

    /**
     * Creates a new session for a user.
     *
     * @param userId User ID
     * @param accessToken Access token for this session
     * @param refreshToken Refresh token for this session
     * @param ipAddress IP address of the client
     * @param userAgent User agent string
     * @return Future with the created session
     */
    fun createSession(
        userId: String,
        accessToken: String,
        refreshToken: String,
        ipAddress: String?,
        userAgent: String?
    ): Future<Session> {
        val promise = Promise.promise<Session>()

        try {
            val sessionId = UUID.randomUUID().toString()
            val now = Instant.now()
            val expiresAt = now.plusSeconds(config.absoluteTimeoutSeconds)

            val session = Session(
                sessionId = sessionId,
                userId = userId,
                accessToken = accessToken,
                refreshToken = refreshToken,
                ipAddress = ipAddress,
                userAgent = userAgent,
                createdAt = now,
                lastAccessedAt = now,
                expiresAt = expiresAt,
                terminated = false
            )

            // Check concurrent session limit
            checkConcurrentSessions(userId)
                .compose { canCreate ->
                    if (!canCreate) {
                        return@compose Future.failedFuture<Session>(
                            SessionLimitExceededException("Maximum concurrent sessions exceeded")
                        )
                    }

                    // Store session in Redis
                    storeSession(session)
                }
                .onSuccess {
                    logger.info { "Session created for user: $userId (sessionId: $sessionId)" }
                    promise.complete(session)
                }
                .onFailure { error ->
                    logger.error(error) { "Failed to create session for user: $userId" }
                    promise.fail(error)
                }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create session" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Retrieves a session by session ID.
     *
     * @param sessionId Session ID
     * @return Future with the session, or null if not found
     */
    fun getSession(sessionId: String): Future<Session?> {
        val promise = Promise.promise<Session?>()

        try {
            val key = "$SESSION_KEY_PREFIX$sessionId"

            redisApi.get(key)
                .onSuccess { response ->
                    if (response == null) {
                        promise.complete(null)
                    } else {
                        val sessionJson = JsonObject(response.toString())
                        val session = jsonToSession(sessionJson)

                        // Check if session is expired or terminated
                        if (session.terminated || session.expiresAt.isBefore(Instant.now())) {
                            promise.complete(null)
                        } else {
                            promise.complete(session)
                        }
                    }
                }
                .onFailure { error ->
                    logger.error(error) { "Failed to get session: $sessionId" }
                    promise.fail(error)
                }
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve session" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Updates the last accessed time for a session.
     *
     * @param sessionId Session ID
     * @return Future indicating success
     */
    fun touchSession(sessionId: String): Future<Void> {
        val promise = Promise.promise<Void>()

        getSession(sessionId)
            .compose { session ->
                if (session == null) {
                    return@compose Future.failedFuture<Void>(SessionNotFoundException("Session not found"))
                }

                val updatedSession = session.copy(lastAccessedAt = Instant.now())

                if (config.renewOnActivity) {
                    // Extend session expiration
                    val newExpiresAt = Instant.now().plusSeconds(config.idleTimeoutSeconds)
                    val extended = updatedSession.copy(expiresAt = newExpiresAt)
                    storeSession(extended)
                } else {
                    storeSession(updatedSession)
                }
            }
            .onSuccess {
                logger.debug { "Session touched: $sessionId" }
                promise.complete()
            }
            .onFailure { error ->
                logger.error(error) { "Failed to touch session: $sessionId" }
                promise.fail(error)
            }

        return promise.future()
    }

    /**
     * Terminates a session.
     *
     * @param sessionId Session ID to terminate
     * @return Future indicating success
     */
    fun terminateSession(sessionId: String): Future<Void> {
        val promise = Promise.promise<Void>()

        getSession(sessionId)
            .compose { session ->
                if (session == null) {
                    return@compose Future.succeededFuture<Void>()
                }

                val terminated = session.copy(terminated = true)

                val sessionKey = "$SESSION_KEY_PREFIX$sessionId"
                val userSessionsKey = "$USER_SESSIONS_KEY_PREFIX${session.userId}"

                // Remove from session store and user sessions set
                Future.all(
                    redisApi.del(listOf(sessionKey)),
                    redisApi.srem(listOf(userSessionsKey, sessionId))
                ).mapEmpty<Void>()
            }
            .onSuccess {
                logger.info { "Session terminated: $sessionId" }
                promise.complete()
            }
            .onFailure { error ->
                logger.error(error) { "Failed to terminate session: $sessionId" }
                promise.fail(error)
            }

        return promise.future()
    }

    /**
     * Terminates all sessions for a user.
     *
     * @param userId User ID
     * @return Future indicating success
     */
    fun terminateAllUserSessions(userId: String): Future<Void> {
        val promise = Promise.promise<Void>()

        getUserSessions(userId)
            .compose { sessions ->
                val futures = sessions.map { session ->
                    terminateSession(session.sessionId)
                }

                if (futures.isEmpty()) {
                    Future.succeededFuture()
                } else {
                    Future.all(futures).mapEmpty<Void>()
                }
            }
            .onSuccess {
                logger.info { "All sessions terminated for user: $userId" }
                promise.complete()
            }
            .onFailure { error ->
                logger.error(error) { "Failed to terminate all sessions for user: $userId" }
                promise.fail(error)
            }

        return promise.future()
    }

    /**
     * Gets all active sessions for a user.
     *
     * @param userId User ID
     * @return Future with list of active sessions
     */
    fun getUserSessions(userId: String): Future<List<Session>> {
        val promise = Promise.promise<List<Session>>()

        try {
            val userSessionsKey = "$USER_SESSIONS_KEY_PREFIX$userId"

            redisApi.smembers(userSessionsKey)
                .compose { response ->
                    if (response == null || response.size() == 0) {
                        return@compose Future.succeededFuture(emptyList<Session>())
                    }

                    val sessionIds = (0 until response.size()).map { response.get(it).toString() }
                    val futures = sessionIds.map { sessionId -> getSession(sessionId) }

                    Future.all(futures)
                        .map { composite ->
                            composite.list<Session?>()
                                .filterNotNull()
                        }
                }
                .onSuccess { sessions ->
                    logger.debug { "Retrieved ${sessions.size} sessions for user: $userId" }
                    promise.complete(sessions)
                }
                .onFailure { error ->
                    logger.error(error) { "Failed to get user sessions: $userId" }
                    promise.fail(error)
                }
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve user sessions" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Stores a session in Redis.
     *
     * @param session Session to store
     * @return Future indicating success
     */
    private fun storeSession(session: Session): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            val sessionKey = "$SESSION_KEY_PREFIX${session.sessionId}"
            val userSessionsKey = "$USER_SESSIONS_KEY_PREFIX${session.userId}"
            val sessionJson = sessionToJson(session).encode()
            val ttl = (session.expiresAt.epochSecond - Instant.now().epochSecond).toInt()

            // Store session with TTL and add to user's session set
            Future.all(
                redisApi.setex(sessionKey, ttl.toString(), sessionJson),
                redisApi.sadd(listOf(userSessionsKey, session.sessionId)),
                redisApi.expire(listOf(userSessionsKey, ttl.toString()))
            )
                .onSuccess {
                    promise.complete()
                }
                .onFailure { error ->
                    promise.fail(error)
                }
        } catch (e: Exception) {
            logger.error(e) { "Failed to store session" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Checks if a user can create a new session based on concurrent session limits.
     *
     * @param userId User ID
     * @return Future with true if user can create a session, false otherwise
     */
    private fun checkConcurrentSessions(userId: String): Future<Boolean> {
        return getUserSessions(userId)
            .map { sessions ->
                sessions.size < config.maxConcurrentSessions
            }
            .recover { error ->
                logger.warn(error) { "Failed to check concurrent sessions, allowing creation" }
                Future.succeededFuture(true)
            }
    }

    /**
     * Converts a Session object to JSON.
     */
    private fun sessionToJson(session: Session): JsonObject {
        return JsonObject()
            .put("sessionId", session.sessionId)
            .put("userId", session.userId)
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("ipAddress", session.ipAddress)
            .put("userAgent", session.userAgent)
            .put("createdAt", session.createdAt.toString())
            .put("lastAccessedAt", session.lastAccessedAt.toString())
            .put("expiresAt", session.expiresAt.toString())
            .put("terminated", session.terminated)
    }

    /**
     * Converts JSON to a Session object.
     */
    private fun jsonToSession(json: JsonObject): Session {
        return Session(
            sessionId = json.getString("sessionId"),
            userId = json.getString("userId"),
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            ipAddress = json.getString("ipAddress"),
            userAgent = json.getString("userAgent"),
            createdAt = Instant.parse(json.getString("createdAt")),
            lastAccessedAt = Instant.parse(json.getString("lastAccessedAt")),
            expiresAt = Instant.parse(json.getString("expiresAt")),
            terminated = json.getBoolean("terminated", false)
        )
    }
}

/**
 * Exception thrown when session limit is exceeded.
 */
class SessionLimitExceededException(message: String) : Exception(message)

/**
 * Exception thrown when session is not found.
 */
class SessionNotFoundException(message: String) : Exception(message)
