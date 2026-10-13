package com.example.model;

public class Customer {
    private Integer id;
    private String name;
    private String email;
    private String phone;
    private String address;
    private Boolean portalAccess;
    private String password;  // <-- added password field

    // Getters and setters
    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
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

    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = address;
    }

    public Boolean getPortalAccess() {
        return portalAccess;
    }
    public void setPortalAccess(Boolean portalAccess) {
        this.portalAccess = portalAccess;
    }

    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
}
