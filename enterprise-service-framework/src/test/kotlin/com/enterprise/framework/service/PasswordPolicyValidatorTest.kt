package com.enterprise.framework.service

import com.enterprise.framework.model.PasswordPolicy
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class PasswordPolicyValidatorTest : DescribeSpec({

    lateinit var validator: PasswordPolicyValidator
    lateinit var policy: PasswordPolicy

    beforeEach {
        policy = PasswordPolicy(
            minLength = 12,
            requireUppercase = true,
            requireLowercase = true,
            requireDigit = true,
            requireSpecialChar = true,
            preventReuse = 5
        )
        validator = PasswordPolicyValidator(policy)
    }

    describe("PasswordPolicyValidator") {

        describe("validate") {

            it("should accept a valid password") {
                val password = "ValidPassword123!"
                val result = validator.validate(password)

                result.shouldBeInstanceOf<PasswordPolicyValidator.ValidationResult.Success>()
            }

            it("should reject password shorter than minimum length") {
                val password = "Short1!"
                val result = validator.validate(password)

                result.shouldBeInstanceOf<PasswordPolicyValidator.ValidationResult.Failure>()
                val failures = (result as PasswordPolicyValidator.ValidationResult.Failure).errors
                failures.any { it.contains("at least 12 characters") } shouldBe true
            }

            it("should reject password without uppercase letter") {
                val password = "validpassword123!"
                val result = validator.validate(password)

                result.shouldBeInstanceOf<PasswordPolicyValidator.ValidationResult.Failure>()
                val failures = (result as PasswordPolicyValidator.ValidationResult.Failure).errors
                failures.any { it.contains("uppercase letter") } shouldBe true
            }

            it("should reject password without lowercase letter") {
                val password = "VALIDPASSWORD123!"
                val result = validator.validate(password)

                result.shouldBeInstanceOf<PasswordPolicyValidator.ValidationResult.Failure>()
                val failures = (result as PasswordPolicyValidator.ValidationResult.Failure).errors
                failures.any { it.contains("lowercase letter") } shouldBe true
            }

            it("should reject password without digit") {
                val password = "ValidPassword!"
                val result = validator.validate(password)

                result.shouldBeInstanceOf<PasswordPolicyValidator.ValidationResult.Failure>()
                val failures = (result as PasswordPolicyValidator.ValidationResult.Failure).errors
                failures.any { it.contains("digit") } shouldBe true
            }

            it("should reject password without special character") {
                val password = "ValidPassword123"
                val result = validator.validate(password)

                result.shouldBeInstanceOf<PasswordPolicyValidator.ValidationResult.Failure>()
                val failures = (result as PasswordPolicyValidator.ValidationResult.Failure).errors
                failures.any { it.contains("special character") } shouldBe true
            }

            it("should accumulate multiple validation errors") {
                val password = "short"
                val result = validator.validate(password)

                result.shouldBeInstanceOf<PasswordPolicyValidator.ValidationResult.Failure>()
                val failures = (result as PasswordPolicyValidator.ValidationResult.Failure).errors
                failures.size shouldBe 4
            }

            it("should accept password with various special characters") {
                val passwords = listOf(
                    "ValidPassword123!",
                    "ValidPassword123@",
                    "ValidPassword123#",
                    "ValidPassword123\$",
                    "ValidPassword123%"
                )

                passwords.forEach { password ->
                    val result = validator.validate(password)
                    result.shouldBeInstanceOf<PasswordPolicyValidator.ValidationResult.Success>()
                }
            }
        }

        describe("validate with relaxed policy") {

            it("should accept simpler password when requirements are relaxed") {
                val relaxedPolicy = PasswordPolicy(
                    minLength = 8,
                    requireUppercase = false,
                    requireLowercase = true,
                    requireDigit = true,
                    requireSpecialChar = false,
                    preventReuse = 3
                )
                val relaxedValidator = PasswordPolicyValidator(relaxedPolicy)

                val password = "password123"
                val result = relaxedValidator.validate(password)

                result.shouldBeInstanceOf<PasswordPolicyValidator.ValidationResult.Success>()
            }
        }

        describe("isPasswordReused") {

            it("should reject password that exists in history") {
                val newPasswordHash = "\$argon2id\$password3"
                val passwordHistory = listOf(
                    "\$argon2id\$password1",
                    "\$argon2id\$password2",
                    "\$argon2id\$password3"
                )

                val isReused = validator.isPasswordReused(newPasswordHash, passwordHistory)

                isReused shouldBe true
            }

            it("should accept password not in history") {
                val newPasswordHash = "\$argon2id\$newpassword"
                val passwordHistory = listOf(
                    "\$argon2id\$password1",
                    "\$argon2id\$password2",
                    "\$argon2id\$password3"
                )

                val isReused = validator.isPasswordReused(newPasswordHash, passwordHistory)

                isReused shouldBe false
            }

            it("should accept password when history is empty") {
                val newPasswordHash = "\$argon2id\$password"
                val passwordHistory = emptyList<String>()

                val isReused = validator.isPasswordReused(newPasswordHash, passwordHistory)

                isReused shouldBe false
            }

            it("should only check recent history based on preventReuse policy") {
                val newPasswordHash = "\$argon2id\$password1"
                val passwordHistory = listOf(
                    "\$argon2id\$password1",  // This is outside the preventReuse window
                    "\$argon2id\$password2",
                    "\$argon2id\$password3",
                    "\$argon2id\$password4",
                    "\$argon2id\$password5",
                    "\$argon2id\$password6"
                )

                val isReused = validator.isPasswordReused(newPasswordHash, passwordHistory)

                isReused shouldBe false  // Only last 5 are checked, password1 is 6th from end
            }
        }
    }
})
