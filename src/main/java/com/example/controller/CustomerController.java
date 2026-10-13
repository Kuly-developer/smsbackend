package com.example.controller;

import com.example.model.Customer;
import com.example.service.CustomerService;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class CustomerController {

    private final CustomerService customerService;
    private final Router router;

    public CustomerController(Vertx vertx, CustomerService customerService) {
        this.customerService = customerService;
        this.router = Router.router(vertx);

        // Parse JSON bodies
        router.route().handler(BodyHandler.create());

        // Routes (mounted at /api/customers)
        router.post("/").handler(this::handleRegister);
        router.get("/").handler(this::handleGetAll);
        router.put("/:id").handler(this::handleUpdate);
        router.delete("/:id").handler(this::handleDelete);
        router.post("/login").handler(this::handleLogin);
    }

    public Router getRouter() {
        return router;
    }

    // Handle customer registration safely
    private void handleRegister(RoutingContext ctx) {
        try {
            var json = ctx.getBodyAsJson();
            Customer c = json.mapTo(Customer.class);
            String plainPassword = json.getString("password");

            // Default portalAccess to false if not provided
            if (c.getPortalAccess() == null) {
                c.setPortalAccess(false);
            }

            // Require password if portalAccess is true
            if (c.getPortalAccess() && (plainPassword == null || plainPassword.isEmpty())) {
                ctx.response()
                   .setStatusCode(400)
                   .putHeader("Content-Type", "application/json")
                   .end(new JsonObject().put("error", "Password is required for portal access").encode());
                return;
            }

            customerService.registerCustomer(c, plainPassword)
                    .onSuccess(customer -> {
                        ctx.response()
                           .setStatusCode(201)
                           .putHeader("Content-Type", "application/json")
                           .end(JsonObject.mapFrom(customer).encode());
                    })
                    .onFailure(err -> {
                        err.printStackTrace();
                        ctx.response()
                           .setStatusCode(500)
                           .putHeader("Content-Type", "application/json")
                           .end(new JsonObject().put("error", err.getMessage()).encode());
                    });
        } catch (Exception e) {
            e.printStackTrace();
            ctx.response()
               .setStatusCode(400)
               .putHeader("Content-Type", "application/json")
               .end(new JsonObject().put("error", "Invalid request data").encode());
        }
    }

    private void handleGetAll(RoutingContext ctx) {
        customerService.getAllCustomers()
                .onSuccess(customers -> {
                    ctx.response()
                       .putHeader("Content-Type", "application/json")
                       .end(customers.encode());
                })
                .onFailure(err -> {
                    err.printStackTrace();
                    ctx.response()
                       .setStatusCode(500)
                       .putHeader("Content-Type", "application/json")
                       .end(new JsonObject().put("error", err.getMessage()).encode());
                });
    }

    private void handleUpdate(RoutingContext ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));
            var json = ctx.getBodyAsJson();
            Customer c = json.mapTo(Customer.class);
            c.setId(id);

            customerService.updateCustomer(c)
                    .onSuccess(customer -> {
                        ctx.response()
                           .setStatusCode(200)
                           .putHeader("Content-Type", "application/json")
                           .end(JsonObject.mapFrom(customer).encode());
                    })
                    .onFailure(err -> {
                        err.printStackTrace();
                        ctx.response()
                           .setStatusCode(500)
                           .putHeader("Content-Type", "application/json")
                           .end(new JsonObject().put("error", err.getMessage()).encode());
                    });
        } catch (NumberFormatException e) {
            ctx.response()
               .setStatusCode(400)
               .putHeader("Content-Type", "application/json")
               .end(new JsonObject().put("error", "Invalid customer ID").encode());
        } catch (Exception e) {
            ctx.response()
               .setStatusCode(400)
               .putHeader("Content-Type", "application/json")
               .end(new JsonObject().put("error", "Invalid request data").encode());
        }
    }

    private void handleDelete(RoutingContext ctx) {
        try {
            int id = Integer.parseInt(ctx.pathParam("id"));

            customerService.deleteCustomer(id)
                    .onSuccess(v -> {
                        ctx.response()
                           .setStatusCode(200)
                           .putHeader("Content-Type", "application/json")
                           .end(new JsonObject().put("message", "Customer deleted successfully").encode());
                    })
                    .onFailure(err -> {
                        err.printStackTrace();
                        ctx.response()
                           .setStatusCode(500)
                           .putHeader("Content-Type", "application/json")
                           .end(new JsonObject().put("error", err.getMessage()).encode());
                    });
        } catch (NumberFormatException e) {
            ctx.response()
               .setStatusCode(400)
               .putHeader("Content-Type", "application/json")
               .end(new JsonObject().put("error", "Invalid customer ID").encode());
        }
    }

    private void handleLogin(RoutingContext ctx) {
        try {
            var json = ctx.getBodyAsJson();
            String email = json.getString("email");
            String password = json.getString("password");

            if (email == null || password == null) {
                ctx.response()
                   .setStatusCode(400)
                   .putHeader("Content-Type", "application/json")
                   .end(new JsonObject().put("error", "Email and password are required").encode());
                return;
            }

            customerService.login(email, password)
                    .onSuccess(customer -> {
                        ctx.response()
                           .putHeader("Content-Type", "application/json")
                           .end(customer.encode());
                    })
                    .onFailure(err -> {
                        ctx.response()
                           .setStatusCode(401)
                           .putHeader("Content-Type", "application/json")
                           .end(new JsonObject().put("error", err.getMessage()).encode());
                    });
        } catch (Exception e) {
            ctx.response()
               .setStatusCode(400)
               .putHeader("Content-Type", "application/json")
               .end(new JsonObject().put("error", "Invalid request data").encode());
        }
    }
}
