package com.enterprise.framework.service

import com.enterprise.framework.model.JwtConfig
import io.vertx.core.Vertx
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import mu.KotlinLogging
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import java.io.StringReader
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.util.Base64

private val logger = KotlinLogging.logger {}

/**
 * Service for creating and managing JWT authentication providers with support
 * for multiple signing algorithms (HS256, RS256, ES256).
 *
 * This service handles key generation, loading, and configuration of JWT
 * authentication based on the algorithm specified in the configuration.
 */
class JwtKeyProvider(private val vertx: Vertx) {

    /**
     * Creates a JWTAuth instance based on the configuration.
     *
     * @param config JWT configuration
     * @return Configured JWTAuth instance
     */
    fun createJwtAuth(config: JwtConfig): JWTAuth {
        return when (config.algorithm.uppercase()) {
            "HS256" -> createHS256Auth(config)
            "RS256" -> createRS256Auth(config)
            "ES256" -> createES256Auth(config)
            else -> {
                logger.warn { "Unsupported algorithm: ${config.algorithm}, falling back to HS256" }
                createHS256Auth(config)
            }
        }
    }

    /**
     * Creates a JWTAuth instance for HS256 (HMAC with SHA-256).
     *
     * @param config JWT configuration
     * @return JWTAuth configured for HS256
     */
    private fun createHS256Auth(config: JwtConfig): JWTAuth {
        logger.info { "Creating JWT auth with HS256 algorithm" }

        val jwtAuthOptions = JWTAuthOptions()
            .addPubSecKey(
                PubSecKeyOptions()
                    .setAlgorithm("HS256")
                    .setBuffer(config.secret)
            )

        return JWTAuth.create(vertx, jwtAuthOptions)
    }

    /**
     * Creates a JWTAuth instance for RS256 (RSA with SHA-256).
     *
     * @param config JWT configuration
     * @return JWTAuth configured for RS256
     */
    private fun createRS256Auth(config: JwtConfig): JWTAuth {
        logger.info { "Creating JWT auth with RS256 algorithm" }

        val keyPair = if (config.publicKey != null && config.privateKey != null) {
            // Load keys from PEM format
            logger.info { "Loading RSA keys from configuration" }
            loadRSAKeysFromPEM(config.publicKey, config.privateKey)
        } else {
            // Generate new RSA key pair
            logger.warn { "No RSA keys configured, generating new key pair (not recommended for production)" }
            generateRSAKeyPair()
        }

        val publicKey = keyPair.public
        val privateKey = keyPair.private

        val jwtAuthOptions = JWTAuthOptions()
            .addPubSecKey(
                PubSecKeyOptions()
                    .setAlgorithm("RS256")
                    .setBuffer(publicKeyToPEM(publicKey))
            )
            .addPubSecKey(
                PubSecKeyOptions()
                    .setAlgorithm("RS256")
                    .setBuffer(privateKeyToPEM(privateKey))
            )

        return JWTAuth.create(vertx, jwtAuthOptions)
    }

    /**
     * Creates a JWTAuth instance for ES256 (ECDSA with SHA-256).
     *
     * @param config JWT configuration
     * @return JWTAuth configured for ES256
     */
    private fun createES256Auth(config: JwtConfig): JWTAuth {
        logger.info { "Creating JWT auth with ES256 algorithm" }

        val keyPair = if (config.publicKey != null && config.privateKey != null) {
            // Load keys from PEM format
            logger.info { "Loading EC keys from configuration" }
            loadECKeysFromPEM(config.publicKey, config.privateKey)
        } else {
            // Generate new EC key pair
            logger.warn { "No EC keys configured, generating new key pair (not recommended for production)" }
            generateECKeyPair()
        }

        val publicKey = keyPair.public
        val privateKey = keyPair.private

        val jwtAuthOptions = JWTAuthOptions()
            .addPubSecKey(
                PubSecKeyOptions()
                    .setAlgorithm("ES256")
                    .setBuffer(publicKeyToPEM(publicKey))
            )
            .addPubSecKey(
                PubSecKeyOptions()
                    .setAlgorithm("ES256")
                    .setBuffer(privateKeyToPEM(privateKey))
            )

        return JWTAuth.create(vertx, jwtAuthOptions)
    }

