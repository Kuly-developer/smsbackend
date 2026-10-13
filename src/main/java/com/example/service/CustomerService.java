package com.example.service;

import org.mindrot.jbcrypt.BCrypt;

import com.example.model.Customer;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.SqlConnection;

public class CustomerService {

    private final PgPool client;

    public CustomerService(PgPool client) {
        this.client = client;
    }

    // Register a new customer with hashed password
    public Future<Customer> registerCustomer(Customer customer, String plainPassword) {
        Promise<Customer> promise = Promise.promise();

        try {
            // Hash password only if provided
            String hashedPassword = null;
            if (plainPassword != null && !plainPassword.isEmpty()) {
                hashedPassword = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
            } else {
                hashedPassword = ""; // store empty string if no password
            }

            String query = "INSERT INTO customers (name, email, phone, address, portal_access, password) VALUES ($1, $2, $3, $4, $5, $6) RETURNING id, name, email, phone, address, portal_access";

            client.preparedQuery(query)
                  .execute(Tuple.of(
                          customer.getName(),
                          customer.getEmail(),
                          customer.getPhone(),
                          customer.getAddress(),
                          customer.getPortalAccess() != null ? customer.getPortalAccess() : false,
                          hashedPassword
                  ))
                  .onSuccess(res -> {
                      if (res.iterator().hasNext()) {
                          Row row = res.iterator().next();
                          Customer savedCustomer = new Customer();
                          savedCustomer.setId(row.getInteger("id"));
                          savedCustomer.setName(row.getString("name"));
                          savedCustomer.setEmail(row.getString("email"));
                          savedCustomer.setPhone(row.getString("phone"));
                          savedCustomer.setAddress(row.getString("address"));
                          savedCustomer.setPortalAccess(row.getBoolean("portal_access"));
                          promise.complete(savedCustomer);
                      } else {
                          promise.fail("Failed to create customer - no data returned");
                      }
                  })
                  .onFailure(err -> {
                      System.err.println("Database error in registerCustomer: " + err.getMessage());
                      // Check for duplicate email
                      if (err.getMessage().contains("duplicate key") || err.getMessage().contains("unique constraint")) {
                          promise.fail("Email already exists");
                      } else {
                          promise.fail("Database error: " + err.getMessage());
                      }
                  });
        } catch (Exception e) {
            System.err.println("Error in registerCustomer: " + e.getMessage());
            promise.fail("Service error: " + e.getMessage());
        }

        return promise.future();
    }

    // Get all customers (exclude password)
    public Future<JsonArray> getAllCustomers() {
        Promise<JsonArray> promise = Promise.promise();

        try {
            String query = "SELECT id, name, email, phone, address, portal_access FROM customers ORDER BY id";

            client.query(query)
                  .execute()
                  .onSuccess(rows -> {
                      JsonArray customers = new JsonArray();
                      for (Row row : rows) {
                          JsonObject json = new JsonObject()
                                  .put("id", row.getInteger("id"))
                                  .put("name", row.getString("name"))
                                  .put("email", row.getString("email"))
                                  .put("phone", row.getString("phone"))
                                  .put("address", row.getString("address"))
                                  .put("portalAccess", row.getBoolean("portal_access"));
                          customers.add(json);
                      }
                      promise.complete(customers);
                  })
                  .onFailure(err -> {
                      System.err.println("Database error in getAllCustomers: " + err.getMessage());
                      promise.fail("Database error: " + err.getMessage());
                  });
        } catch (Exception e) {
            System.err.println("Error in getAllCustomers: " + e.getMessage());
            promise.fail("Service error: " + e.getMessage());
        }

        return promise.future();
    }

    // Update customer details
    public Future<Customer> updateCustomer(Customer customer) {
        Promise<Customer> promise = Promise.promise();

        try {
            String query = "UPDATE customers SET name=$1, email=$2, phone=$3, address=$4, portal_access=$5 WHERE id=$6 RETURNING id, name, email, phone, address, portal_access";

            client.preparedQuery(query)
                  .execute(Tuple.of(
                          customer.getName(),
                          customer.getEmail(),
                          customer.getPhone(),
                          customer.getAddress(),
                          customer.getPortalAccess() != null ? customer.getPortalAccess() : false,
                          customer.getId()
                  ))
                  .onSuccess(res -> {
                      if (res.rowCount() == 0) {
                          promise.fail("Customer not found with ID: " + customer.getId());
                      } else {
                          Row row = res.iterator().next();
                          Customer updatedCustomer = new Customer();
                          updatedCustomer.setId(row.getInteger("id"));
                          updatedCustomer.setName(row.getString("name"));
                          updatedCustomer.setEmail(row.getString("email"));
                          updatedCustomer.setPhone(row.getString("phone"));
                          updatedCustomer.setAddress(row.getString("address"));
                          updatedCustomer.setPortalAccess(row.getBoolean("portal_access"));
                          promise.complete(updatedCustomer);
                      }
                  })
                  .onFailure(err -> {
                      System.err.println("Database error in updateCustomer: " + err.getMessage());
                      // Check for duplicate email
                      if (err.getMessage().contains("duplicate key") || err.getMessage().contains("unique constraint")) {
                          promise.fail("Email already exists");
                      } else {
                          promise.fail("Database error: " + err.getMessage());
                      }
                  });
        } catch (Exception e) {
            System.err.println("Error in updateCustomer: " + e.getMessage());
            promise.fail("Service error: " + e.getMessage());
        }

        return promise.future();
    }

