import pytest
from unittest.mock import Mock, patch, MagicMock
from kafka import KafkaConsumer
from kafka.errors import KafkaError
import json
import sys
import os

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from consumer import IntegrationConsumer

class TestKafkaIntegration:
    
    @patch('consumer.KafkaConsumer')
    def test_kafka_consumer_initialization(self, mock_consumer):
        """Test Kafka consumer initialization"""
        # Given
        mock_consumer_instance = Mock()
        mock_consumer.return_value = mock_consumer_instance
        
        # When
        consumer = IntegrationConsumer()
        
        # Then
        mock_consumer.assert_called_once()
        call_args = mock_consumer.call_args
        assert consumer.config.CUSTOMER_TOPIC in call_args[0]
        assert consumer.config.INVENTORY_TOPIC in call_args[0]
    
    @patch('consumer.KafkaConsumer')
    def test_message_processing_workflow(self, mock_consumer):
        """Test complete message processing workflow"""
        # Given
        mock_consumer_instance = Mock()
        mock_consumer.return_value = mock_consumer_instance
        
        customer_message = Mock()
        customer_message.topic = 'customer_data'
        customer_message.value = {"id": 1, "name": "John Doe", "email": "john@example.com"}
        
        product_message = Mock()
        product_message.topic = 'inventory_data'  
        product_message.value = {"id": 101, "name": "Laptop", "price": 999.99}
        
        # When
        consumer = IntegrationConsumer()
        consumer.process_customer_record(customer_message.value)
        consumer.process_inventory_record(product_message.value)
        
        # Then
        assert len(consumer.customer_records) == 1
        assert len(consumer.inventory_records) == 1
        assert consumer.customer_records[0]['name'] == 'John Doe'
        assert consumer.inventory_records[0]['name'] == 'Laptop'
    
    @patch('consumer.KafkaConsumer')
    def test_kafka_error_handling(self, mock_consumer):
        """Test Kafka error handling"""
        # Given
        mock_consumer.side_effect = KafkaError("Connection failed")
        
        # When & Then
        with pytest.raises(KafkaError):
            IntegrationConsumer()
