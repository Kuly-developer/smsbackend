package com.example.controller;

import com.example.middleware.JwtAuthHandler;
import com.example.service.ProductService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;

public class ProductController {
    private final Router router;
    private final ProductService productService;

    public ProductController(Vertx vertx, ProductService productService) {
        this.router = Router.router(vertx);
        this.productService = productService;

        router.route().handler(io.vertx.ext.web.handler.BodyHandler.create());

        // ✅ CORS handler
        router.route().handler(ctx -> {
            ctx.response()
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
            if (ctx.request().method().name().equals("OPTIONS")) {
                ctx.response().setStatusCode(204).end();
            } else {
                ctx.next();
            }
        });

        // ============ PUBLIC ROUTES ============
        router.get("/public").handler(productService::getAllPublic);
        router.get("/all").handler(productService::getAllPublic);
        router.delete("/public/:id").handler(productService::deletePublic);

        // Get products by salesperson ID (public)
        router.get("/salesperson/:salespersonId").handler(this::getProductsBySalespersonId);

        // Debug routes (public)
        router.get("/stock/:id").handler(productService::getProductStock);
        router.post("/manual-stock-update").handler(productService::manualStockUpdate);
        router.post("/test-stock-update").handler(ctx -> testStockUpdate(ctx));

        // ============ PROTECTED ROUTES (JWT required) ============
        router.route().handler(JwtAuthHandler::handle);

        // Authenticated product routes
        router.get("/").handler(productService::getAll);
        router.post("/").handler(productService::create);
        router.get("/:id").handler(productService::getById);
        router.put("/:id").handler(productService::update);
        router.delete("/:id").handler(productService::delete);
        router.post("/reduce-stock").handler(productService::reduceStock);
    }

    /**
     * Get products by salesperson ID (public endpoint)
     */
    private void getProductsBySalespersonId(RoutingContext ctx) {
        try {
            int salespersonId = Integer.parseInt(ctx.pathParam("salespersonId"));

            String query = "SELECT p.*, c.name as category_name FROM products p " +
                           "LEFT JOIN categories c ON p.category_id = c.id " +
                           "WHERE p.salesperson_id = $1";
            productService.getClient().preparedQuery(query)
                .execute(Tuple.of(salespersonId))
                .onSuccess(rows -> {
                    JsonArray products = new JsonArray();
                    for (var row : rows) {
                        products.add(row.toJson());
                    }
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(products.encode());
                })
                .onFailure(err -> {
                    System.err.println("❌ Error fetching products for salesperson " + salespersonId + ": " + err.getMessage());
                    err.printStackTrace();
                    ctx.response()
                        .setStatusCode(500)
                        .end(new JsonObject()
                            .put("error", "Failed to fetch products")
                            .put("details", err.getMessage())
                            .encode());
                });
        } catch (NumberFormatException e) {
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject().put("error", "Invalid salesperson ID").encode());
        }
    }

    /**
     * Test stock update endpoint (debug only)
     */
    private void testStockUpdate(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            Integer productId = body.getInteger("product_id");
            Integer quantity = body.getInteger("quantity");

            if (productId == null || quantity == null) {
                ctx.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "product_id and quantity are required").encode());
                return;
            }

            System.out.println("🧪 TEST: Manual stock update requested");
            System.out.println("📦 Product ID: " + productId + ", Quantity: " + quantity);

            // First, get current stock
            String selectQuery = "SELECT id, name, stock FROM products WHERE id = $1";
            productService.getClient().preparedQuery(selectQuery)
                .execute(Tuple.of(productId))
                .compose(rows -> {
                    if (!rows.iterator().hasNext()) {
                        return Future.failedFuture("Product not found");
                    }
                    io.vertx.sqlclient.Row row = rows.iterator().next();
                    Integer currentStock = row.getInteger("stock");
                    String productName = row.getString("name");

                    System.out.println("📊 Current stock for '" + productName + "': " + currentStock);

                    // Update stock
                    String updateQuery = "UPDATE products SET stock = stock - $1 WHERE id = $2";
                    return productService.getClient().preparedQuery(updateQuery)
                        .execute(Tuple.of(quantity, productId))
                        .map(updateRows -> {
                            System.out.println("✅ Stock update successful. Rows affected: " + updateRows.rowCount());
                            return new JsonObject()
                                .put("product_id", productId)
                                .put("product_name", productName)
                                .put("old_stock", currentStock)
                                .put("new_stock", currentStock - quantity)
                                .put("quantity_reduced", quantity);
                        });
                })
                .onSuccess(result -> {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(result.encode());
                })
                .onFailure(err -> {
                    System.err.println("❌ Stock update failed: " + err.getMessage());
                    ctx.response()
                        .setStatusCode(400)
                        .end(new JsonObject().put("error", err.getMessage()).encode());
                });
        } catch (Exception e) {
            System.err.println("❌ Exception in testStockUpdate: " + e.getMessage());
            e.printStackTrace();
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject().put("error", e.getMessage()).encode());
        }
    }

    public Router getRouter() {
        return router;
    }
}
