from flask import Flask, jsonify, request
from flask_cors import CORS
import random
import time
from datetime import datetime
import logging

app = Flask(__name__)
CORS(app)

# Set up logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Sample data
customers_data = [
    {"id": 1, "name": "John Doe", "email": "john@example.com", "created_date": "2024-01-15", "status": "active"},
    {"id": 2, "name": "Jane Smith", "email": "jane@example.com", "created_date": "2024-02-10", "status": "active"},
    {"id": 3, "name": "Bob Johnson", "email": "bob@example.com", "created_date": "2024-03-05", "status": "inactive"},
    {"id": 4, "name": "Alice Brown", "email": "alice@example.com", "created_date": "2024-04-12", "status": "active"},
    {"id": 5, "name": "Charlie Wilson", "email": "charlie@example.com", "created_date": "2024-05-08", "status": "active"}
]

products_data = [
    {"id": 101, "name": "Laptop", "stock": 50, "price": 999.99, "category": "Electronics"},
    {"id": 102, "name": "Phone", "stock": 75, "price": 699.99, "category": "Electronics"},
    {"id": 103, "name": "Book", "stock": 200, "price": 29.99, "category": "Education"},
    {"id": 104, "name": "Chair", "stock": 25, "price": 149.99, "category": "Furniture"},
    {"id": 105, "name": "Desk", "stock": 15, "price": 299.99, "category": "Furniture"}
]

# Add more sample data to reach 1000+ records for scalability testing
for i in range(6, 1001):
    customers_data.append({
        "id": i,
        "name": f"Customer {i}",
        "email": f"customer{i}@example.com",
        "created_date": f"2024-{random.randint(1,12):02d}-{random.randint(1,28):02d}",
        "status": random.choice(["active", "inactive"])
    })

for i in range(106, 1001):
    products_data.append({
        "id": i,
        "name": f"Product {i}",
        "stock": random.randint(1, 100),
        "price": round(random.uniform(10.0, 999.99), 2),
        "category": random.choice(["Electronics", "Education", "Furniture", "Clothing", "Sports"])
    })

@app.route('/')
def home():
    return jsonify({"message": "Mock APIs are running", "timestamp": datetime.now().isoformat()})

# CRM API endpoints
@app.route('/customers', methods=['GET'])
def get_customers():
    # Simulate some processing delay
    time.sleep(0.1)
    
    # Pagination support
    page = int(request.args.get('page', 1))
    limit = int(request.args.get('limit', 100))
    
    start = (page - 1) * limit
    end = start + limit
    
    response_data = customers_data[start:end]
    
    # Simulate occasional failures (5% chance)
    if random.random() < 0.05:
        return jsonify({"error": "CRM system temporarily unavailable"}), 500
    
    return jsonify({
        "data": response_data,
        "page": page,
        "limit": limit,
        "total": len(customers_data),
        "total_pages": (len(customers_data) + limit - 1) // limit
    })

@app.route('/customers/<int:customer_id>', methods=['GET'])
def get_customer(customer_id):
    customer = next((c for c in customers_data if c['id'] == customer_id), None)
    if customer:
        return jsonify(customer)
    return jsonify({"error": "Customer not found"}), 404

@app.route('/customers', methods=['POST'])
def create_customer():
    data = request.json
    new_id = max([c['id'] for c in customers_data]) + 1
    new_customer = {
        "id": new_id,
        "name": data.get('name'),
        "email": data.get('email'),
        "created_date": datetime.now().strftime('%Y-%m-%d'),
        "status": "active"
    }
    customers_data.append(new_customer)
    return jsonify(new_customer), 201

# Inventory API endpoints
@app.route('/products', methods=['GET'])
def get_products():
    # Simulate some processing delay
    time.sleep(0.1)
    
    # Pagination support
    page = int(request.args.get('page', 1))
    limit = int(request.args.get('limit', 100))
    
    start = (page - 1) * limit
    end = start + limit
    
    response_data = products_data[start:end]
    
    # Simulate occasional failures (0.5% chance - reduced from 3%)
    if random.random() < 0.005:
        return jsonify({"error": "Inventory system temporarily unavailable"}), 500
    
    return jsonify({
        "data": response_data,
        "page": page,
        "limit": limit,
        "total": len(products_data),
        "total_pages": (len(products_data) + limit - 1) // limit
    })

@app.route('/products/<int:product_id>', methods=['GET'])
def get_product(product_id):
    product = next((p for p in products_data if p['id'] == product_id), None)
    if product:
        return jsonify(product)
    return jsonify({"error": "Product not found"}), 404

# Enhanced Analytics API endpoint (receives the merged data)
@app.route('/analytics/data', methods=['POST'])
def receive_analytics_data():
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({"error": "No data provided"}), 400
        
        # Handle different data formats
        customers_count = 0
        inventory_count = 0
        
        # Check if it's merged data format (from Python consumer)
        if isinstance(data, dict):
            customers_count = len(data.get('customers', []))
            inventory_count = len(data.get('inventory', []))
            timestamp = data.get('timestamp', datetime.now().isoformat())
            
            logger.info(f"Analytics received merged data: {customers_count} customers, {inventory_count} products at {timestamp}")
            
            # Log summary information
            summary = data.get('summary', {})
            if summary:
                logger.info(f"Data summary: Total customers: {summary.get('total_customers', 0)}, "
                           f"Active customers: {summary.get('active_customers', 0)}, "
                           f"Total products: {summary.get('total_products', 0)}")
        
        # Handle array format
        elif isinstance(data, list):
            customers_count = len(data)
            logger.info(f"Analytics received array data: {customers_count} records")
        
        else:
            # Single record
            customers_count = 1
            logger.info(f"Analytics received single record")
        
        # Simulate processing time
        time.sleep(0.05)
        
        return jsonify({
            "status": "success",
            "message": "Analytics data processed successfully",
            "customers_processed": customers_count,
            "inventory_processed": inventory_count,
            "total_records": customers_count + inventory_count,
            "processed_at": datetime.now().isoformat()
        }), 200
        
    except Exception as e:
        logger.error(f"Error processing analytics data: {str(e)}")
        return jsonify({"error": f"Failed to process data: {str(e)}"}), 500

# Health check endpoint
@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        "status": "healthy",
        "services": {
            "customers": "available",
            "products": "available", 
            "analytics": "available"
        },
        "timestamp": datetime.now().isoformat()
    })

# Analytics status endpoint
@app.route('/analytics/status', methods=['GET'])
def analytics_status():
    return jsonify({
        "status": "ready",
        "endpoint": "/analytics/data",
        "methods": ["POST"],
        "message": "Analytics service is ready to receive data"
    })

if __name__ == '__main__':
    print("Starting Mock APIs server...")
    print("Available endpoints:")
    print("- GET /customers (CRM)")
    print("- GET /products (Inventory)")  
    print("- POST /analytics/data (Analytics)")
    print("- GET /health (Health Check)")
    print("- GET /analytics/status (Analytics Status)")
    app.run(host='0.0.0.0', port=8080, debug=True)