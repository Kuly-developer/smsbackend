package com.example.service;

import org.mindrot.jbcrypt.BCrypt;

import com.example.model.Customer;
import com.example.model.User;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class AuthService {
    private final PgPool client;

    public AuthService(PgPool client) {
        this.client = client;
    }

    public Future<User> getUserByUsername(String username) {
        String query = "SELECT * FROM users WHERE username = $1";
        return client.preparedQuery(query)
                .execute(Tuple.of(username))
                .map(rows -> {
                    if (rows.rowCount() == 0) return null;
                    Row row = rows.iterator().next();
                    return new User(
                            row.getInteger("id"),
                            row.getString("username"),
                            row.getString("password_hash"),
                            row.getInteger("role_id"),
                            row.getString("full_name"),
                            row.getString("email")
                    );
                });
    }

    public boolean checkPassword(String plainPassword, String hashedPassword) {
        try {
            if (hashedPassword == null) return false;

            hashedPassword = hashedPassword.trim();

            // Normalize bcrypt prefix to $2a$ for compatibility with jBCrypt
            if (hashedPassword.startsWith("$2y$") || hashedPassword.startsWith("$2b$")) {
                hashedPassword = "$2a$" + hashedPassword.substring(4);
            }

            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid bcrypt hash format: " + e.getMessage());
            return false;
        }
    }

    // NEW: Get customer by email
    public Future<Customer> getCustomerByEmail(String email) {
        Promise<Customer> promise = Promise.promise();
        client.preparedQuery("SELECT id, name, email, phone, address, portal_access, password FROM customers WHERE email = $1")
            .execute(Tuple.of(email), ar -> {
                if (ar.succeeded() && ar.result().size() > 0) {
                    Row row = ar.result().iterator().next();
                    Customer customer = new Customer();
                    customer.setId(row.getInteger("id"));
                    customer.setName(row.getString("name"));
                    customer.setEmail(row.getString("email"));
                    customer.setPhone(row.getString("phone"));
                    customer.setAddress(row.getString("address"));
                    customer.setPortalAccess(row.getBoolean("portal_access"));
                    customer.setPassword(row.getString("password")); // store hash in password field
                    promise.complete(customer);
                } else {
                    promise.complete(null);
                }
            });
        return promise.future();
    }
}
