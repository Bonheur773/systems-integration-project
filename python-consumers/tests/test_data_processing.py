import pytest
import json
import uuid
from datetime import datetime
from unittest.mock import Mock, patch
import sys
import os

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from consumer import IntegrationConsumer

class TestDataProcessing:
    
    def setup_method(self):
        """Setup test fixtures"""
        with patch('consumer.KafkaConsumer'):
            self.consumer = IntegrationConsumer()
        
        # Load sample data from fixtures
        fixtures_path = os.path.join(os.path.dirname(__file__), 'fixtures')
        
        with open(os.path.join(fixtures_path, 'sample_customer_data.json')) as f:
            self.sample_customers = json.load(f)
            
        with open(os.path.join(fixtures_path, 'sample_product_data.json')) as f:
            self.sample_products = json.load(f)
    
    def test_batch_processing_performance(self):
        """Test batch processing performance"""
        # Given
        batch_size = 100
        
        # When
        start_time = datetime.now()
        
        for i in range(batch_size):
            customer = self.sample_customers[i % len(self.sample_customers)]
            product = self.sample_products[i % len(self.sample_products)]
            
            self.consumer.process_customer_record(customer)
            self.consumer.process_inventory_record(product)
        
        end_time = datetime.now()
        processing_time = (end_time - start_time).total_seconds()
        
        # Then
        assert len(self.consumer.customer_records) == batch_size
        assert len(self.consumer.inventory_records) == batch_size
        assert processing_time < 5.0  # Should process 100 records in under 5 seconds
        print(f"Processed {batch_size} records in {processing_time:.2f} seconds")
    
    def test_data_transformation_accuracy(self):
        """Test accuracy of data transformation"""
        # Given
        customer = self.sample_customers[0]
        product = self.sample_products[0]
        
        # When
        self.consumer.process_customer_record(customer)
        self.consumer.process_inventory_record(product)
        
        # Then
        processed_customer = self.consumer.customer_records[-1]
        processed_product = self.consumer.inventory_records[-1]
        
        assert processed_customer['id'] == customer['id']
        assert processed_customer['name'] == customer['name']
        assert processed_product['id'] == product['id']
        assert processed_product['name'] == product['name']
        
        # Check added fields
        assert 'received_at' in processed_customer
        assert 'received_at' in processed_product
    
    @patch('consumer.requests.post')
    def test_merged_data_structure(self, mock_post):
        """Test structure of merged data"""
        # Given
        mock_response = Mock()
        mock_response.status_code = 200
        mock_post.return_value = mock_response
        
        customer = self.sample_customers[0]
        product = self.sample_products[0]
        
        self.consumer.process_customer_record(customer)
        self.consumer.process_inventory_record(product)
        
        # When
        self.consumer.merge_and_send_data()
        
        # Then
        mock_post.assert_called_once()
        sent_data = mock_post.call_args[1]['json']
        
        assert 'timestamp' in sent_data
        assert 'summary' in sent_data
        assert 'customers' in sent_data
        assert 'inventory' in sent_data
        assert sent_data['summary']['total_customers'] >= 1
        assert sent_data['summary']['total_products'] >= 1
    
    def test_memory_usage_efficiency(self):
        """Test memory usage during processing"""
        import psutil
        import os
        
        # Given
        process = psutil.Process(os.getpid())
        initial_memory = process.memory_info().rss / 1024 / 1024  # MB
        
        # When - Process large batch
        for i in range(1000):
            customer = self.sample_customers[i % len(self.sample_customers)]
            self.consumer.process_customer_record(customer)
        
        # Then
        final_memory = process.memory_info().rss / 1024 / 1024  # MB
        memory_increase = final_memory - initial_memory
        
        assert memory_increase < 100  # Should not increase memory by more than 100MB
        print(f"Memory increase: {memory_increase:.2f} MB")