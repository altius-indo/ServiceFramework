# Authentication System Documentation

This document describes the comprehensive authentication system implemented in the Enterprise Service Framework.

## Overview

The authentication system provides:
- **Secure Password Storage**: Argon2id hashing with configurable work factors
- **JWT Tokens**: Access and refresh token support with multiple algorithms (HS256, RS256, ES256)
- **Session Management**: Distributed sessions with Redis backend
- **Rate Limiting**: Token bucket algorithm with automatic blacklisting
- **Brute Force Protection**: Account lockout after failed attempts
- **Audit Logging**: Structured security event logging
- **Token Revocation**: Redis-backed token blacklisting

## API Endpoints

### Public Endpoints

#### POST /auth/login
Authenticates a user and returns access/refresh tokens.

**Request:**
```json
{
  "username": "user@example.com",
  "password": "SecurePassword123!"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "userId": "uuid",
    "username": "user@example.com",
    "email": "user@example.com",
    "roles": "user,admin"
  }
}
```

**Error Responses:**
- `400 Bad Request`: Missing username or password
- `401 Unauthorized`: Invalid credentials, account disabled, or account locked
- `429 Too Many Requests`: Rate limit exceeded

#### POST /auth/refresh
Refreshes an access token using a refresh token.

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**Error Responses:**
- `400 Bad Request`: Missing refresh token
- `401 Unauthorized`: Invalid or expired refresh token

#### POST /auth/introspect
Checks the validity of a token and returns metadata.

**Request:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK - Active Token):**
```json
{
  "active": true,
  "sub": "user-id",
  "jti": "token-id",
  "type": "access",
  "iss": "enterprise-framework",
  "exp": 1234567890,
  "iat": 1234564290
}
```

**Response (200 OK - Inactive Token):**
```json
{
  "active": false,
  "error": "Token has been revoked"
}
```

### Protected Endpoints

All protected endpoints require an `Authorization` header with a valid Bearer token:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### POST /auth/logout
Logs out the user by revoking tokens and terminating all sessions.

**Response (200 OK):**
```json
{
  "message": "Logged out successfully"
}
```

**Error Responses:**
- `401 Unauthorized`: Missing or invalid token

#### GET /auth/sessions
Lists all active sessions for the authenticated user.

**Response (200 OK):**
```json
{
  "sessions": [
    {
      "sessionId": "uuid",
      "createdAt": "2025-11-06T10:00:00Z",
      "lastAccessedAt": "2025-11-06T10:30:00Z",
      "expiresAt": "2025-11-07T10:00:00Z",
      "ipAddress": "192.168.1.1",
      "userAgent": "Mozilla/5.0..."
    }
  ]
}
```

**Error Responses:**
- `401 Unauthorized`: Missing or invalid token

#### DELETE /auth/sessions/:sessionId
Terminates a specific session.

**Response (200 OK):**
```json
{
  "message": "Session terminated successfully"
}
```

**Error Responses:**
- `400 Bad Request`: Missing session ID
- `401 Unauthorized`: Missing or invalid token
- `500 Internal Server Error`: Session not found or doesn't belong to user

## Configuration

The authentication system is configured via `application.json`:

```json
{
  "auth": {
    "jwt": {
      "secret": "change-this-secret-in-production",
      "issuer": "enterprise-framework",
      "accessTokenExpirationSeconds": 3600,
      "refreshTokenExpirationSeconds": 604800,
      "algorithm": "HS256"
    },
    "password": {
      "minLength": 12,
      "requireUppercase": true,
      "requireLowercase": true,
      "requireDigit": true,
      "requireSpecialChar": true,
      "preventReuse": 5
    },
    "session": {
      "absoluteTimeoutSeconds": 86400,
      "idleTimeoutSeconds": 3600,
      "maxConcurrentSessions": 5,
      "renewOnActivity": true
    },
    "rateLimiting": {
      "enabled": true,
      "maxRequests": 10,
      "windowSeconds": 60,
      "blacklistDurationSeconds": 300
    },
    "bruteForce": {
      "enabled": true,
      "maxFailedAttempts": 5,
      "lockoutDurationSeconds": 900,
      "resetAfterSuccessfulLogin": true
    }
  }
}
```