    /**
     * Generates an RSA key pair for RS256 signing.
     *
     * @return KeyPair containing public and private keys
     */
    private fun generateRSAKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048, SecureRandom())
        return keyGen.generateKeyPair()
    }

    /**
     * Generates an EC key pair for ES256 signing.
     *
     * @return KeyPair containing public and private keys
     */
    private fun generateECKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256, SecureRandom())
        return keyGen.generateKeyPair()
    }

    /**
     * Loads RSA keys from PEM format strings.
     *
     * @param publicKeyPEM Public key in PEM format
     * @param privateKeyPEM Private key in PEM format
     * @return KeyPair containing the loaded keys
     */
    private fun loadRSAKeysFromPEM(publicKeyPEM: String, privateKeyPEM: String): KeyPair {
        val publicKey = loadPublicKeyFromPEM(publicKeyPEM)
        val privateKey = loadPrivateKeyFromPEM(privateKeyPEM)
        return KeyPair(publicKey, privateKey)
    }

    /**
     * Loads EC keys from PEM format strings.
     *
     * @param publicKeyPEM Public key in PEM format
     * @param privateKeyPEM Private key in PEM format
     * @return KeyPair containing the loaded keys
     */
    private fun loadECKeysFromPEM(publicKeyPEM: String, privateKeyPEM: String): KeyPair {
        val publicKey = loadPublicKeyFromPEM(publicKeyPEM)
        val privateKey = loadPrivateKeyFromPEM(privateKeyPEM)
        return KeyPair(publicKey, privateKey)
    }

    /**
     * Loads a public key from PEM format.
     *
     * @param pem Public key in PEM format
     * @return PublicKey instance
     */
    private fun loadPublicKeyFromPEM(pem: String): PublicKey {
        val parser = PEMParser(StringReader(pem))
        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(parser.readObject())
        parser.close()

        val converter = JcaPEMKeyConverter()
        return converter.getPublicKey(publicKeyInfo)
    }

    /**
     * Loads a private key from PEM format.
     *
     * @param pem Private key in PEM format
     * @return PrivateKey instance
     */
    private fun loadPrivateKeyFromPEM(pem: String): PrivateKey {
        val parser = PEMParser(StringReader(pem))
        val privateKeyInfo = PrivateKeyInfo.getInstance(parser.readObject())
        parser.close()

        val converter = JcaPEMKeyConverter()
        return converter.getPrivateKey(privateKeyInfo)
    }

    /**
     * Converts a public key to PEM format.
     *
     * @param publicKey Public key to convert
     * @return PEM-encoded public key
     */
    private fun publicKeyToPEM(publicKey: PublicKey): String {
        val encoded = Base64.getEncoder().encode(publicKey.encoded)
        return "-----BEGIN PUBLIC KEY-----\n" +
                String(encoded).chunked(64).joinToString("\n") +
                "\n-----END PUBLIC KEY-----"
    }

    /**
     * Converts a private key to PEM format.
     *
     * @param privateKey Private key to convert
     * @return PEM-encoded private key
     */
    private fun privateKeyToPEM(privateKey: PrivateKey): String {
        val encoded = Base64.getEncoder().encode(privateKey.encoded)
        return "-----BEGIN PRIVATE KEY-----\n" +
                String(encoded).chunked(64).joinToString("\n") +
                "\n-----END PRIVATE KEY-----"
    }

    /**
     * Generates and logs a new RSA key pair for configuration purposes.
     * This is useful for initial setup.
     */
    fun generateAndPrintRSAKeys() {
        logger.info { "Generating RSA key pair..." }
        val keyPair = generateRSAKeyPair()

        val publicKeyPEM = publicKeyToPEM(keyPair.public)
        val privateKeyPEM = privateKeyToPEM(keyPair.private)

        logger.info { "Generated RSA Public Key:\n$publicKeyPEM" }
        logger.info { "Generated RSA Private Key:\n$privateKeyPEM" }
    }

    /**
     * Generates and logs a new EC key pair for configuration purposes.
     * This is useful for initial setup.
     */
    fun generateAndPrintECKeys() {
        logger.info { "Generating EC key pair..." }
        val keyPair = generateECKeyPair()

        val publicKeyPEM = publicKeyToPEM(keyPair.public)
        val privateKeyPEM = privateKeyToPEM(keyPair.private)

        logger.info { "Generated EC Public Key:\n$publicKeyPEM" }
        logger.info { "Generated EC Private Key:\n$privateKeyPEM" }
    }
}
