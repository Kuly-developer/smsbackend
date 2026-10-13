package com.example.model;

import java.util.List;

public class Order {
    private Integer id;
    private Integer customerId;
    private Double total;
    private String status;
    private List<OrderItem> items;

    // --- Constructors ---
    public Order() {
    }

    public Order(Integer id, Integer customerId, Double total, String status, List<OrderItem> items) {
        this.id = id;
        this.customerId = customerId;
        this.total = total;
        this.status = status;
        this.items = items;
    }

    // --- Getters & Setters ---
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
    }

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    // --- Utility ---
    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", customerId=" + customerId +
                ", total=" + total +
                ", status='" + status + '\'' +
                ", items=" + items +
                '}';
    }
}
