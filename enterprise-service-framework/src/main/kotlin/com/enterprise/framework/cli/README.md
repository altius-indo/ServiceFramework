# Enterprise Service Framework CLI

A comprehensive command-line interface for managing the Enterprise Service Framework control plane, including authentication, authorization, and policy management.

## Features

- **Bootstrap Authentication & Authorization**: Initial system setup with admin user
- **Authentication Management**: Login, logout, and session management
- **Authorization Management**: Role and permission management
- **Policy Management**: Create, update, delete, and manage policies
- **Configuration Management**: View and update CLI settings

## Installation

The CLI is included in the main application JAR. Build the project:

```bash
cd enterprise-service-framework
./gradlew shadowJar
```

This creates: `build/libs/enterprise-service-framework-1.0.0-SNAPSHOT-all.jar`

## Quick Start

### 1. Bootstrap the System

First, bootstrap the system to create the initial admin user:

```bash
java -jar build/libs/enterprise-service-framework-1.0.0-SNAPSHOT-all.jar bootstrap \
  --username admin \
  --email admin@example.com \
  --password adminuser123!
```

Or use the wrapper script:

```bash
./scripts/esf-cli.sh bootstrap --username admin --email admin@example.com --password
```

### 2. Authenticate

Login with the bootstrap admin user:

```bash
./scripts/esf-cli.sh auth login --username admin --password
```

### 3. Manage Resources

Once authenticated, you can manage roles, permissions, and policies:

```bash
# List roles
./scripts/esf-cli.sh authz role list

# Create a role
./scripts/esf-cli.sh authz role create --name Editor --permissions "document:read,document:write"

# List policies
./scripts/esf-cli.sh policy list

# Create a policy
./scripts/esf-cli.sh policy create \
  --name "Allow Engineering" \
  --effect ALLOW \
  --actions "read,write" \
  --resources "document:*"
```

## Commands

### Bootstrap

Initialize the system with the first admin user:

```bash
esf bootstrap [OPTIONS]

Options:
  -u, --username <username>    Admin username (required)
  -e, --email <email>          Admin email (required)
  -p, --password               Admin password (will prompt if not provided)
  --force                      Force bootstrap even if already completed
  --server-url <url>           Server URL (default: http://localhost:8080)
```

### Authentication

#### Login

```bash
esf auth login [OPTIONS]

Options:
  -u, --username <username>    Username (required)
  -p, --password               Password (will prompt if not provided)
  --server-url <url>           Server URL (default: http://localhost:8080)
```

#### Logout

```bash
esf auth logout
```

#### Status

```bash
esf auth status
```

### Authorization

#### Role Management

**Create Role:**
```bash
esf authz role create [OPTIONS]

Options:
  -n, --name <name>            Role name (required)
  -d, --description <desc>    Role description
  -p, --permissions <perms>    Comma-separated permissions
  -P, --parent <parentId>      Parent role ID
```

**List Roles:**
```bash
esf authz role list
```

**Assign Role:**
```bash
esf authz role assign --role <roleId> --user <userId>
```

**Show Role:**
```bash
esf authz role show <roleId>
```

#### Permission Management

**Check Permission:**
```bash
esf authz check --user <userId> --resource <resourceId> --action <action>
```

### Policy Management

**Create Policy:**
```bash
esf policy create [OPTIONS]

Options:
  -n, --name <name>            Policy name (required)
  -e, --effect <effect>        Effect: ALLOW or DENY (required)
  -a, --actions <actions>      Comma-separated actions (required)
  -r, --resources <resources>  Comma-separated resources (required)
  -p, --priority <priority>   Priority (default: 100)
  -d, --description <desc>     Policy description
```

**List Policies:**
```bash
esf policy list
```

**Show Policy:**
```bash
esf policy show <policyId>
```

**Update Policy:**
```bash
esf policy update <policyId> [OPTIONS]

Options:
  -n, --name <name>            New policy name
  -e, --effect <effect>        New effect: ALLOW or DENY
```

**Delete Policy:**
```bash
esf policy delete <policyId>
```

