# Service Management Scripts

This directory contains scripts for managing the Enterprise Service Framework.

## Scripts

### `start.sh`
Starts the Enterprise Service Framework and all its dependencies.

**Features:**
- Checks prerequisites (Java, Docker, Docker Compose)
- Starts Docker services (Redis, DynamoDB)
- Builds the application
- Starts the application in the background
- Waits for services to be healthy
- Shows service status

**Usage:**
```bash
./scripts/start.sh
```

**What it does:**
1. Validates Java 17+ is installed
2. Checks Docker and Docker Compose are available
3. Starts Redis and DynamoDB via Docker Compose
4. Builds the application using Gradle
5. Starts the application in the background
6. Monitors health until ready
7. Displays service status

**Output:**
- Application runs on `http://localhost:8080`
- Health endpoint: `http://localhost:8080/health`
- Logs: `logs/service.log`
- PID file: `.service.pid`

### `stop.sh`
Stops the Enterprise Service Framework gracefully.

**Usage:**
```bash
# Stop application only (Docker services continue running)
./scripts/stop.sh

# Stop application and Docker services
./scripts/stop.sh --stop-deps

# Force stop without confirmation
./scripts/stop.sh --force

# Show help
./scripts/stop.sh --help
```

**Options:**
- `--stop-deps`: Also stops Docker services (Redis, DynamoDB)
- `--force`: Skip confirmation prompt
- `-h, --help`: Show help message

**What it does:**
1. Stops the application gracefully (SIGTERM)
2. Waits up to 30 seconds for graceful shutdown
3. Force kills if necessary (SIGKILL)
4. Optionally stops Docker services
5. Cleans up PID files

### `status.sh`
Shows the current status of all services.

**Usage:**
```bash
./scripts/status.sh
```

**What it shows:**
- Docker services status (Redis, DynamoDB)
- Application process status
- Health endpoint response
- Port usage (8080, 6379, 8000)
- Recent application logs

## Quick Start

1. **Start the service:**
   ```bash
   cd enterprise-service-framework
   ./scripts/start.sh
   ```

2. **Check status:**
   ```bash
   ./scripts/status.sh
   ```

3. **Stop the service:**
   ```bash
   ./scripts/stop.sh
   ```

## Prerequisites

- **Java 17+**: Required for running the application
- **Docker**: Required for Redis and DynamoDB
- **Docker Compose**: Required for managing Docker services
- **curl**: Optional, for health checks
- **jq**: Optional, for pretty JSON output in status script

## Troubleshooting

### Port Already in Use
If port 8080 is already in use:
```bash
# Find process using port 8080
lsof -i:8080

# Kill the process (replace PID)
kill <PID>
```

### Docker Services Not Starting
```bash
# Check Docker services status
docker-compose -f docker/docker-compose.yml ps

# View logs
docker-compose -f docker/docker-compose.yml logs

# Restart services
docker-compose -f docker/docker-compose.yml restart
```

### Application Not Starting
```bash
# Check logs
tail -f logs/service.log

# Check if process is running
./scripts/status.sh

# Check Java version
java -version
```

### Clean Restart
```bash
# Stop everything
./scripts/stop.sh --stop-deps

# Clean Docker volumes (optional - removes data)
docker-compose -f docker/docker-compose.yml down -v

# Start fresh
./scripts/start.sh
```

## Environment Variables

The scripts use the following defaults:
- Application port: `8080`
- Redis port: `6379`
- DynamoDB port: `8000`
- Log file: `logs/service.log`
- PID file: `.service.pid`

## Notes

- The application runs in the background after starting
- Logs are written to `logs/service.log`
- The PID is saved in `.service.pid` for graceful shutdown
- Docker services are managed separately and can run independently
- Use `status.sh` to check if everything is running correctly

