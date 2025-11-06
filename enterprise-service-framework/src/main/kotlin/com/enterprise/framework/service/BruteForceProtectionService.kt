package com.enterprise.framework.service

import com.enterprise.framework.model.BruteForceConfig
import com.enterprise.framework.model.User
import com.enterprise.framework.repository.UserRepository
import io.vertx.core.Future
import mu.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Service for protecting against brute force attacks on authentication.
 *
 * This service tracks failed login attempts and locks accounts when
 * the configured threshold is exceeded.
 *
 * @property userRepository User repository for updating user records
 * @property config Brute force protection configuration
 */
class BruteForceProtectionService(
    private val userRepository: UserRepository,
    private val config: BruteForceConfig
) {

    /**
     * Records a failed login attempt for a user.
     *
     * @param user User who failed authentication
     * @return Future with updated user (potentially locked)
     */
    fun recordFailedAttempt(user: User): Future<User> {
        if (!config.enabled) {
            return Future.succeededFuture(user)
        }

        val updatedAttempts = user.failedLoginAttempts + 1
        val shouldLock = updatedAttempts >= config.maxFailedAttempts

        val updatedUser = if (shouldLock) {
            val lockedUntil = Instant.now().plusSeconds(config.lockoutDurationSeconds)
            logger.warn { "Account locked due to too many failed attempts: ${user.username} (attempts: $updatedAttempts)" }

            user.copy(
                failedLoginAttempts = updatedAttempts,
                locked = true,
                lockedUntil = lockedUntil
            )
        } else {
            logger.debug { "Failed login attempt recorded for ${user.username} (attempts: $updatedAttempts)" }

            user.copy(
                failedLoginAttempts = updatedAttempts
            )
        }

        return userRepository.updateUser(updatedUser)
    }

    /**
     * Resets failed login attempts for a user (after successful login).
     *
     * @param user User who successfully authenticated
     * @return Future with updated user
     */
    fun resetFailedAttempts(user: User): Future<User> {
        if (!config.resetAfterSuccessfulLogin || user.failedLoginAttempts == 0) {
            return Future.succeededFuture(user)
        }

        logger.debug { "Resetting failed attempts for ${user.username}" }

        val updatedUser = user.copy(
            failedLoginAttempts = 0,
            locked = false,
            lockedUntil = null
        )

        return userRepository.updateUser(updatedUser)
    }

    /**
     * Checks if a user account is currently locked.
     *
     * @param user User to check
     * @return True if account is locked and lockout period hasn't expired
     */
    fun isAccountLocked(user: User): Boolean {
        if (!config.enabled || !user.locked) {
            return false
        }

        // Check if lockout period has expired
        val lockedUntil = user.lockedUntil
        if (lockedUntil != null && lockedUntil.isBefore(Instant.now())) {
            logger.info { "Lockout period expired for ${user.username}, account will be unlocked on next login attempt" }
            return false
        }

        return true
    }

    /**
     * Unlocks a user account.
     *
     * @param user User to unlock
     * @return Future with updated user
     */
    fun unlockAccount(user: User): Future<User> {
        if (!user.locked) {
            return Future.succeededFuture(user)
        }

        logger.info { "Unlocking account: ${user.username}" }

        val updatedUser = user.copy(
            locked = false,
            lockedUntil = null,
            failedLoginAttempts = 0
        )

        return userRepository.updateUser(updatedUser)
    }

    /**
     * Manually locks a user account (e.g., for administrative action).
     *
     * @param user User to lock
     * @param durationSeconds Duration to lock the account in seconds
     * @return Future with updated user
     */
    fun lockAccount(user: User, durationSeconds: Long): Future<User> {
        logger.warn { "Manually locking account: ${user.username}" }

        val lockedUntil = Instant.now().plusSeconds(durationSeconds)

        val updatedUser = user.copy(
            locked = true,
            lockedUntil = lockedUntil
        )

        return userRepository.updateUser(updatedUser)
    }

    /**
     * Gets the remaining lockout duration for a locked account.
     *
     * @param user User to check
     * @return Remaining lockout duration in seconds, or 0 if not locked
     */
    fun getRemainingLockoutDuration(user: User): Long {
        if (!user.locked || user.lockedUntil == null) {
            return 0
        }

        val now = Instant.now()
        val remaining = user.lockedUntil.epochSecond - now.epochSecond

        return remaining.coerceAtLeast(0)
    }

    /**
     * Checks if a user should be automatically unlocked.
     *
     * @param user User to check
     * @return Future with updated user if unlocked, original user otherwise
     */
    fun checkAndUnlockIfExpired(user: User): Future<User> {
        if (!user.locked || user.lockedUntil == null) {
            return Future.succeededFuture(user)
        }

        if (user.lockedUntil.isBefore(Instant.now())) {
            logger.info { "Auto-unlocking expired account: ${user.username}" }
            return unlockAccount(user)
        }

        return Future.succeededFuture(user)
    }
}
