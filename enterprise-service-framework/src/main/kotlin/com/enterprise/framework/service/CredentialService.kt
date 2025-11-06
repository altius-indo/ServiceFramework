package com.enterprise.framework.service

import com.enterprise.framework.model.Credential
import com.enterprise.framework.model.PasswordPolicy
import com.enterprise.framework.repository.UserRepository
import io.vertx.core.Future
import io.vertx.core.Promise
import mu.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Service for managing user credentials including password creation,
 * validation, and policy enforcement.
 *
 * @property passwordHashingService Service for hashing passwords
 * @property passwordPolicyValidator Service for validating passwords
 * @property userRepository Repository for user data
 */
class CredentialService(
    private val passwordHashingService: PasswordHashingService,
    private val passwordPolicyValidator: PasswordPolicyValidator,
    private val userRepository: UserRepository
) {

    /**
     * Creates new credentials for a user after validating the password.
     *
     * @param userId User ID
     * @param password Plaintext password
     * @return Future with the created credential
     */
    fun createCredentials(userId: String, password: String): Future<Credential> {
        val promise = Promise.promise<Credential>()

        try {
            // Validate password against policy
            val validationResult = passwordPolicyValidator.validate(password)

            when (validationResult) {
                is PasswordPolicyValidator.ValidationResult.Success -> {
                    // Hash the password
                    val (hash, salt) = passwordHashingService.hashPassword(password)

                    val credential = Credential(
                        userId = userId,
                        passwordHash = hash,
                        algorithm = "argon2id",
                        salt = salt,
                        createdAt = Instant.now()
                    )

                    logger.info { "Credentials created for user: $userId" }
                    promise.complete(credential)
                }
                is PasswordPolicyValidator.ValidationResult.Failure -> {
                    logger.warn { "Password validation failed: ${validationResult.errors.joinToString("; ")}" }
                    promise.fail(
                        PasswordValidationException(
                            "Password does not meet policy requirements",
                            validationResult.errors
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create credentials" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Verifies a password against stored credentials.
     *
     * @param password Plaintext password to verify
     * @param credential Stored credentials
     * @return Future with true if password is correct, false otherwise
     */
    fun verifyPassword(password: String, credential: Credential): Future<Boolean> {
        val promise = Promise.promise<Boolean>()

        try {
            val isValid = passwordHashingService.verifyPassword(password, credential.passwordHash)

            if (isValid) {
                logger.debug { "Password verification successful" }

                // Check if password hash needs rehashing (algorithm upgrade)
                if (passwordHashingService.needsRehash(credential.passwordHash)) {
                    logger.info { "Password hash needs rehashing for user: ${credential.userId}" }
                    // Note: Rehashing should be done during the next password change
                }
            } else {
                logger.debug { "Password verification failed" }
            }

            promise.complete(isValid)
        } catch (e: Exception) {
            logger.error(e) { "Failed to verify password" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Changes a user's password.
     *
     * @param userId User ID
     * @param currentPassword Current password for verification
     * @param newPassword New password
     * @param currentCredential Current stored credentials
     * @return Future indicating success
     */
    fun changePassword(
        userId: String,
        currentPassword: String,
        newPassword: String,
        currentCredential: Credential
    ): Future<Void> {
        val promise = Promise.promise<Void>()

        verifyPassword(currentPassword, currentCredential)
            .compose { isValid ->
                if (!isValid) {
                    return@compose Future.failedFuture<Void>(
                        PasswordVerificationException("Current password is incorrect")
                    )
                }

                // Validate new password
                val validationResult = passwordPolicyValidator.validate(newPassword)

                when (validationResult) {
                    is PasswordPolicyValidator.ValidationResult.Success -> {
                        // Check password reuse
                        val (newHash, newSalt) = passwordHashingService.hashPassword(newPassword)

                        if (passwordPolicyValidator.isPasswordReused(newHash, currentCredential.passwordHistory)) {
                            return@compose Future.failedFuture<Void>(
                                PasswordReuseException("Password has been used recently and cannot be reused")
                            )
                        }

                        // Create new credential with updated history
                        val newCredential = Credential(
                            userId = userId,
                            passwordHash = newHash,
                            algorithm = "argon2id",
                            salt = newSalt,
                            passwordHistory = (currentCredential.passwordHistory + currentCredential.passwordHash)
                                .takeLast(5), // Keep last 5 passwords
                            createdAt = Instant.now()
                        )

                        userRepository.updateCredentials(userId, newCredential)
                    }
                    is PasswordPolicyValidator.ValidationResult.Failure -> {
                        Future.failedFuture(
                            PasswordValidationException(
                                "New password does not meet policy requirements",
                                validationResult.errors
                            )
                        )
                    }
                }
            }
            .onSuccess {
                logger.info { "Password changed successfully for user: $userId" }
                promise.complete()
            }
            .onFailure { error ->
                logger.error(error) { "Failed to change password for user: $userId" }
                promise.fail(error)
            }

        return promise.future()
    }

    /**
     * Resets a user's password (admin or forgot password flow).
     *
     * @param userId User ID
     * @param newPassword New password
     * @param currentCredential Current stored credentials
     * @return Future indicating success
     */
    fun resetPassword(
        userId: String,
        newPassword: String,
        currentCredential: Credential
    ): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // Validate new password
            val validationResult = passwordPolicyValidator.validate(newPassword)

            when (validationResult) {
                is PasswordPolicyValidator.ValidationResult.Success -> {
                    // Hash the new password
                    val (newHash, newSalt) = passwordHashingService.hashPassword(newPassword)

                    // Check password reuse
                    if (passwordPolicyValidator.isPasswordReused(newHash, currentCredential.passwordHistory)) {
                        promise.fail(
                            PasswordReuseException("Password has been used recently and cannot be reused")
                        )
                        return promise.future()
                    }

                    // Create new credential
                    val newCredential = Credential(
                        userId = userId,
                        passwordHash = newHash,
                        algorithm = "argon2id",
                        salt = newSalt,
                        passwordHistory = (currentCredential.passwordHistory + currentCredential.passwordHash)
                            .takeLast(5),
                        createdAt = Instant.now()
                    )

                    userRepository.updateCredentials(userId, newCredential)
                        .onSuccess {
                            logger.info { "Password reset successfully for user: $userId" }
                            promise.complete()
                        }
                        .onFailure { error ->
                            logger.error(error) { "Failed to reset password" }
                            promise.fail(error)
                        }
                }
                is PasswordPolicyValidator.ValidationResult.Failure -> {
                    logger.warn { "Password validation failed: ${validationResult.errors.joinToString("; ")}" }
                    promise.fail(
                        PasswordValidationException(
                            "Password does not meet policy requirements",
                            validationResult.errors
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to reset password" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Checks if a password is expired based on policy.
     *
     * @param credential Credential to check
     * @param policy Password policy
     * @return True if password is expired, false otherwise
     */
    fun isPasswordExpired(credential: Credential, policy: PasswordPolicy): Boolean {
        if (policy.expirationDays == null) {
            return false
        }

        val expiresAt = credential.createdAt.plusSeconds(policy.expirationDays.toLong() * 86400)
        return expiresAt.isBefore(Instant.now())
    }
}

/**
 * Exception thrown when password validation fails.
 */
class PasswordValidationException(
    message: String,
    val errors: List<String> = emptyList()
) : Exception(message)

/**
 * Exception thrown when password verification fails.
 */
class PasswordVerificationException(message: String) : Exception(message)

/**
 * Exception thrown when password reuse is detected.
 */
class PasswordReuseException(message: String) : Exception(message)