### JWT Algorithms

The system supports three JWT signing algorithms:

#### HS256 (HMAC with SHA-256)
Symmetric key algorithm using a shared secret.

```json
{
  "algorithm": "HS256",
  "secret": "your-secret-key-here"
}
```

#### RS256 (RSA with SHA-256)
Asymmetric algorithm using RSA public/private keys.

```json
{
  "algorithm": "RS256",
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----",
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
}
```

#### ES256 (ECDSA with SHA-256)
Asymmetric algorithm using elliptic curve public/private keys.

```json
{
  "algorithm": "ES256",
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----",
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
}
```

## Security Features

### Password Policies

Passwords must meet the following requirements:
- Minimum length: 12 characters (configurable)
- At least one uppercase letter
- At least one lowercase letter
- At least one digit
- At least one special character
- Cannot reuse last 5 passwords

### Brute Force Protection

After 5 failed login attempts:
- Account is locked for 15 minutes
- All subsequent login attempts fail with lockout message
- Failed attempts counter resets on successful login

### Rate Limiting

Authentication endpoints are rate limited:
- Maximum 10 requests per 60 seconds per IP
- Offenders are blacklisted for 5 minutes after repeated violations
- Distributed rate limiting via Redis

### Session Management

- Maximum 5 concurrent sessions per user
- Sessions expire after 24 hours (absolute timeout)
- Idle sessions expire after 1 hour (idle timeout)
- Sessions are renewed on activity (configurable)
- Sessions can be listed and terminated individually

### Audit Logging

All authentication events are logged:
- Login success/failure
- Token refresh
- Token revocation
- Account lockout
- Password changes
- Session termination
- Rate limit violations

Logs are structured for SIEM integration.

## Infrastructure Requirements

### Redis
Required for:
- Token revocation blacklist
- Session storage
- Rate limiting counters
- Brute force tracking

**Connection String:**
```
redis://localhost:6379
```

### DynamoDB
Required for:
- User storage
- Credential storage
- Password history

**Table Schema:**
```
PK: USER#<userId>
SK: PROFILE
Attributes: username, email, roles, passwordHash, etc.
GSI: username-index (for login lookups)
```

## Example Usage

### Complete Authentication Flow

```bash
# 1. Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user@example.com",
    "password": "SecurePassword123!"
  }'

# Response:
# {
#   "accessToken": "eyJ...",
#   "refreshToken": "eyJ...",
#   "tokenType": "Bearer",
#   "expiresIn": 3600
# }

# 2. Access Protected Resource
curl -X GET http://localhost:8080/api/v1/ \
  -H "Authorization: Bearer eyJ..."

# 3. Refresh Token (when access token expires)
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJ..."
  }'

# 4. List Active Sessions
curl -X GET http://localhost:8080/auth/sessions \
  -H "Authorization: Bearer eyJ..."

# 5. Logout
curl -X POST http://localhost:8080/auth/logout \
  -H "Authorization: Bearer eyJ..."
```

## Troubleshooting

### Account Locked
Wait 15 minutes or contact administrator to unlock manually.

### Token Expired
Use the refresh token to obtain a new access token via `/auth/refresh`.

### Rate Limited
Wait 5 minutes for the blacklist to expire, or contact administrator.

### Invalid Credentials
Ensure username and password are correct. Check that account is enabled and not locked.

## Future Enhancements (Phase 2 & 3)

- OAuth 2.0/OIDC integration (Google, GitHub, etc.)
- SAML 2.0 support
- Multi-factor authentication (TOTP, SMS, FIDO2)
- LDAP/Active Directory integration
- Service-to-service authentication
- API key management
- Risk-based authentication
- Device fingerprinting
