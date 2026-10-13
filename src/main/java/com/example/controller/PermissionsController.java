package com.example.controller;

import com.example.service.PermissionsService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class PermissionsController {

    private final PermissionsService permissionsService;

    public PermissionsController(PermissionsService permissionsService) {
        this.permissionsService = permissionsService;
    }

    public void registerRoutes(Router router) {
        router.put("/api/permissions/:customerId").handler(this::updatePermissions);
        router.get("/api/permissions/:customerId").handler(this::getPermissions);
        router.get("/api/permissions").handler(this::getAllPermissions);
    }

    private void updatePermissions(RoutingContext ctx) {
        int customerId;
        try {
            customerId = Integer.parseInt(ctx.pathParam("customerId"));
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end("Invalid customer ID");
            return;
        }

        JsonObject body = ctx.getBodyAsJson();
        boolean accountantAccess = body.getBoolean("accountantAccess", false);
        boolean salespersonAccess = body.getBoolean("salespersonAccess", false);
        boolean customerAccess = body.getBoolean("customerAccess", false);

        permissionsService.updatePortalAccess(customerId, accountantAccess, salespersonAccess, customerAccess)
            .onSuccess(success -> {
                if (success) {
                    JsonObject response = new JsonObject()
                        .put("message", "Permissions updated successfully")
                        .put("customerId", customerId)
                        .put("accountantAccess", accountantAccess)
                        .put("salespersonAccess", salespersonAccess)
                        .put("customerAccess", customerAccess);

                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
                } else {
                    ctx.response().setStatusCode(404).end("Customer not found");
                }
            })
            .onFailure(err -> {
                ctx.response().setStatusCode(500).end("Failed to update permissions: " + err.getMessage());
            });
 }

    private void getPermissions(RoutingContext ctx) {
        int customerId;
        try {
            customerId = Integer.parseInt(ctx.pathParam("customerId"));
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end("Invalid customer ID");
            return;
        }

        permissionsService.getCustomerPermissions(customerId)
            .onSuccess(permissions -> {
                if (permissions != null) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(permissions.encode());
                } else {
                    ctx.response().setStatusCode(404).end("Customer not found");
                }
            })
            .onFailure(err -> {
                ctx.response().setStatusCode(500).end("Failed to fetch permissions: " + err.getMessage());
            });
    }

    private void getAllPermissions(RoutingContext ctx) {
        permissionsService.getAllCustomersWithPermissions()
            .onSuccess(customers -> {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(customers.encode());
            })
            .onFailure(err -> {
                ctx.response().setStatusCode(500).end("Failed to fetch permissions: " + err.getMessage());
            });
    }
}
