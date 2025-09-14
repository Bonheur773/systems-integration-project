package com.integration;

import com.integration.producer.CustomerProducerService;
import com.integration.producer.Customer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomerProducerTest {

    @Mock
    private WebClient webClient;
    
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    
    @Mock
    private WebClient.ResponseSpec responseSpec;
    
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private CustomerProducerService customerProducerService;

    private String mockApiResponse;
    
    @BeforeEach
    void setUp() {
        mockApiResponse = """
            {
                "status": "success",
                "data": [
                    {
                        "id": 1,
                        "name": "John Doe",
                        "email": "john@example.com",
                        "created_date": "2024-01-15",
                        "status": "active"
                    },
                    {
                        "id": 2,
                        "name": "Jane Smith",
                        "email": "jane@example.com",
                        "created_date": "2024-01-16",
                        "status": "active"
                    }
                ]
            }
            """;
    }

    @Test
    void testFetchCustomersFromCRM_Success() throws Exception {
        // Arrange
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockApiResponse));
        
        Customer customer1 = new Customer(1L, "John Doe", "john@example.com", "2024-01-15", "active");
        Customer customer2 = new Customer(2L, "Jane Smith", "jane@example.com", "2024-01-16", "active");
        
        when(objectMapper.readTree(mockApiResponse)).thenReturn(
            new ObjectMapper().readTree(mockApiResponse)
        );
        when(objectMapper.treeToValue(any(), eq(Customer.class)))
            .thenReturn(customer1)
            .thenReturn(customer2);

        // Act
        List<Customer> result = customerProducerService.fetchCustomersFromCRM();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("John Doe", result.get(0).getName());
        assertEquals("Jane Smith", result.get(1).getName());
        
        verify(webClient).get();
        verify(objectMapper).readTree(mockApiResponse);
        verify(objectMapper, times(2)).treeToValue(any(), eq(Customer.class));
    }

    @Test
    void testFetchCustomersFromCRM_ApiFailure() {
        // Arrange
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
            .thenReturn(Mono.error(new WebClientResponseException(500, "Internal Server Error", null, null, null)));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            customerProducerService.fetchCustomersFromCRM()
        );
        
        assertTrue(exception.getMessage().contains("Failed to fetch customers from CRM"));
        verify(webClient).get();
    }

    @Test
    void testFetchCustomersFromCRM_EmptyResponse() throws Exception {
        // Arrange
        String emptyResponse = """
            {
                "status": "success",
                "data": []
            }
            """;
            
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(emptyResponse));
        
        when(objectMapper.readTree(emptyResponse)).thenReturn(
            new ObjectMapper().readTree(emptyResponse)
        );

        // Act
        List<Customer> result = customerProducerService.fetchCustomersFromCRM();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
        
        verify(webClient).get();
        verify(objectMapper).readTree(emptyResponse);
        verify(objectMapper, never()).treeToValue(any(), eq(Customer.class));
    }

    @Test
    void testFetchCustomersFromCRM_InvalidJsonResponse() throws Exception {
        // Arrange
        String invalidResponse = "invalid json response";
        
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(invalidResponse));
        
        when(objectMapper.readTree(invalidResponse))
            .thenThrow(new RuntimeException("Invalid JSON"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            customerProducerService.fetchCustomersFromCRM()
        );
        
        assertTrue(exception.getMessage().contains("Failed to fetch customers"));
        verify(webClient).get();
        verify(objectMapper).readTree(invalidResponse);
    }

    @Test
    void testRetryMechanism_Success() throws Exception {
        // Arrange - First call fails, second succeeds
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
            .thenReturn(Mono.error(new WebClientResponseException(503, "Service Unavailable", null, null, null)))
            .thenReturn(Mono.just(mockApiResponse));
        
        Customer customer = new Customer(1L, "John Doe", "john@example.com", "2024-01-15", "active");
        
        when(objectMapper.readTree(mockApiResponse)).thenReturn(
            new ObjectMapper().readTree(mockApiResponse)
        );
        when(objectMapper.treeToValue(any(), eq(Customer.class))).thenReturn(customer);

        // Act
        List<Customer> result = customerProducerService.fetchCustomersFromCRM();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size()); // Based on mock response having 2 customers
        
        // Verify retry happened (webClient.get() called multiple times)
        verify(webClient, atLeast(2)).get();
    }
}