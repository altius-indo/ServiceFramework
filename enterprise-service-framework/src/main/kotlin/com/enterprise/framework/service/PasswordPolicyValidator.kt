package com.enterprise.framework.service

import com.enterprise.framework.model.PasswordPolicy
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Service for validating passwords against configured password policies.
 *
 * This service checks passwords against various complexity requirements including
 * length, character classes, and historical password reuse prevention.
 */
class PasswordPolicyValidator(private val policy: PasswordPolicy) {

    /**
     * Validates a password against the configured policy.
     *
     * @param password The password to validate
     * @param passwordHistory List of previous password hashes to check for reuse
     * @return ValidationResult indicating success or specific failures
     */
    fun validate(password: String, passwordHistory: List<String> = emptyList()): ValidationResult {
        val errors = mutableListOf<String>()

        // Check minimum length
        if (password.length < policy.minLength) {
            errors.add("Password must be at least ${policy.minLength} characters long")
        }

        // Check uppercase requirement
        if (policy.requireUppercase && !password.any { it.isUpperCase() }) {
            errors.add("Password must contain at least one uppercase letter")
        }

        // Check lowercase requirement
        if (policy.requireLowercase && !password.any { it.isLowerCase() }) {
            errors.add("Password must contain at least one lowercase letter")
        }

        // Check digit requirement
        if (policy.requireDigit && !password.any { it.isDigit() }) {
            errors.add("Password must contain at least one digit")
        }

        // Check special character requirement
        if (policy.requireSpecialChar && !containsSpecialCharacter(password)) {
            errors.add("Password must contain at least one special character (!@#\$%^&*(),.?\":{}|<>)")
        }

        if (errors.isEmpty()) {
            logger.debug { "Password validation successful" }
            return ValidationResult.Success
        } else {
            logger.debug { "Password validation failed: ${errors.joinToString("; ")}" }
            return ValidationResult.Failure(errors)
        }
    }

    /**
     * Checks if a password is in the reuse history.
     *
     * @param newPasswordHash The hash of the new password to check
     * @param passwordHistory List of previous password hashes
     * @return True if the password has been used recently, false otherwise
     */
    fun isPasswordReused(newPasswordHash: String, passwordHistory: List<String>): Boolean {
        val recentPasswords = passwordHistory.takeLast(policy.preventReuse)
        return newPasswordHash in recentPasswords
    }

    /**
     * Checks if the password contains at least one special character.
     *
     * @param password The password to check
     * @return True if the password contains a special character, false otherwise
     */
    private fun containsSpecialCharacter(password: String): Boolean {
        val specialChars = "!@#\$%^&*(),.?\":{}|<>-_=+[];\\/~`"
        return password.any { it in specialChars }
    }

    /**
     * Result of password validation.
     */
    sealed class ValidationResult {
        /**
         * Validation succeeded.
         */
        object Success : ValidationResult()

        /**
         * Validation failed with specific errors.
         *
         * @property errors List of validation error messages
         */
        data class Failure(val errors: List<String>) : ValidationResult()
    }
}
