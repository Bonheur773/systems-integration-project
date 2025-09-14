import pytest
import json
import requests
from unittest.mock import Mock, patch
import sys
import os

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from consumer import IntegrationConsumer
from config import Config

class TestAnalyticsIntegration:
    
    def setup_method(self):
        """Setup test fixtures"""
        with patch('consumer.KafkaConsumer'):
            self.consumer = IntegrationConsumer()
        
        self.sample_analytics_data = {
            "timestamp": "2024-01-15T10:30:00Z",
            "summary": {
                "total_customers": 2,
                "total_products": 1,
                "active_customers": 2
            },
            "customers": [
                {
                    "id": 1,
                    "name": "John Doe",
                    "email": "john@example.com",
                    "status": "active"
                }
            ],
            "inventory": [
                {
                    "id": 101,
                    "name": "Laptop",
                    "price": 999.99
                }
            ]
        }
    
    @patch('consumer.requests.post')
    def test_send_to_analytics_success(self, mock_post):
        """Test successful data submission to analytics"""
        # Given
        mock_response = Mock()
        mock_response.status_code = 200
        mock_post.return_value = mock_response
        
        # Add test data
        self.consumer.customer_records = [{"id": 1, "name": "John Doe"}]
        self.consumer.inventory_records = [{"id": 101, "name": "Laptop"}]
        
        # When
        self.consumer.merge_and_send_data()
        
        # Then
        mock_post.assert_called_once()
        call_args = mock_post.call_args
        assert call_args[0][0] == f"{self.consumer.config.ANALYTICS_API_URL}/analytics/data"
        assert 'json' in call_args[1]
    
    @patch('consumer.requests.post')
    def test_send_to_analytics_timeout_handling(self, mock_post):
        """Test timeout handling"""
        # Given
        mock_post.side_effect = requests.exceptions.Timeout()
        self.consumer.customer_records = [{"id": 1, "name": "John"}]
        
        # When
        self.consumer.merge_and_send_data()
        
        # Then
        mock_post.assert_called_once()
        # Data should still be in records after timeout
        assert len(self.consumer.customer_records) >= 1
    
    @patch('consumer.requests.post')
    def test_send_to_analytics_connection_error(self, mock_post):
        """Test connection error handling"""
        # Given
        mock_post.side_effect = requests.exceptions.ConnectionError()
        self.consumer.customer_records = [{"id": 1, "name": "John"}]
        
        # When
        self.consumer.merge_and_send_data()
        
        # Then
        mock_post.assert_called_once()
        # Data should still be in records after connection error
        assert len(self.consumer.customer_records) >= 1