    // Delete a customer by ID with proper foreign key constraint handling
    public Future<Void> deleteCustomer(int id) {
        Promise<Void> promise = Promise.promise();

        try {
            // First, check if customer exists
            String checkQuery = "SELECT id FROM customers WHERE id=$1";

            client.preparedQuery(checkQuery)
                  .execute(Tuple.of(id))
                  .onSuccess(checkRes -> {
                      if (!checkRes.iterator().hasNext()) {
                          promise.fail("Customer not found with ID: " + id);
                          return;
                      }

                      // Use getConnection to start a transaction
                      client.getConnection()
                            .onSuccess(connection -> {
                                // Start transaction
                                connection.begin()
                                          .onSuccess(tx -> {
                                              // Delete from cart table first
                                              String deleteCartQuery = "DELETE FROM cart WHERE customer_id=$1";

                                              connection.preparedQuery(deleteCartQuery)
                                                        .execute(Tuple.of(id))
                                                        .compose(cartRes -> {
                                                            // Now delete the customer
                                                            String deleteCustomerQuery = "DELETE FROM customers WHERE id=$1";
                                                            return connection.preparedQuery(deleteCustomerQuery).execute(Tuple.of(id));
                                                        })
                                                        .onSuccess(finalRes -> {
                                                            // Commit the transaction
                                                            tx.commit()
                                                              .onSuccess(commitRes -> {
                                                                  connection.close(); // Always close connection
                                                                  if (finalRes.rowCount() > 0) {
                                                                      promise.complete();
                                                                  } else {
                                                                      promise.fail("Failed to delete customer with ID: " + id);
                                                                  }
                                                              })
                                                              .onFailure(commitErr -> {
                                                                  tx.rollback();
                                                                  connection.close(); // Always close connection
                                                                  promise.fail("Failed to commit transaction: " + commitErr.getMessage());
                                                              });
                                                        })
                                                        .onFailure(err -> {
                                                            // Rollback transaction on error
                                                            tx.rollback();
                                                            connection.close(); // Always close connection
                                                            System.err.println("Error in deletion transaction: " + err.getMessage());

                                                            // Provide specific error messages
                                                            if (err.getMessage().contains("foreign key constraint")) {
                                                                if (err.getMessage().contains("cart_customer_id_fkey")) {
                                                                    promise.fail("Cannot delete customer. Customer has items in their cart. Please clear the cart first.");
                                                                } else {
                                                                    promise.fail("Cannot delete customer. This customer is referenced in other records.");
                                                                }
                                                            } else {
                                                                promise.fail("Database error: " + err.getMessage());
                                                            }
                                                        });
                                          })
                                          .onFailure(txErr -> {
                                              connection.close(); // Always close connection
                                              promise.fail("Failed to start transaction: " + txErr.getMessage());
                                          });
                            })
                            .onFailure(connErr -> {
                                promise.fail("Failed to get database connection: " + connErr.getMessage());
                            });
                  })
                  .onFailure(checkErr -> {
                      System.err.println("Database error checking customer: " + checkErr.getMessage());
                      promise.fail("Database error: " + checkErr.getMessage());
                  });
        } catch (Exception e) {
            System.err.println("Error in deleteCustomer: " + e.getMessage());
            promise.fail("Service error: " + e.getMessage());
        }

        return promise.future();
    }

