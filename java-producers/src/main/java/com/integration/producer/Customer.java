package com.integration.producer;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Customer {
    
    private Long id;
    private String name;
    private String email;
    
    @JsonProperty("created_date")
    private String createdDate;
    
    private String status;
    
    // Constructors
    public Customer() {}
    
    public Customer(Long id, String name, String email, String createdDate, String status) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.createdDate = createdDate;
        this.status = status;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getCreatedDate() {
        return createdDate;
    }
    
    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    @Override
    public String toString() {
        return "Customer{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", createdDate='" + createdDate + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}