import pytest
import json
from unittest.mock import Mock, patch, MagicMock
import sys
import os
from datetime import datetime

# Add parent directory to path to import modules
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from consumer import IntegrationConsumer
from config import Config

class TestIntegrationConsumer:
    
    def setup_method(self):
        """Setup test fixtures"""
        with patch('consumer.KafkaConsumer'):
            self.consumer = IntegrationConsumer()
        
        self.sample_customer = {
            "id": 1,
            "name": "John Doe",
            "email": "john@example.com",
            "status": "active",
            "created_date": "2024-01-15"
        }
        
        self.sample_product = {
            "id": 101,
            "name": "Laptop",
            "price": 999.99,
            "quantity": 50,
            "category": "Electronics"
        }
    
    def test_process_customer_record_success(self):
        """Test successful customer record processing"""
        # Given
        initial_count = len(self.consumer.customer_records)
        
        # When
        self.consumer.process_customer_record(self.sample_customer)
        
        # Then
        assert len(self.consumer.customer_records) == initial_count + 1
        latest_record = self.consumer.customer_records[-1]
        assert latest_record['id'] == 1
        assert latest_record['name'] == 'John Doe'
        assert latest_record['email'] == 'john@example.com'
        assert 'received_at' in latest_record
    
    def test_process_inventory_record_success(self):
        """Test successful inventory record processing"""
        # Given
        initial_count = len(self.consumer.inventory_records)
        
        # When
        self.consumer.process_inventory_record(self.sample_product)
        
        # Then
        assert len(self.consumer.inventory_records) == initial_count + 1
        latest_record = self.consumer.inventory_records[-1]
        assert latest_record['id'] == 101
        assert latest_record['name'] == 'Laptop'
        assert latest_record['price'] == 999.99
        assert 'received_at' in latest_record
    
    @patch('consumer.requests.post')
    def test_merge_and_send_data_success(self, mock_post):
        """Test successful data merging and sending"""
        # Given
        mock_response = Mock()
        mock_response.status_code = 200
        mock_post.return_value = mock_response
        
        # Add some test data
        self.consumer.process_customer_record(self.sample_customer)
        self.consumer.process_inventory_record(self.sample_product)
        
        # When
        self.consumer.merge_and_send_data()
        
        # Then
        mock_post.assert_called_once()
        call_args = mock_post.call_args
        
        # Check the API was called with correct URL
        assert call_args[1]['json']['summary']['total_customers'] >= 1
        assert call_args[1]['json']['summary']['total_products'] >= 1
        assert 'timestamp' in call_args[1]['json']
    
    @patch('consumer.requests.post')
    def test_merge_and_send_data_api_failure(self, mock_post):
        """Test handling of API failure during data sending"""
        # Given
        mock_response = Mock()
        mock_response.status_code = 500
        mock_response.text = "Internal Server Error"
        mock_post.return_value = mock_response
        
        # Add test data
        self.consumer.process_customer_record(self.sample_customer)
        
        # When
        self.consumer.merge_and_send_data()
        
        # Then
        mock_post.assert_called_once()
        # Records should not be cleared on failure
        assert len(self.consumer.customer_records) >= 1
    
    def test_merge_and_send_data_no_data(self):
        """Test merge and send with no data"""
        # Given
        self.consumer.customer_records = []
        self.consumer.inventory_records = []
        
        # When
        with patch('consumer.requests.post') as mock_post:
            self.consumer.merge_and_send_data()
        
        # Then
        mock_post.assert_not_called()
    
    def test_should_merge_data_timing(self):
        """Test merge timing logic"""
        # Given
        self.consumer.merge_interval_seconds = 1
        
        # When - immediately after initialization
        should_merge_immediate = self.consumer.should_merge_data()
        
        # Then
        assert should_merge_immediate is True  # Should merge on first run
    
    def test_data_cleanup_after_successful_send(self):
        """Test that data is properly cleaned up after successful send"""
        # Given
        mock_response = Mock()
        mock_response.status_code = 200
        
        # Add more than 100 records to test cleanup
        for i in range(105):
            customer = self.sample_customer.copy()
            customer['id'] = i
            self.consumer.process_customer_record(customer)
        
        # When
        with patch('consumer.requests.post', return_value=mock_response):
            self.consumer.merge_and_send_data()
        
        # Then
        assert len(self.consumer.customer_records) <= 100  # Should keep only last 100
    
    def test_thread_safety_with_concurrent_processing(self):
        """Test thread safety of record processing"""
        import threading
        
        # Given
        def add_customers():
            for i in range(10):
                customer = self.sample_customer.copy()
                customer['id'] = f"customer_{i}"
                self.consumer.process_customer_record(customer)
        
        def add_products():
            for i in range(10):
                product = self.sample_product.copy()
                product['id'] = f"product_{i}"
                self.consumer.process_inventory_record(product)
        
        # When
        customer_thread = threading.Thread(target=add_customers)
        product_thread = threading.Thread(target=add_products)
        
        customer_thread.start()
        product_thread.start()
        
        customer_thread.join()
        product_thread.join()
        
        # Then
        assert len(self.consumer.customer_records) == 10
        assert len(self.consumer.inventory_records) == 10






