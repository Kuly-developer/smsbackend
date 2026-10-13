package com.example.controller;

import com.example.model.Order;
import com.example.model.OrderItem;
import com.example.service.OrderService;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class OrderController {
    private final OrderService orderService;

    public OrderController(Vertx vertx, Router router, OrderService orderService) {
        this.orderService = orderService;

        // Apply BodyHandler to all routes
        router.route().handler(BodyHandler.create());

        // Order routes
        router.get("/api/orders").handler(this::getAllOrders);
        router.get("/api/orders/:id").handler(this::getOrderById);
        router.get("/api/orders/:id/items").handler(this::getOrderItems);
        router.post("/api/orders").handler(this::createOrder);
        router.put("/api/orders/:id/status").handler(this::updateOrderStatus);
        router.delete("/api/orders/:id").handler(this::deleteOrder);
        router.get("/api/orders/customer/:customerId").handler(this::getOrdersByCustomer);
        router.get("/api/orders/status/:status").handler(this::getOrdersByStatus);

        // Invoice routes
        router.post("/api/invoices").handler(this::createInvoice);
    }

    private void getAllOrders(RoutingContext ctx) {
        System.out.println("📦 Fetching all orders...");
        orderService.getAllOrders()
            .onSuccess(orders -> ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
                .end(Json.encodePrettily(orders)))
            .onFailure(err -> ctx.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Failed to fetch orders")
                    .put("details", err.getMessage())
                    .encode()));
    }

    private void getOrderById(RoutingContext ctx) {
        try {
            Integer orderId = Integer.parseInt(ctx.request().getParam("id"));
            orderService.getOrderById(orderId)
                .onSuccess(order -> ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(Json.encodePrettily(order)))
                .onFailure(err -> {
                    if (err.getMessage().contains("not found")) {
                        ctx.response().setStatusCode(404)
                            .end(new JsonObject().put("error", "Order not found").encode());
                    } else {
                        ctx.response().setStatusCode(500)
                            .end(new JsonObject()
                                .put("error", "Failed to fetch order")
                                .put("details", err.getMessage())
                                .encode());
                    }
                });
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400)
                .end(new JsonObject().put("error", "Invalid order ID").encode());
        }
    }

    private void getOrderItems(RoutingContext ctx) {
        try {
            Integer orderId = Integer.parseInt(ctx.request().getParam("id"));
            orderService.getOrderItems(orderId)
                .onSuccess(items -> ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(Json.encodePrettily(items)))
                .onFailure(err -> ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error", "Failed to fetch order items")
                        .put("details", err.getMessage())
                        .encode()));
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400)
                .end(new JsonObject().put("error", "Invalid order ID").encode());
        }
    }

    private void createOrder(RoutingContext ctx) {
        try {
            JsonObject body = ctx.getBodyAsJson();
            System.out.println("🛒 CREATE ORDER REQUEST RECEIVED");
            System.out.println("📦 Request body: " + body.encodePrettily());

            Order order = body.mapTo(Order.class);

            if (order.getItems() == null || order.getItems().isEmpty()) {
                ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Order items are required").encode());
                return;
            }

            for (OrderItem item : order.getItems()) {
                System.out.println("   Product ID: " + item.getProductId() + ", Qty: " + item.getQuantity());
            }

            orderService.createOrder(order)
                .onSuccess(orderId -> ctx.response()
                    .setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("message", "Order created successfully")
                        .put("orderId", orderId)
                        .encode()))
                .onFailure(err -> ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error", err.getMessage())
                        .encode()));
        } catch (Exception e) {
            ctx.response().setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Invalid order payload")
                    .put("details", e.getMessage())
                    .encode());
        }
    }

    private void updateOrderStatus(RoutingContext ctx) {
        try {
            Integer orderId = Integer.parseInt(ctx.request().getParam("id"));
            String status = ctx.getBodyAsJson().getString("status");

            if (status == null || status.trim().isEmpty()) {
                ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Status is required").encode());
                return;
            }

            orderService.updateOrderStatus(orderId, status)
                .onSuccess(v -> ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("message", "Order status updated successfully")
                        .put("orderId", orderId)
                        .put("newStatus", status)
                        .encode()))
                .onFailure(err -> ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error", "Failed to update order status")
                        .put("details", err.getMessage())
                        .encode()));
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400)
                .end(new JsonObject().put("error", "Invalid order ID").encode());
        }
    }

    private void deleteOrder(RoutingContext ctx) {
        try {
            Integer orderId = Integer.parseInt(ctx.request().getParam("id"));
            orderService.deleteOrder(orderId)
                .onSuccess(v -> ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("message", "Order deleted successfully")
                        .put("orderId", orderId)
                        .encode()))

                .onFailure(err -> ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error", "Failed to delete order")
                        .put("details", err.getMessage())
                        .encode()));
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400)
                .end(new JsonObject().put("error", "Invalid order ID").encode());
        }
    }

    private void getOrdersByCustomer(RoutingContext ctx) {
        try {
            Integer customerId = Integer.parseInt(ctx.request().getParam("customerId"));
            orderService.getOrdersByCustomerId(customerId)
                .onSuccess(orders -> ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(Json.encodePrettily(orders)))
                .onFailure(err -> ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error", "Failed to fetch customer orders")
                        .put("details", err.getMessage())
                        .encode()));
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400)
                .end(new JsonObject().put("error", "Invalid customer ID").encode());
        }
    }

    private void getOrdersByStatus(RoutingContext ctx) {
        try {
            String status = ctx.request().getParam("status");
            if (status == null || status.trim().isEmpty()) {
                ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Status parameter is required").encode());
                return;
            }

            orderService.getOrdersByStatus(status)
                .onSuccess(orders -> ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(Json.encodePrettily(orders)))
                .onFailure(err -> ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error", "Failed to fetch orders by status")
                        .put("details", err.getMessage())
                        .encode()));
        } catch (Exception e) {
            ctx.response().setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Invalid status parameter")
                    .put("details", e.getMessage())
                    .encode());
        }
    }

    private void createInvoice(RoutingContext ctx) {
        try {
            JsonObject body = ctx.getBodyAsJson();
            Integer orderId = body.getInteger("orderId");
            String invoiceNumber = body.getString("invoiceNumber");
            Double total = body.getDouble("total");

            if (orderId == null || invoiceNumber == null || total == null) {
                ctx.response().setStatusCode(400)
                    .end(new JsonObject()
                        .put("error", "orderId, invoiceNumber, and total are required")
                        .encode());
                return;
            }

            orderService.createInvoice(orderId, invoiceNumber, total)
                .onSuccess(invoiceId -> ctx.response()
                    .setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("message", "Invoice created successfully")
                        .put("invoiceId", invoiceId)
                        .encode()))
                .onFailure(err -> ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error", "Failed to create invoice")
                        .put("details", err.getMessage())
                        .encode()));
        } catch (Exception e) {
            ctx.response().setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("error", "Invalid invoice payload")
                    .put("details", e.getMessage())
                    .encode());
        }
    }
}
