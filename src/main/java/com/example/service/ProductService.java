package com.example.service;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

public class ProductService {
    private final PgPool client;

    public ProductService(PgPool client) {
        this.client = client;
    }

    // Getter for client (needed for debug routes)
    public PgPool getClient() {
        return this.client;
    }

    // ---------- Public Methods ----------
    public void getAllPublic(RoutingContext ctx) {
        String query = "SELECT p.*, c.name as category_name FROM products p " +
                       "LEFT JOIN categories c ON p.category_id = c.id";
        client.preparedQuery(query)
            .execute()
            .onSuccess(rows -> {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(jsonArrayFromRows(rows).encode());
            })
            .onFailure(err -> {
                System.err.println("❌ Error in getAllPublic: " + err.getMessage());
                err.printStackTrace();
                ctx.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                        .put("error", "Database error")
                        .put("details", err.getMessage())
                        .encode());
            });
    }

    public void deletePublic(RoutingContext ctx) {
        System.out.println("DELETE PUBLIC endpoint called");

        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            System.out.println("Attempting to delete product ID: " + id);

            // First, delete related records from cart table
            String deleteCartQuery = "DELETE FROM cart WHERE product_id = $1";

            client.preparedQuery(deleteCartQuery)
                .execute(Tuple.of(id))
                .compose(cartRows -> {
                    System.out.println("Deleted cart records: " + cartRows.rowCount());

                    // Now delete the product
                    String deleteProductQuery = "DELETE FROM products WHERE id = $1";
                    return client.preparedQuery(deleteProductQuery)
                        .execute(Tuple.of(id));
                })
                .onSuccess(rows -> {
                    System.out.println("Delete successful, products affected: " + rows.rowCount());
                    if (rows.rowCount() > 0) {
                        ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("message", "Product deleted successfully").encode());
                    } else {
                        ctx.response()
                            .setStatusCode(404)
                            .end(new JsonObject().put("error", "Product not found").encode());
                    }
                })
                .onFailure(err -> {
                    System.err.println("❌ Delete failed: " + err.getMessage());
                    err.printStackTrace();
                    ctx.response()
                        .setStatusCode(500)
                        .end(new JsonObject().put("error", "Delete failed: " + err.getMessage()).encode());
                });
        } catch (NumberFormatException e) {
            System.err.println("Invalid product ID format");
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject().put("error", "Invalid product ID").encode());
        }
    }

    // ---------- Authenticated Methods ----------
    public void getAll(RoutingContext ctx) {
        Integer salespersonId = ctx.get("userId");
        if (salespersonId == null) {
            System.err.println("❌ Unauthorized access attempt to getAll");
            ctx.response().setStatusCode(401).end(new JsonObject()
                .put("error", "Unauthorized")
                .put("details", "User ID not found in context")
                .encode());
            return;
        }

        // Updated query to include category information
        String query = "SELECT p.*, c.name as category_name FROM products p " +
                       "LEFT JOIN categories c ON p.category_id = c.id " +
                       "WHERE p.salesperson_id = $1";
        client.preparedQuery(query)
            .execute(Tuple.of(salespersonId))
            .onSuccess(rows -> {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(jsonArrayFromRows(rows).encode());
            })
            .onFailure(err -> {
                System.err.println("❌ Error in getAll for salesperson " + salespersonId + ": " + err.getMessage());
                err.printStackTrace();
                ctx.response()
                    .setStatusCode(500)
                    .end(new JsonObject()
                        .put("error", "Database error")
                        .put("details", err.getMessage())
                        .encode());
            });
    }

    public void getById(RoutingContext ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Integer salespersonId = ctx.get("userId");

            if (salespersonId == null) {
                ctx.response().setStatusCode(401).end("Unauthorized");
                return;
            }

            // Updated query to include category information
            String query = "SELECT p.*, c.name as category_name FROM products p " +
                           "LEFT JOIN categories c ON p.category_id = c.id " +
                           "WHERE p.id = $1 AND p.salesperson_id = $2";
            client.preparedQuery(query)
                .execute(Tuple.of(id, salespersonId))
                .onSuccess(rows -> {
                    if (!rows.iterator().hasNext()) {
                        ctx.response().setStatusCode(404)
                            .end(new JsonObject().put("error", "Product not found").encode());
                        return;
                    }

                    JsonObject product = rows.iterator().next().toJson();
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(product.encode());
                })
                .onFailure(err -> {
                    System.err.println("❌ Error in getById for product " + id + ": " + err.getMessage());
                    err.printStackTrace();
                    ctx.response().setStatusCode(500)
                        .end(new JsonObject().put("error", err.getMessage()).encode());
                });

        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400)
                .end(new JsonObject().put("error", "Invalid product ID").encode());
        }
    }

    public void create(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            String name = body.getString("name");
            String description = body.getString("description");
            Double price = body.getDouble("price");
            Integer stock = body.getInteger("stock");
            String image_url = body.getString("image_url");
            Integer category_id = body.getInteger("category_id");

            // Validate required fields
            if (name == null || price == null) {
                ctx.response().setStatusCode(400)
                    .end(new JsonObject()
                        .put("error", "Name and price are required")
                        .encode());
                return;
            }

            // Validate category_id is provided (since it's NOT NULL in database)
            if (category_id == null) {
                ctx.response().setStatusCode(400)
                    .end(new JsonObject()
                        .put("error", "Category ID is required")
                        .encode());
                return;
            }

            Integer salespersonId = ctx.get("userId");
            if (salespersonId == null) {
                ctx.response().setStatusCode(401)
                    .end(new JsonObject()
                        .put("error", "Unauthorized")
                        .encode());
                return;
            }

            // Updated query to include category_id
            String query = "INSERT INTO products (name, description, price, stock, image_url, salesperson_id, category_id) " +
                           "VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING id";

            client.preparedQuery(query)
                .execute(Tuple.of(
                    name,
                    description,
                    price,
                    stock != null ? stock : 0,
                    image_url,
                    salespersonId,
                    category_id
                ))
                .onSuccess(rows -> {
                    int id = rows.iterator().next().getInteger("id");
                    body.put("id", id);
                    body.put("salesperson_id", salespersonId);

                    System.out.println("✅ Product created successfully with ID: " + id);
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .setStatusCode(201)
                        .end(body.encode());
                })
                .onFailure(err -> {
                    System.err.println("❌ Error in create: " + err.getMessage());
                    err.printStackTrace();

                    // Provide more specific error message for constraint violations
                    String errorMessage = "Failed to create product";
                    if (err.getMessage().contains("category_id")) {
                        errorMessage = "Invalid category ID";
                    } else if (err.getMessage().contains("salesperson_id")) {
                        errorMessage = "Invalid salesperson ID";
                    }

                    ctx.response()
                        .setStatusCode(500)
                        .end(new JsonObject()
                            .put("error", errorMessage)
                            .put("details", err.getMessage())
                            .encode());
                });
        } catch (Exception e) {
            System.err.println("❌ Exception in create: " + e.getMessage());
            e.printStackTrace();
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject()
                    .put("error", "Invalid request")
                    .put("details", e.getMessage())
                    .encode());
        }
    }

    public void update(RoutingContext ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            JsonObject body = ctx.body().asJsonObject();

            Integer salespersonId = ctx.get("userId");
            if (salespersonId == null) {
                ctx.response().setStatusCode(401)
                    .end(new JsonObject()
                        .put("error", "Unauthorized")
                        .encode());
                return;
            }

            // Check if product exists and belongs to the salesperson
            String checkQuery = "SELECT 1 FROM products WHERE id = $1 AND salesperson_id = $2";
            client.preparedQuery(checkQuery)
                .execute(Tuple.of(id, salespersonId))
                .onSuccess(checkRows -> {
                    if (!checkRows.iterator().hasNext()) {
                        ctx.response().setStatusCode(403)
                            .end(new JsonObject()
                                .put("error", "Forbidden: You do not own this product")
                                .encode());
                        return;
                    }

                    // Validate category_id if provided
                    Integer category_id = body.getInteger("category_id");
                    if (category_id == null) {
                        ctx.response().setStatusCode(400)
                            .end(new JsonObject()
                                .put("error", "Category ID is required")
                                .encode());
                        return;
                    }

                    // Updated query to include category_id
                    String query = "UPDATE products SET name=$1, description=$2, price=$3, stock=$4, image_url=$5, category_id=$6 WHERE id=$7";
                    client.preparedQuery(query)
                        .execute(Tuple.of(
                            body.getString("name"),
                            body.getString("description"),
                            body.getDouble("price"),
                            body.getInteger("stock"),
                            body.getString("image_url"),
                            category_id,
                            id
                        ))
                        .onSuccess(rows -> {
                            System.out.println("✅ Product updated successfully with ID: " + id);
                            ctx.response()
                                .putHeader("Content-Type", "application/json")
                                .end(body.put("id", id).encode());
                        })
                        .onFailure(err -> {
                            System.err.println("❌ Error in update for product " + id + ": " + err.getMessage());
                            err.printStackTrace();
                            ctx.response()
                                .setStatusCode(500)
                                .end(new JsonObject()
                                    .put("error", "Failed to update product")
                                    .put("details", err.getMessage())
                                    .encode());
                        });
                })
                .onFailure(err -> {
                    System.err.println("❌ Error checking ownership for product " + id + ": " + err.getMessage());
                    err.printStackTrace();
                    ctx.response()
                        .setStatusCode(500)
                        .end(new JsonObject()
                            .put("error", "Database error")
                            .put("details", err.getMessage())
                            .encode());
                });
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400)
                .end(new JsonObject()
                    .put("error", "Invalid product ID")
                    .encode());
        }
    }

    public void delete(RoutingContext ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));

            Integer salespersonId = ctx.get("userId");
            if (salespersonId == null) {
                ctx.response().setStatusCode(401)
                    .end(new JsonObject()
                        .put("error", "Unauthorized")
                        .encode());
                return;
            }

            // Check if product exists and belongs to the salesperson
            String checkQuery = "SELECT 1 FROM products WHERE id = $1 AND salesperson_id = $2";
            client.preparedQuery(checkQuery)
                .execute(Tuple.of(id, salespersonId))
                .onSuccess(checkRows -> {
                    if (!checkRows.iterator().hasNext()) {
                        ctx.response().setStatusCode(403)
                            .end(new JsonObject()
                                .put("error", "Forbidden: You do not own this product")
                                .encode());
                        return;
                    }

                    // First delete from cart (foreign key constraint)
                    String deleteCartQuery = "DELETE FROM cart WHERE product_id = $1";
                    client.preparedQuery(deleteCartQuery)
                        .execute(Tuple.of(id))
                        .compose(cartRows -> {
                            // Then delete the product
                            String deleteProductQuery = "DELETE FROM products WHERE id = $1";
                            return client.preparedQuery(deleteProductQuery)
                                .execute(Tuple.of(id));
                        })
                        .onSuccess(rows -> {
                            if (rows.rowCount() > 0) {
                                System.out.println("✅ Product deleted successfully with ID: " + id);
                                ctx.response().setStatusCode(204).end();
                            } else {
                                ctx.response().setStatusCode(404)
                                    .end(new JsonObject()
                                        .put("error", "Product not found")
                                        .encode());
                            }
                        })
                        .onFailure(err -> {
                            System.err.println("❌ Error in delete for product " + id + ": " + err.getMessage());
                            err.printStackTrace();
                            ctx.response()
                                .setStatusCode(500)
                                .end(new JsonObject()
                                    .put("error", "Failed to delete product")
                                    .put("details", err.getMessage())
                                    .encode());
                        });
                })
                .onFailure(err -> {
                    System.err.println("❌ Error checking ownership for product " + id + ": " + err.getMessage());
                    err.printStackTrace();
                    ctx.response()
                        .setStatusCode(500)
                        .end(new JsonObject()
                            .put("error", "Database error")
                            .put("details", err.getMessage())
                            .encode());
                });
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400)
                .end(new JsonObject()
                    .put("error", "Invalid product ID")
                    .encode());
        }
    }

    // ---------- Stock Management Methods ----------
    public void reduceStock(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            JsonArray items = body.getJsonArray("items");

            System.out.println("🔄 Reducing stock for items: " + items);

            if (items == null || items.isEmpty()) {
                ctx.response()
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("error", "Items array is required")
                        .encode());
                return;
            }

            List<Future> updateFutures = new ArrayList<>();

            for (int i = 0; i < items.size(); i++) {
                JsonObject item = items.getJsonObject(i);
                Integer productId = item.getInteger("product_id");
                Integer quantity = item.getInteger("quantity");

                if (productId == null || quantity == null) {
                    ctx.response()
                        .setStatusCode(400)
                        .end(new JsonObject()
                            .put("error", "product_id and quantity are required for all items")
                            .encode());
                    return;
                }

                System.out.println("📦 Reducing stock for product " + productId + " by " + quantity);

                // Update stock for each product
                String updateStock = "UPDATE products SET stock = stock - $1 WHERE id = $2 AND stock >= $1";
                Future<Void> updateFuture = client.preparedQuery(updateStock)
                    .execute(Tuple.of(quantity, productId))
                    .compose(rows -> {
                        if (rows.rowCount() == 0) {
                            return Future.failedFuture("Insufficient stock or product not found for ID: " + productId);
                        }
                        System.out.println("✅ Successfully reduced stock for product " + productId);
                        return Future.succeededFuture();
                    });
                updateFutures.add(updateFuture);
            }

            // Wait for all stock updates to complete
            CompositeFuture.all(updateFutures)
                .onSuccess(v -> {
                    System.out.println("🎉 All stock reductions completed successfully");
                    ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                            .put("message", "Stock reduced successfully for all products")
                            .encode());
                })
                .onFailure(err -> {
                    System.err.println("❌ Failed to reduce stock: " + err.getMessage());
                    err.printStackTrace();
                    ctx.response()
                        .setStatusCode(400)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject()
                            .put("error", "Failed to reduce stock")
                            .put("details", err.getMessage())
                            .encode());
                });

        } catch (Exception e) {
            System.err.println("❌ Error in reduceStock: " + e.getMessage());
            e.printStackTrace();
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject()
                    .put("error", "Invalid request payload")
                    .put("details", e.getMessage())
                    .encode());
        }
    }

    // ---------- Debug Methods ----------
    public void getProductStock(RoutingContext ctx) {
        try {
            int productId = Integer.parseInt(ctx.pathParam("id"));

            String query = "SELECT id, name, stock FROM products WHERE id = $1";
            client.preparedQuery(query)
                .execute(Tuple.of(productId))
                .onSuccess(rows -> {
                    if (!rows.iterator().hasNext()) {
                        ctx.response().setStatusCode(404)
                            .end(new JsonObject().put("error", "Product not found").encode());
                        return;
                    }

                    Row row = rows.iterator().next();
                    JsonObject product = new JsonObject()
                        .put("id", row.getInteger("id"))
                        .put("name", row.getString("name"))
                        .put("stock", row.getInteger("stock"));

                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(product.encode());
                })
                .onFailure(err -> {
                    System.err.println("❌ Error in getProductStock: " + err.getMessage());
                    err.printStackTrace();
                    ctx.response().setStatusCode(500)
                        .end(new JsonObject().put("error", err.getMessage()).encode());
                });

        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400)
                .end(new JsonObject().put("error", "Invalid product ID").encode());
        }
    }

    public void manualStockUpdate(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            Integer productId = body.getInteger("product_id");
            Integer quantity = body.getInteger("quantity");

            if (productId == null || quantity == null) {
                ctx.response().setStatusCode(400)
                    .end(new JsonObject()
                        .put("error", "product_id and quantity are required")
                        .encode());
                return;
            }

            System.out.println("🔧 Manual stock update - Product: " + productId + ", Reduce by: " + quantity);

            String updateStock = "UPDATE products SET stock = stock - $1 WHERE id = $2 AND stock >= $1";
            client.preparedQuery(updateStock)
                .execute(Tuple.of(quantity, productId))
                .onSuccess(rows -> {
                    if (rows.rowCount() > 0) {
                        System.out.println("✅ Manual stock update successful");
                        ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject()
                                .put("message", "Stock updated successfully")
                                .put("rows_affected", rows.rowCount())
                                .encode());
                    } else {
                        // Check if product exists but has insufficient stock
                        String checkStock = "SELECT stock FROM products WHERE id = $1";
                        client.preparedQuery(checkStock)
                            .execute(Tuple.of(productId))
                            .onSuccess(stockRows -> {
                                if (stockRows.iterator().hasNext()) {
                                    int currentStock = stockRows.iterator().next().getInteger("stock");
                                    ctx.response().setStatusCode(400)
                                        .end(new JsonObject()
                                            .put("error", "Insufficient stock")
                                            .put("current_stock", currentStock)
                                            .put("requested", quantity)
                                            .encode());
                                } else {
                                    ctx.response().setStatusCode(404)
                                        .end(new JsonObject()
                                            .put("error", "Product not found")
                                            .encode());
                                }
                            })
                            .onFailure(err -> {
                                ctx.response().setStatusCode(500)
                                    .end(new JsonObject()
                                        .put("error", "Failed to check stock")
                                        .encode());
                            });
                    }
                })
                .onFailure(err -> {
                    System.err.println("❌ Manual stock update failed: " + err.getMessage());
                    err.printStackTrace();
                    ctx.response().setStatusCode(500)
                        .end(new JsonObject()
                            .put("error", "Failed to update stock")
                            .put("details", err.getMessage())
                            .encode());
                });

        } catch (Exception e) {
            System.err.println("❌ Exception in manualStockUpdate: " + e.getMessage());
            e.printStackTrace();
            ctx.response().setStatusCode(400)
                .end(new JsonObject()
                    .put("error", "Invalid request payload")
                    .put("details", e.getMessage())
                    .encode());
        }
    }

    // ---------- Helper Method ----------
    private JsonArray jsonArrayFromRows(RowSet<Row> rows) {
        JsonArray array = new JsonArray();
        for (Row row : rows) {
            array.add(row.toJson());
        }
        return array;
    }
}
