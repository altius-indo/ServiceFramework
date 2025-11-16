package com.enterprise.framework.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PasswordHashingServiceTest : DescribeSpec({

    lateinit var service: PasswordHashingService

    beforeEach {
        service = PasswordHashingService()
    }

    afterEach {
        service.cleanup()
    }

    describe("PasswordHashingService") {

        describe("hashPassword") {

            it("should hash a password successfully") {
                val password = "SecurePassword123!"
                val (hash, salt) = service.hashPassword(password)

                hash shouldNotBe null
                hash shouldNotBe password
                salt shouldNotBe null
                (hash.startsWith("\$argon2")) shouldBe true
            }

            it("should generate different hashes for the same password") {
                val password = "SecurePassword123!"
                val (hash1, salt1) = service.hashPassword(password)
                val (hash2, salt2) = service.hashPassword(password)

                hash1 shouldNotBe hash2
                salt1 shouldNotBe salt2
            }

            it("should handle empty password") {
                val password = ""
                val (hash, salt) = service.hashPassword(password)

                hash shouldNotBe null
                salt shouldNotBe null
                (hash.startsWith("\$argon2")) shouldBe true
            }

            it("should handle special characters in password") {
                val password = "P@ssw0rd!#\$%^&*()"
                val (hash, salt) = service.hashPassword(password)

                hash shouldNotBe null
                salt shouldNotBe null
                (hash.startsWith("\$argon2")) shouldBe true
            }
        }

        describe("verifyPassword") {

            it("should verify correct password") {
                val password = "SecurePassword123!"
                val (hash, _) = service.hashPassword(password)

                val result = service.verifyPassword(password, hash)

                result shouldBe true
            }

            it("should reject incorrect password") {
                val correctPassword = "SecurePassword123!"
                val incorrectPassword = "WrongPassword456!"
                val (hash, _) = service.hashPassword(correctPassword)

                val result = service.verifyPassword(incorrectPassword, hash)

                result shouldBe false
            }

            it("should reject empty password when hash is not empty") {
                val password = "SecurePassword123!"
                val (hash, _) = service.hashPassword(password)

                val result = service.verifyPassword("", hash)

                result shouldBe false
            }

            it("should handle case-sensitive verification") {
                val password = "SecurePassword123!"
                val (hash, _) = service.hashPassword(password)

                val result = service.verifyPassword("securepassword123!", hash)

                result shouldBe false
            }
        }

        describe("needsRehash") {

            it("should return false for freshly hashed password") {
                val password = "SecurePassword123!"
                val (hash, _) = service.hashPassword(password)

                val result = service.needsRehash(hash)

                result shouldBe false
            }
        }
    }
})
