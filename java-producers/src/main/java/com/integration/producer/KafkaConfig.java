package com.integration.producer;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class KafkaConfig {
    
    @Value("${kafka.topics.customer-data}")
    private String customerTopic;
    
    @Value("${kafka.topics.inventory-data}")
    private String inventoryTopic;
    
    @Bean
    public NewTopic customerDataTopic() {
        return TopicBuilder.name(customerTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean
    public NewTopic inventoryDataTopic() {
        return TopicBuilder.name(inventoryTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}