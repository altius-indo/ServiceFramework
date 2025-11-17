#!/bin/bash

###############################################################################
# Enterprise Service Framework - DynamoDB Initialization Script
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

ENDPOINT="http://localhost:8000"
REGION="us-east-1"
TABLE_NAME="enterprise-data"

echo "========================================="
echo "DynamoDB Table Initialization"
echo "========================================="
echo "Endpoint: $ENDPOINT"
echo "Region: $REGION"
echo "Table: $TABLE_NAME"
echo ""

# Check if DynamoDB is running
echo "Checking if DynamoDB is running..."
if ! curl -s "$ENDPOINT" > /dev/null 2>&1; then
    echo "✗ Error: DynamoDB is not accessible at $ENDPOINT"
    echo "  Please start DynamoDB using: docker-compose -f docker/docker-compose.yml up -d"
    exit 1
fi
echo "✓ DynamoDB is running"
echo ""

# Use environment variables for AWS CLI with local DynamoDB
export AWS_ACCESS_KEY_ID="dummy"
export AWS_SECRET_ACCESS_KEY="dummy"
export AWS_DEFAULT_REGION="$REGION"

echo "Creating table: $TABLE_NAME..."

# Create the table
aws dynamodb create-table \
    --endpoint-url "$ENDPOINT" \
    --region "$REGION" \
    --table-name "$TABLE_NAME" \
    --attribute-definitions \
        AttributeName=PK,AttributeType=S \
        AttributeName=SK,AttributeType=S \
        AttributeName=GSI1PK,AttributeType=S \
        AttributeName=GSI1SK,AttributeType=S \
    --key-schema \
        AttributeName=PK,KeyType=HASH \
        AttributeName=SK,KeyType=RANGE \
    --global-secondary-indexes \
        "[{\"IndexName\":\"GSI1\",\"KeySchema\":[{\"AttributeName\":\"GSI1PK\",\"KeyType\":\"HASH\"},{\"AttributeName\":\"GSI1SK\",\"KeyType\":\"RANGE\"}],\"Projection\":{\"ProjectionType\":\"ALL\"},\"ProvisionedThroughput\":{\"ReadCapacityUnits\":5,\"WriteCapacityUnits\":5}}]" \
    --billing-mode PAY_PER_REQUEST \
    --output json > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo "✓ Table '$TABLE_NAME' created successfully"
    echo ""
    echo "You can now run the bootstrap command:"
    echo "  ./scripts/esf-cli.sh bootstrap -u admin -e admin@example.com -p yourpassword"
    exit 0
else
    # Check if table already exists
    aws dynamodb describe-table \
        --endpoint-url "$ENDPOINT" \
        --region "$REGION" \
        --table-name "$TABLE_NAME" \
        --output json > /dev/null 2>&1

    if [ $? -eq 0 ]; then
        echo "✓ Table '$TABLE_NAME' already exists"
        echo ""
        echo "You can now run the bootstrap command:"
        echo "  ./scripts/esf-cli.sh bootstrap -u admin -e admin@example.com -p yourpassword"
        exit 0
    else
        echo "✗ Error creating table"
        echo "  Please check that DynamoDB is running and accessible"
        exit 1
    fi
fi
