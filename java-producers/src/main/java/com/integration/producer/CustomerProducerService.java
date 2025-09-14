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
public class CustomerProducerService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerProducerService.class);
    
    private final WebClient webClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${api.crm.base-url}")
    private String crmBaseUrl;
    
    @Value("${kafka.topics.customer-data}")
    private String customerTopic;
    
    public CustomerProducerService(WebClient.Builder webClientBuilder, 
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRateString = "${scheduler.customer.interval}")
    public void fetchAndPublishCustomers() {
        logger.info("Starting customer data fetch and publish process");
        
        try {
            List<Customer> customers = fetchCustomersFromCRM();
            publishCustomersToKafka(customers);
            logger.info("Successfully processed {} customer records", customers.size());
        } catch (Exception e) {
            logger.error("Error in customer data processing: {}", e.getMessage(), e);
        }
    }

    @Retryable(
        retryFor = {WebClientResponseException.class, RuntimeException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public List<Customer> fetchCustomersFromCRM() {
        logger.info("Fetching customers from CRM API: {}/customers", crmBaseUrl);
        
        try {
            String response = webClient
                .get()
                .uri(crmBaseUrl + "/customers")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseCustomersFromResponse(response);
            
        } catch (WebClientResponseException e) {
            logger.error("API call failed with status: {} and body: {}", 
                        e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch customers from CRM", e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching customers: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch customers", e);
        }
    }
    
    private List<Customer> parseCustomersFromResponse(String response) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(response);
        JsonNode dataNode = rootNode.get("data");
        
        List<Customer> customers = new ArrayList<>();
        
        if (dataNode != null && dataNode.isArray()) {
            for (JsonNode customerNode : dataNode) {
                Customer customer = objectMapper.treeToValue(customerNode, Customer.class);
                customers.add(customer);
            }
        }
        
        logger.info("Parsed {} customers from API response", customers.size());
        return customers;
    }
    
    private void publishCustomersToKafka(List<Customer> customers) {
        for (Customer customer : customers) {
            try {
                String customerJson = objectMapper.writeValueAsString(customer);
                String key = "customer_" + customer.getId();
                
                kafkaTemplate.send(customerTopic, key, customerJson)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            logger.debug("Published customer {} to Kafka topic {}", 
                                       customer.getId(), customerTopic);
                        } else {
                            logger.error("Failed to publish customer {} to Kafka: {}", 
                                       customer.getId(), ex.getMessage());
                        }
                    });
                    
            } catch (JsonProcessingException e) {
                logger.error("Error serializing customer {}: {}", customer.getId(), e.getMessage());
            }
        }
        
        logger.info("Published {} customer records to Kafka topic: {}", customers.size(), customerTopic);
    }
}