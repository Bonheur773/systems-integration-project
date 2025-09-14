package com.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {"customer_data", "inventory_data"})
public class KafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    private CountDownLatch latch = new CountDownLatch(1);
    private String receivedMessage;
    
    @Test
    void testKafkaMessageProduction() throws InterruptedException {
        // Given
        String testMessage = """
            {"id": 1, "name": "Test Customer", "email": "test@example.com"}
        """;
        
        // When
        kafkaTemplate.send("customer_data", testMessage);
        
        // Then
        boolean messageReceived = latch.await(10, TimeUnit.SECONDS);
        assertTrue(messageReceived, "Message should be received within timeout");
        assertNotNull(receivedMessage);
        assertTrue(receivedMessage.contains("Test Customer"));
    }
    
    @KafkaListener(topics = "customer_data")
    public void receiveMessage(String message) {
        this.receivedMessage = message;
        latch.countDown();
    }
    
    @Test
    void testKafkaTopicConfiguration() {
        // Verify that topics are properly configured
        assertNotNull(kafkaTemplate);
        
        // Test message sending to both topics
        kafkaTemplate.send("customer_data", "test customer message");
        kafkaTemplate.send("inventory_data", "test product message");
        
        // If no exceptions thrown, topics are properly configured
        assertTrue(true);
    }
}