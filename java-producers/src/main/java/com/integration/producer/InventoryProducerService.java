package com.integration.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Service
public class InventoryProducerService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryProducerService.class);
    
    private final WebClient webClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${api.inventory.base-url}")
    private String inventoryBaseUrl;
    
    @Value("${kafka.topics.inventory-data}")
    private String inventoryTopic;
    
    public InventoryProducerService(WebClient.Builder webClientBuilder, 
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRateString = "${scheduler.inventory.interval}")
    public void fetchAndPublishInventory() {
        logger.info("Starting inventory data fetch and publish process");
        
        try {
            List<Product> products = fetchProductsFromInventory();
            publishProductsToKafka(products);
            logger.info("Successfully processed {} product records", products.size());
        } catch (Exception e) {
            logger.error("Error in inventory data processing: {}", e.getMessage(), e);
        }
    }

    @Retryable(
        retryFor = {WebClientResponseException.class, RuntimeException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public List<Product> fetchProductsFromInventory() {
        logger.info("Fetching products from Inventory API: {}/products", inventoryBaseUrl);
        
        try {
            String response = webClient
                .get()
                .uri(inventoryBaseUrl + "/products")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseProductsFromResponse(response);
            
        } catch (WebClientResponseException e) {
            logger.error("API call failed with status: {} and body: {}", 
                        e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch products from Inventory", e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching products: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch products", e);
        }
    }
    
    private List<Product> parseProductsFromResponse(String response) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(response);
        JsonNode dataNode = rootNode.get("data");
        
        List<Product> products = new ArrayList<>();
        
        if (dataNode != null && dataNode.isArray()) {
            for (JsonNode productNode : dataNode) {
                Product product = objectMapper.treeToValue(productNode, Product.class);
                products.add(product);
            }
        }
        
        logger.info("Parsed {} products from API response", products.size());
        return products;
    }
    
    private void publishProductsToKafka(List<Product> products) {
        for (Product product : products) {
            try {
                String productJson = objectMapper.writeValueAsString(product);
                String key = "product_" + product.getId();
                
                kafkaTemplate.send(inventoryTopic, key, productJson)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            logger.debug("Published product {} to Kafka topic {}", 
                                       product.getId(), inventoryTopic);
                        } else {
                            logger.error("Failed to publish product {} to Kafka: {}", 
                                       product.getId(), ex.getMessage());
                        }
                    });
                    
            } catch (JsonProcessingException e) {
                logger.error("Error serializing product {}: {}", product.getId(), e.getMessage());
            }
        }
        
        logger.info("Published {} product records to Kafka topic: {}", products.size(), inventoryTopic);
    }
}