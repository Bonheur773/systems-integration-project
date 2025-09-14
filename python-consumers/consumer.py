import json
import logging
import requests
import time
from kafka import KafkaConsumer
from config import Config
from datetime import datetime, timedelta
import threading

# Set up logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class IntegrationConsumer:
    def __init__(self):
        self.config = Config()
        self.customer_records = []
        self.inventory_records = []
        self.last_merge_time = datetime.now()
        self.merge_interval_seconds = 60  # Merge data every minute
        self.lock = threading.Lock()
        
        # Initialize Kafka consumer
        self.consumer = KafkaConsumer(
            self.config.CUSTOMER_TOPIC,
            self.config.INVENTORY_TOPIC,
            bootstrap_servers=[self.config.KAFKA_BOOTSTRAP_SERVERS],
            group_id=self.config.KAFKA_GROUP_ID,
            value_deserializer=lambda x: json.loads(x.decode('utf-8')),
            auto_offset_reset='latest',  # Start from latest messages
            enable_auto_commit=True
        )
        
        logger.info(f"Initialized consumer for topics: {self.config.CUSTOMER_TOPIC}, {self.config.INVENTORY_TOPIC}")
    
    def process_customer_record(self, record):
        """Process individual customer record"""
        with self.lock:
            self.customer_records.append({
                'id': record.get('id'),
                'name': record.get('name'),
                'email': record.get('email'),
                'status': record.get('status'),
                'created_date': record.get('created_date'),
                'received_at': datetime.now().isoformat()
            })
            logger.info(f"Added customer: {record.get('name')} (Total: {len(self.customer_records)})")
    
    def process_inventory_record(self, record):
        """Process individual inventory record"""
        with self.lock:
            self.inventory_records.append({
                'id': record.get('id'),
                'name': record.get('name'),
                'price': record.get('price'),
                'quantity': record.get('quantity'),
                'category': record.get('category'),
                'received_at': datetime.now().isoformat()
            })
            logger.info(f"Added product: {record.get('name')} (Total: {len(self.inventory_records)})")
    
    def merge_and_send_data(self):
        """Merge customer and inventory data and send to analytics"""
        with self.lock:
            if not self.customer_records and not self.inventory_records:
                logger.info("No data to merge - waiting for more records")
                return
            
            # Create merged dataset
            merged_data = {
                'timestamp': datetime.now().isoformat(),
                'summary': {
                    'total_customers': len(self.customer_records),
                    'total_products': len(self.inventory_records),
                    'active_customers': len([c for c in self.customer_records if c.get('status') == 'active'])
                },
                'customers': self.customer_records.copy(),
                'inventory': self.inventory_records.copy()
            }
            
            # Send to analytics API
            try:
                response = requests.post(
                    f"{self.config.ANALYTICS_API_URL}/analytics/data",
                    json=merged_data,
                    headers={'Content-Type': 'application/json'},
                    timeout=30
                )
                
                if response.status_code == 200:
                    logger.info(f"✅ Successfully sent merged data to analytics: {len(self.customer_records)} customers, {len(self.inventory_records)} products")
                    
                    # Clear processed records (keep last 100 for reference)
                    self.customer_records = self.customer_records[-100:] if len(self.customer_records) > 100 else []
                    self.inventory_records = self.inventory_records[-100:] if len(self.inventory_records) > 100 else []
                    
                else:
                    logger.error(f"Failed to send to analytics API: {response.status_code} - {response.text}")
                    
            except requests.exceptions.RequestException as e:
                logger.error(f"Error sending to analytics API: {str(e)}")
    
    def should_merge_data(self):
        """Check if it's time to merge and send data"""
        return datetime.now() - self.last_merge_time >= timedelta(seconds=self.merge_interval_seconds)
    
    def consume_messages(self):
        """Main consumer loop"""
        logger.info("Starting consumer loop...")
        
        try:
            for message in self.consumer:
                topic = message.topic
                value = message.value
                
                logger.debug(f"Processing message from topic: {topic}")
                
                if topic == self.config.CUSTOMER_TOPIC:
                    self.process_customer_record(value)
                    
                elif topic == self.config.INVENTORY_TOPIC:
                    self.process_inventory_record(value)
                
                # Check if we should merge and send data
                if self.should_merge_data():
                    self.merge_and_send_data()
                    self.last_merge_time = datetime.now()
                    
        except KeyboardInterrupt:
            logger.info("Shutting down consumer...")
        except Exception as e:
            logger.error(f"Error in consumer loop: {str(e)}")
        finally:
            self.consumer.close()
    
    def run(self):
        """Start the consumer"""
        logger.info("Starting Integration Consumer...")
        
        # Send any remaining data before starting
        self.merge_and_send_data()
        
        # Start consuming messages
        self.consume_messages()

if __name__ == "__main__":
    consumer = IntegrationConsumer()
    consumer.run()