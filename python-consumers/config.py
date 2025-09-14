# Configuration for Python Consumers
import os

class Config:
    # Kafka Configuration
    KAFKA_BOOTSTRAP_SERVERS = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')
    KAFKA_GROUP_ID = os.getenv('KAFKA_GROUP_ID', 'integration-consumers')
    CUSTOMER_TOPIC = os.getenv('CUSTOMER_TOPIC', 'customer_data')
    INVENTORY_TOPIC = os.getenv('INVENTORY_TOPIC', 'inventory_data')
    
    # API Configuration - Fixed URL (removed /analytics/data since consumer code adds it)
    ANALYTICS_API_URL = os.getenv('ANALYTICS_API_URL', 'http://localhost:8080')
    
    # Processing Configuration
    BATCH_SIZE = int(os.getenv('BATCH_SIZE', '10'))
    MAX_RETRIES = int(os.getenv('MAX_RETRIES', '3'))
    RETRY_DELAY = int(os.getenv('RETRY_DELAY', '5'))  # seconds
    
    # Idempotency Configuration
    PROCESSED_RECORDS_CACHE_SIZE = int(os.getenv('CACHE_SIZE', '10000'))
    
    # Logging
    LOG_LEVEL = os.getenv('LOG_LEVEL', 'INFO')