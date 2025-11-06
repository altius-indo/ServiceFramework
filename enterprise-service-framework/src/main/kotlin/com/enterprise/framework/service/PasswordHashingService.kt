package com.enterprise.framework.service

import de.mkammerer.argon2.Argon2
import de.mkammerer.argon2.Argon2Factory
import mu.KotlinLogging
import java.security.SecureRandom
import java.util.Base64

private val logger = KotlinLogging.logger {}

/**
 * Service for secure password hashing and verification using Argon2id.
 *
 * This service provides methods for hashing passwords using the Argon2id algorithm,
 * which is recommended by OWASP for password storage. It also supports verification
 * of passwords against stored hashes.
 */
class PasswordHashingService {
    private val argon2: Argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    private val secureRandom = SecureRandom()

    companion object {
        private const val ITERATIONS = 3
        private const val MEMORY_KB = 65536 // 64 MB
        private const val PARALLELISM = 4
        private const val SALT_LENGTH = 16
    }

    /**
     * Hashes a password using Argon2id algorithm.
     *
     * @param password The plaintext password to hash
     * @return A tuple containing the hash and the salt used (both Base64-encoded)
     */
    fun hashPassword(password: String): Pair<String, String> {
        try {
            val salt = generateSalt()
            val hash = argon2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, password.toCharArray())

            logger.debug { "Password hashed successfully using Argon2id" }

            return Pair(hash, Base64.getEncoder().encodeToString(salt))
        } catch (e: Exception) {
            logger.error(e) { "Failed to hash password" }
            throw PasswordHashingException("Failed to hash password", e)
        }
    }

    /**
     * Verifies a password against a stored hash.
     *
     * @param password The plaintext password to verify
     * @param hash The stored hash to verify against
     * @return True if the password matches the hash, false otherwise
     */
    fun verifyPassword(password: String, hash: String): Boolean {
        try {
            val result = argon2.verify(hash, password.toCharArray())

            if (result) {
                logger.debug { "Password verification successful" }
            } else {
                logger.debug { "Password verification failed" }
            }

            return result
        } catch (e: Exception) {
            logger.error(e) { "Failed to verify password" }
            return false
        }
    }

    /**
     * Checks if a hash needs rehashing (e.g., due to updated parameters).
     *
     * @param hash The hash to check
     * @return True if the hash needs rehashing, false otherwise
     */
    fun needsRehash(hash: String): Boolean {
        return try {
            argon2.needsRehash(hash, ITERATIONS, MEMORY_KB, PARALLELISM)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to check if hash needs rehashing" }
            false
        }
    }

    /**
     * Generates a cryptographically secure random salt.
     *
     * @return A byte array containing the salt
     */
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Cleans up resources. Should be called when the service is no longer needed.
     */
    fun cleanup() {
        try {
            argon2.wipeArray(CharArray(0)) // Clean up any remaining memory
        } catch (e: Exception) {
            logger.warn(e) { "Failed to cleanup Argon2 resources" }
        }
    }
}

/**
 * Exception thrown when password hashing fails.
 */
class PasswordHashingException(message: String, cause: Throwable? = null) : Exception(message, cause)