    // Simplified delete method without transaction (recommended)
    public Future<Void> deleteCustomerSimple(int id) {
        Promise<Void> promise = Promise.promise();

        try {
            // First, check if customer exists
            String checkQuery = "SELECT id FROM customers WHERE id=$1";

            client.preparedQuery(checkQuery)
                  .execute(Tuple.of(id))
                  .onSuccess(checkRes -> {
                      if (!checkRes.iterator().hasNext()) {
                          promise.fail("Customer not found with ID: " + id);
                          return;
                      }

                      // First delete from cart table
                      String deleteCartQuery = "DELETE FROM cart WHERE customer_id=$1";

                      client.preparedQuery(deleteCartQuery)
                            .execute(Tuple.of(id))
                            .compose(cartRes -> {
                                // Now delete the customer
                                String deleteCustomerQuery = "DELETE FROM customers WHERE id=$1";
                                return client.preparedQuery(deleteCustomerQuery).execute(Tuple.of(id));
                            })
                            .onSuccess(finalRes -> {
                                if (finalRes.rowCount() > 0) {
                                    promise.complete();
                                } else {
                                    promise.fail("Failed to delete customer with ID: " + id);
                                }
                            })
                            .onFailure(err -> {
                                System.err.println("Error in deletion process: " + err.getMessage());

                                if (err.getMessage().contains("foreign key constraint")) {
                                    if (err.getMessage().contains("cart_customer_id_fkey")) {
                                        promise.fail("Cannot delete customer. Customer has items in their cart. Please clear the cart first or use the advanced delete method.");
                                    } else {
                                        promise.fail("Cannot delete customer. This customer is referenced in other records.");
                                    }
                                } else {
                                    promise.fail("Database error: " + err.getMessage());
                                }
                            });
                  })
                  .onFailure(checkErr -> {
                      System.err.println("Database error checking customer: " + checkErr.getMessage());
                      promise.fail("Database error: " + checkErr.getMessage());
                  });
        } catch (Exception e) {
            System.err.println("Error in deleteCustomerSimple: " + e.getMessage());
            promise.fail("Service error: " + e.getMessage());
        }

        return promise.future();
    }

    // Customer login
    public Future<JsonObject> login(String email, String plainPassword) {
        Promise<JsonObject> promise = Promise.promise();

        try {
            String query = "SELECT * FROM customers WHERE email=$1";

            client.preparedQuery(query)
                  .execute(Tuple.of(email))
                  .onSuccess(rows -> {
                      if (!rows.iterator().hasNext()) {
                          promise.fail("Invalid email or password");
                          return;
                      }

                      Row row = rows.iterator().next();
                      String hashedPassword = row.getString("password");

                      // Check if customer has portal access
                      if (!row.getBoolean("portal_access")) {
                          promise.fail("Portal access not enabled for this customer");
                          return;
                      }

                      // Check if password is set and matches
                      if (hashedPassword == null || hashedPassword.isEmpty() ||
                          !BCrypt.checkpw(plainPassword, hashedPassword)) {
                          promise.fail("Invalid email or password");
                          return;
                      }

                      JsonObject customer = new JsonObject()
                              .put("id", row.getInteger("id"))
                              .put("name", row.getString("name"))
                              .put("email", row.getString("email"))
                              .put("phone", row.getString("phone"))
                              .put("address", row.getString("address"))
                              .put("portalAccess", row.getBoolean("portal_access"));

                      promise.complete(customer);
                  })
                  .onFailure(err -> {
                      System.err.println("Database error in login: " + err.getMessage());
                      promise.fail("Database error: " + err.getMessage());
                  });
        } catch (Exception e) {
            System.err.println("Error in login: " + e.getMessage());
            promise.fail("Service error: " + e.getMessage());
        }

        return promise.future();
    }

    // Optional: Method to check if customer can be deleted (no references)
    public Future<Boolean> canDeleteCustomer(int id) {
        Promise<Boolean> promise = Promise.promise();

        try {
            // Check for references in cart table
            String checkCartQuery = "SELECT COUNT(*) as cart_count FROM cart WHERE customer_id=$1";

            client.preparedQuery(checkCartQuery)
                  .execute(Tuple.of(id))
                  .onSuccess(res -> {
                      Row row = res.iterator().next();
                      long cartCount = row.getLong("cart_count");

                      // Add checks for other tables if needed
                      // For now, just check cart table
                      promise.complete(cartCount == 0);
                  })
                  .onFailure(err -> {
                      System.err.println("Error checking customer references: " + err.getMessage());
                      promise.fail("Error checking customer references: " + err.getMessage());
                  });
        } catch (Exception e) {
            System.err.println("Error in canDeleteCustomer: " + e.getMessage());
            promise.fail("Service error: " + e.getMessage());
        }

        return promise.future();
    }

    // Method to clear customer cart before deletion (if needed)
    public Future<Void> clearCustomerCart(int customerId) {
        Promise<Void> promise = Promise.promise();

        try {
            String deleteCartQuery = "DELETE FROM cart WHERE customer_id=$1";

            client.preparedQuery(deleteCartQuery)
                  .execute(Tuple.of(customerId))
                  .onSuccess(res -> {
                      promise.complete();
                  })
                  .onFailure(err -> {
                      System.err.println("Error clearing customer cart: " + err.getMessage());
                      promise.fail("Failed to clear customer cart: " + err.getMessage());
                  });
        } catch (Exception e) {
            System.err.println("Error in clearCustomerCart: " + e.getMessage());
            promise.fail("Service error: " + e.getMessage());
        }

        return promise.future();
    }
}
