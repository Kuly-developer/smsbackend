package com.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.vertx.core.json.JsonObject;

public class User {
    private int id;
    private String username;
    private String passwordHash;
    private int roleId;
    private String fullName;
    private String email;

    // 🔹 NEW: transient password for incoming JSON
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    public User() {}

    public User(int id, String username, String passwordHash, int roleId, String fullName, String email) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roleId = roleId;
        this.fullName = fullName;
        this.email = email;
    }

    // getters/setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public int getRoleId() { return roleId; }
    public void setRoleId(int roleId) { this.roleId = roleId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    // 🔹 getter/setter for transient password
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    // Convert to JsonObject (without passwordHash)
    public JsonObject toJson() {
        return new JsonObject()
            .put("id", this.id)
            .put("username", this.username)
            .put("fullName", this.fullName)
            .put("email", this.email)
            .put("roleId", this.roleId);
    }
}
