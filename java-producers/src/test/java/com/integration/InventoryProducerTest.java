package com.integration;

import com.integration.producer.InventoryProducerService;
import com.integration.producer.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
public class InventoryProducerTest {

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
    private InventoryProducerService inventoryProducerService;

    private String mockApiResponse;
    
    @BeforeEach
    void setUp() {
        mockApiResponse = """
            {
                "status": "success",
                "data": [
                    {
                        "id": 101,
                        "name": "Laptop",
                        "stock": 50,
                        "price": 999.99,
                        "category": "Electronics"
                    },
                    {
                        "id": 102,
                        "name": "Mouse",
                        "stock": 200,
                        "price": 25.50,
                        "category": "Accessories"
                    }
                ]
            }
            """;
    }

    @Test
    void testFetchProductsFromInventory_Success() throws Exception {
        // Arrange
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockApiResponse));
        
        Product product1 = new Product(101L, "Laptop", 50, 999.99, "Electronics");
        Product product2 = new Product(102L, "Mouse", 200, 25.50, "Accessories");
        
        when(objectMapper.readTree(mockApiResponse)).thenReturn(
            new ObjectMapper().readTree(mockApiResponse)
        );
        when(objectMapper.treeToValue(any(), eq(Product.class)))
            .thenReturn(product1)
            .thenReturn(product2);

        // Act
        List<Product> result = inventoryProducerService.fetchProductsFromInventory();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Laptop", result.get(0).getName());
        assertEquals("Mouse", result.get(1).getName());
        assertEquals(999.99, result.get(0).getPrice());
        assertEquals(50, result.get(0).getStock());
        
        verify(webClient).get();
        verify(objectMapper).readTree(mockApiResponse);
        verify(objectMapper, times(2)).treeToValue(any(), eq(Product.class));
    }

    @Test
    void testFetchProductsFromInventory_ApiFailure() {
        // Arrange
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
            .thenReturn(Mono.error(new WebClientResponseException(404, "Not Found", null, null, null)));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            inventoryProducerService.fetchProductsFromInventory()
        );
        
        assertTrue(exception.getMessage().contains("Failed to fetch products from Inventory"));
        verify(webClient).get();
    }

    @Test
    void testFetchProductsFromInventory_EmptyInventory() throws Exception {
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
        List<Product> result = inventoryProducerService.fetchProductsFromInventory();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
        
        verify(webClient).get();
        verify(objectMapper).readTree(emptyResponse);
        verify(objectMapper, never()).treeToValue(any(), eq(Product.class));
    }

    @Test
    void testFetchProductsFromInventory_LargeInventory() throws Exception {
        // Arrange - Test with larger dataset
        String largeResponse = """
            {
                "status": "success",
                "data": [
                    {"id": 1, "name": "Product1", "stock": 100, "price": 10.0, "category": "Cat1"},
                    {"id": 2, "name": "Product2", "stock": 0, "price": 20.0, "category": "Cat2"},
                    {"id": 3, "name": "Product3", "stock": 50, "price": 30.0, "category": "Cat3"}
                ]
            }
            """;
            
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(largeResponse));
        
        Product product1 = new Product(1L, "Product1", 100, 10.0, "Cat1");
        Product product2 = new Product(2L, "Product2", 0, 20.0, "Cat2");
        Product product3 = new Product(3L, "Product3", 50, 30.0, "Cat3");
        
        when(objectMapper.readTree(largeResponse)).thenReturn(
            new ObjectMapper().readTree(largeResponse)
        );
        when(objectMapper.treeToValue(any(), eq(Product.class)))
            .thenReturn(product1)
            .thenReturn(product2)
            .thenReturn(product3);

        // Act
        List<Product> result = inventoryProducerService.fetchProductsFromInventory();

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        
        // Verify out of stock product is still included
        assertEquals(0, result.get(1).getStock());
        assertEquals("Product2", result.get(1).getName());
        
        verify(objectMapper, times(3)).treeToValue(any(), eq(Product.class));
    }

    @Test
    void testFetchProductsFromInventory_MalformedResponse() throws Exception {
        // Arrange
        String malformedResponse = """
            {
                "status": "success",
                "data": [
                    {"id": "invalid", "name": null, "stock": -1}
                ]
            }
            """;
            
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(malformedResponse));
        
        when(objectMapper.readTree(malformedResponse)).thenReturn(
            new ObjectMapper().readTree(malformedResponse)
        );
        when(objectMapper.treeToValue(any(), eq(Product.class)))
            .thenThrow(new RuntimeException("Invalid product data"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            inventoryProducerService.fetchProductsFromInventory()
        );
        
        assertTrue(exception.getMessage().contains("Failed to fetch products"));
        verify(webClient).get();
        verify(objectMapper).readTree(malformedResponse);
    }

    @Test
    void testRetryMechanism_EventualSuccess() throws Exception {
        // Arrange - First two calls fail, third succeeds
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
            .thenReturn(Mono.error(new WebClientResponseException(500, "Internal Server Error", null, null, null)))
            .thenReturn(Mono.error(new WebClientResponseException(503, "Service Unavailable", null, null, null)))
            .thenReturn(Mono.just(mockApiResponse));
        
        Product product = new Product(101L, "Laptop", 50, 999.99, "Electronics");
        
        when(objectMapper.readTree(mockApiResponse)).thenReturn(
            new ObjectMapper().readTree(mockApiResponse)
        );
        when(objectMapper.treeToValue(any(), eq(Product.class))).thenReturn(product);

        // Act
        List<Product> result = inventoryProducerService.fetchProductsFromInventory();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size()); // Based on mock response having 2 products
        
        // Verify retry happened multiple times
        verify(webClient, atLeast(3)).get();
    }

    @Test
    void testInventoryResponseWithoutDataField() throws Exception {
        // Arrange - Response without 'data' field
        String responseWithoutData = """
            {
                "status": "success",
                "products": []
            }
            """;
            
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseWithoutData));
        
        when(objectMapper.readTree(responseWithoutData)).thenReturn(
            new ObjectMapper().readTree(responseWithoutData)
        );

        // Act
        List<Product> result = inventoryProducerService.fetchProductsFromInventory();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size()); // Should handle missing 'data' field gracefully
        
        verify(webClient).get();
        verify(objectMapper).readTree(responseWithoutData);
        verify(objectMapper, never()).treeToValue(any(), eq(Product.class));
    }
}