**Enable Policy:**
```bash
esf policy enable <policyId>
```

**Disable Policy:**
```bash
esf policy disable <policyId>
```

### Configuration

**Show Configuration:**
```bash
esf config show
```

**Set Configuration:**
```bash
esf config set --server-url <url>
```

## Bootstrap Authentication & Authorization

The CLI includes a bootstrap mechanism that:

1. **Creates the first admin user** with full system access
2. **Creates the SystemAdmin role** with all permissions
3. **Assigns the role** to the admin user
4. **Creates initial permissions** for common operations
5. **Creates bootstrap policies** for system access

### Bootstrap Process

The bootstrap process is designed to be run once during initial system setup:

```bash
esf bootstrap \
  --username admin \
  --email admin@example.com \
  --password
```

**Security Notes:**
- Bootstrap can only be run once (unless `--force` is used)
- The admin user is created with full system access
- All bootstrap operations are logged
- After bootstrap, use `esf auth login` for subsequent operations

### Bootstrap Components

1. **Admin User**: Created with SystemAdmin role
2. **SystemAdmin Role**: Full access (`*:*` permissions)
3. **Initial Permissions**: Common permissions for system management
4. **Bootstrap Policies**: Policies for admin access and default deny

## Authentication Flow

1. **Bootstrap** (first time only):
   ```bash
   esf bootstrap --username admin --email admin@example.com --password
   ```

2. **Login**:
   ```bash
   esf auth login --username admin --password
   ```
   - Token is saved to `~/.esf/token.json`
   - Token is automatically refreshed when expired

3. **Use CLI Commands**:
   - All commands check authentication automatically
   - Token is included in API requests

4. **Logout**:
   ```bash
   esf auth logout
   ```
   - Clears local token
   - Invalidates server session

## Configuration

CLI configuration is stored in `~/.esf/`:

- `token.json`: Authentication token (encrypted in production)
- `config.json`: CLI settings (server URL, etc.)

## Examples

### Complete Setup Workflow

```bash
# 1. Start the service
./scripts/start.sh

# 2. Bootstrap the system
./scripts/esf-cli.sh bootstrap \
  --username admin \
  --email admin@example.com \
  --password

# 3. Login
./scripts/esf-cli.sh auth login --username admin --password

# 4. Create a role
./scripts/esf-cli.sh authz role create \
  --name Editor \
  --permissions "document:read,document:write,document:delete"

# 5. Create a policy
./scripts/esf-cli.sh policy create \
  --name "Allow Engineering Department" \
  --effect ALLOW \
  --actions "read,write" \
  --resources "document:*" \
  --priority 100

# 6. Check authorization
./scripts/esf-cli.sh authz check \
  --user user123 \
  --resource doc456 \
  --action read
```

## Troubleshooting

### Authentication Errors

If you get authentication errors:

```bash
# Check authentication status
esf auth status

# Re-login if needed
esf auth login --username admin --password
```

### Bootstrap Already Completed

If bootstrap was already run:

```bash
# Use --force to re-bootstrap (use with caution)
esf bootstrap --force --username admin --email admin@example.com --password
```

### Server Connection Issues

If the CLI can't connect to the server:

```bash
# Check server URL
esf config show

# Update server URL
esf config set --server-url http://your-server:8080
```

## Security Considerations

1. **Token Storage**: Tokens are stored in `~/.esf/token.json`. In production, consider encrypting this file.

2. **Password Input**: Use `--password` flag to prompt for password instead of passing it on command line.

3. **Bootstrap Security**: Bootstrap should only be run in secure environments. The `--force` flag should be used with extreme caution.

4. **Token Expiration**: Tokens automatically refresh, but you may need to re-authenticate after extended periods.

## Integration with Services

The CLI communicates with the service via REST API:

- Authentication: `/auth/login`, `/auth/logout`, `/auth/refresh`
- Authorization: `/authz/v1/*` (to be implemented)
- Policies: `/authz/v1/policies/*` (to be implemented)

All API calls include the authentication token in the `Authorization` header.

