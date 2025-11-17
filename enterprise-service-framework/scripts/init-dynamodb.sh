#!/bin/bash

###############################################################################
# Enterprise Service Framework - DynamoDB Initialization Script
# Uses curl to interact with DynamoDB Local REST API
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

ENDPOINT="http://localhost:8000"
TABLE_NAME="enterprise-data"

echo "========================================="
echo "DynamoDB Table Initialization"
echo "========================================="
echo "Endpoint: $ENDPOINT"
echo "Table: $TABLE_NAME"
echo ""

# Check if DynamoDB is running
echo "Checking if DynamoDB is running..."
if ! curl -s -m 2 "$ENDPOINT" > /dev/null 2>&1; then
    echo "✗ Error: DynamoDB is not accessible at $ENDPOINT"
    echo "  Please start DynamoDB using: docker-compose -f docker/docker-compose.yml up -d"
    exit 1
fi
echo "✓ DynamoDB is running"
echo ""

echo "Creating table: $TABLE_NAME..."

# DynamoDB CreateTable request payload
CREATE_TABLE_JSON='{
    "TableName": "enterprise-data",
    "KeySchema": [
        {"AttributeName": "PK", "KeyType": "HASH"},
        {"AttributeName": "SK", "KeyType": "RANGE"}
    ],
    "AttributeDefinitions": [
        {"AttributeName": "PK", "AttributeType": "S"},
        {"AttributeName": "SK", "AttributeType": "S"},
        {"AttributeName": "GSI1PK", "AttributeType": "S"},
        {"AttributeName": "GSI1SK", "AttributeType": "S"}
    ],
    "GlobalSecondaryIndexes": [{
        "IndexName": "GSI1",
        "KeySchema": [
            {"AttributeName": "GSI1PK", "KeyType": "HASH"},
            {"AttributeName": "GSI1SK", "KeyType": "RANGE"}
        ],
        "Projection": {"ProjectionType": "ALL"},
        "ProvisionedThroughput": {
            "ReadCapacityUnits": 5,
            "WriteCapacityUnits": 5
        }
    }],
    "BillingMode": "PAY_PER_REQUEST"
}'

# Create table using DynamoDB HTTP API
# DynamoDB Local accepts any AWS credentials, so we use dummy values
RESPONSE=$(curl -s -X POST "$ENDPOINT" \
    -H "Content-Type: application/x-amz-json-1.0" \
    -H "X-Amz-Target: DynamoDB_20120810.CreateTable" \
    -H "Authorization: AWS4-HMAC-SHA256 Credential=dummy/20230101/us-east-1/dynamodb/aws4_request, SignedHeaders=host;x-amz-date;x-amz-target, Signature=dummy" \
    -H "X-Amz-Date: 20230101T000000Z" \
    -d "$CREATE_TABLE_JSON" 2>&1)

# Check if table was created or already exists
if echo "$RESPONSE" | grep -q "TableDescription"; then
    echo "✓ Table '$TABLE_NAME' created successfully"
elif echo "$RESPONSE" | grep -q "ResourceInUseException"; then
    echo "✓ Table '$TABLE_NAME' already exists"
else
    echo "✗ Error creating table"
    echo "Response: $RESPONSE"
    exit 1
fi

echo ""
echo "You can now run the bootstrap command:"
echo "  ./scripts/esf-cli.sh bootstrap -u admin -e admin@example.com -p"
echo ""

exit 0
