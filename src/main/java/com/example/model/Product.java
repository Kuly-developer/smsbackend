package com.example.model;

public class Product {
    private Integer id;
    private String name;
    private String description;
    private Double price;
    private Integer stock;
    private String image_url;      // changed
    private Integer salesperson_id; // changed

    public Product(Integer id, String name, String description, Double price, Integer stock, String image_url, Integer salesperson_id) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.image_url = image_url;
        this.salesperson_id = salesperson_id;
    }

    // Getters and setters
    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Double getPrice() { return price; }
    public Integer getStock() { return stock; }
    public String getImage_url() { return image_url; }
    public Integer getSalesperson_id() { return salesperson_id; }

    public void setId(Integer id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(Double price) { this.price = price; }
    public void setStock(Integer stock) { this.stock = stock; }
    public void setImage_url(String image_url) { this.image_url = image_url; }
    public void setSalesperson_id(Integer salesperson_id) { this.salesperson_id = salesperson_id; }
}
