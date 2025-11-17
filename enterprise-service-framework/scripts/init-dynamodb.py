#!/usr/bin/env python3
"""
Enterprise Service Framework - DynamoDB Table Initialization Script
Creates the required DynamoDB table for the application.
"""

import boto3
import sys
from botocore.exceptions import ClientError

# Configuration
ENDPOINT_URL = "http://localhost:8000"
REGION = "us-east-1"
TABLE_NAME = "enterprise-data"

def print_header():
    print("=" * 50)
    print("DynamoDB Table Initialization")
    print("=" * 50)
    print(f"Endpoint: {ENDPOINT_URL}")
    print(f"Region: {REGION}")
    print(f"Table: {TABLE_NAME}")
    print()

def check_dynamodb_running(dynamodb):
    """Check if DynamoDB is accessible"""
    try:
        print("Checking if DynamoDB is running...")
        dynamodb.meta.client.list_tables()
        print("✓ DynamoDB is running")
        print()
        return True
    except Exception as e:
        print(f"✗ Error: DynamoDB is not accessible at {ENDPOINT_URL}")
        print(f"  {str(e)}")
        print("  Please start DynamoDB using: docker-compose -f docker/docker-compose.yml up -d")
        return False

def table_exists(dynamodb, table_name):
    """Check if table already exists"""
    try:
        table = dynamodb.Table(table_name)
        table.load()
        return True
    except ClientError as e:
        if e.response['Error']['Code'] == 'ResourceNotFoundException':
            return False
        raise

def create_table(dynamodb):
    """Create the DynamoDB table"""
    try:
        print(f"Creating table: {TABLE_NAME}...")

        table = dynamodb.create_table(
            TableName=TABLE_NAME,
            KeySchema=[
                {'AttributeName': 'PK', 'KeyType': 'HASH'},
                {'AttributeName': 'SK', 'KeyType': 'RANGE'}
            ],
            AttributeDefinitions=[
                {'AttributeName': 'PK', 'AttributeType': 'S'},
                {'AttributeName': 'SK', 'AttributeType': 'S'},
                {'AttributeName': 'GSI1PK', 'AttributeType': 'S'},
                {'AttributeName': 'GSI1SK', 'AttributeType': 'S'}
            ],
            GlobalSecondaryIndexes=[
                {
                    'IndexName': 'GSI1',
                    'KeySchema': [
                        {'AttributeName': 'GSI1PK', 'KeyType': 'HASH'},
                        {'AttributeName': 'GSI1SK', 'KeyType': 'RANGE'}
                    ],
                    'Projection': {'ProjectionType': 'ALL'},
                    'ProvisionedThroughput': {
                        'ReadCapacityUnits': 5,
                        'WriteCapacityUnits': 5
                    }
                }
            ],
            BillingMode='PAY_PER_REQUEST'
        )

        print(f"✓ Table '{TABLE_NAME}' created successfully")
        return True
    except ClientError as e:
        if e.response['Error']['Code'] == 'ResourceInUseException':
            print(f"✓ Table '{TABLE_NAME}' already exists")
            return True
        else:
            print(f"✗ Error creating table: {str(e)}")
            return False

def main():
    print_header()

    # Create DynamoDB resource with dummy credentials for local development
    dynamodb = boto3.resource(
        'dynamodb',
        endpoint_url=ENDPOINT_URL,
        region_name=REGION,
        aws_access_key_id='dummy',
        aws_secret_access_key='dummy'
    )

    # Check if DynamoDB is running
    if not check_dynamodb_running(dynamodb):
        sys.exit(1)

    # Check if table exists
    if table_exists(dynamodb, TABLE_NAME):
        print(f"✓ Table '{TABLE_NAME}' already exists")
    else:
        # Create table
        if not create_table(dynamodb):
            sys.exit(1)

    print()
    print("You can now run the bootstrap command:")
    print("  ./scripts/esf-cli.sh bootstrap -u admin -e admin@example.com -p")
    print()

    sys.exit(0)

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\nOperation cancelled by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n✗ Unexpected error: {str(e)}")
        sys.exit(1)
