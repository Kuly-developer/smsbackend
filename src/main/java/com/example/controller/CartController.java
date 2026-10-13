package com.example.controller;

import com.example.model.Cart;
import com.example.service.CartService;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class CartController {

    private final CartService cartService;

    public CartController(Vertx vertx, Router router, CartService cartService) {
        this.cartService = cartService;

        router.route().handler(BodyHandler.create());

        router.post("/api/cart").handler(this::addToCart);
        router.get("/api/cart/:customerId").handler(this::getCart);
        router.put("/api/cart/:id").handler(this::updateCart);
        router.delete("/api/cart/:id").handler(this::deleteCart);

        // Clear entire cart for a customer
        router.delete("/api/cart/customer/:customerId").handler(this::clearCustomerCart);
    }

    private void addToCart(RoutingContext ctx) {
        try {
            JsonObject json = ctx.getBodyAsJson();
            Cart cart = json.mapTo(Cart.class);

            if (cart.getCustomerId() == null || cart.getProductId() == null || cart.getQuantity() == null) {
                ctx.response().setStatusCode(400).end("Missing fields: customerId, productId, quantity");
                return;
            }

            cartService.addCartItem(cart)
                .onSuccess(v -> ctx.response().setStatusCode(201)
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("message", "Added to cart").encode()))
                .onFailure(err -> {
                    err.printStackTrace();
                    ctx.response().setStatusCode(500)
                            .end(new JsonObject().put("error", "Failed to add item").put("details", err.getMessage()).encode());
                });
        } catch (Exception e) {
            e.printStackTrace();
            ctx.response().setStatusCode(400)
                    .end(new JsonObject().put("error", "Invalid cart data").put("details", e.getMessage()).encode());
        }
    }

    private void getCart(RoutingContext ctx) {
        try {
            Integer customerId = Integer.valueOf(ctx.pathParam("customerId"));
            cartService.getCartByCustomerId(customerId)
                .onSuccess(list -> ctx.response().putHeader("Content-Type", "application/json")
                        .end(io.vertx.core.json.Json.encodePrettily(list)))
                .onFailure(err -> {
                    err.printStackTrace();
                    ctx.response().setStatusCode(500)
                            .end(new JsonObject().put("error", "Failed to fetch cart").put("details", err.getMessage()).encode());
                });
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end(new JsonObject().put("error", "Invalid customer ID").encode());
        }
    }

    private void updateCart(RoutingContext ctx) {
        try {
            Integer id = Integer.valueOf(ctx.pathParam("id"));
            Integer quantity = ctx.getBodyAsJson().getInteger("quantity");

            if (quantity == null) {
                ctx.response().setStatusCode(400).end(new JsonObject().put("error", "Quantity required").encode());
                return;
            }

            cartService.updateCartItem(id, quantity)
                .onSuccess(v -> ctx.response().setStatusCode(200).end(new JsonObject().put("message", "Cart updated").encode()))
                .onFailure(err -> {
                    err.printStackTrace();
                    ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to update").put("details", err.getMessage()).encode());
                });

        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end(new JsonObject().put("error", "Invalid ID").encode());
        }
    }

    private void deleteCart(RoutingContext ctx) {
        try {
            Integer id = Integer.valueOf(ctx.pathParam("id"));
            cartService.deleteCartItem(id)
                .onSuccess(v -> ctx.response().setStatusCode(200).end(new JsonObject().put("message", "Deleted").encode()))
                .onFailure(err -> {
                    err.printStackTrace();
                    ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to delete").put("details", err.getMessage()).encode());
                });
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end(new JsonObject().put("error", "Invalid ID").encode());
        }
    }

    private void clearCustomerCart(RoutingContext ctx) {
        try {
            Integer customerId = Integer.valueOf(ctx.pathParam("customerId"));
            cartService.clearCartByCustomer(customerId)
                .onSuccess(v -> ctx.response().putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("success", true).encode()))
                .onFailure(err -> {
                    err.printStackTrace();
                    ctx.response().setStatusCode(500)
                        .end(new JsonObject().put("error", "Failed to clear cart").put("details", err.getMessage()).encode());
                });
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end(new JsonObject().put("error", "Invalid customer ID").encode());
        }
    }
}
