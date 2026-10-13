package com.example.controller;

import com.example.service.RoleService;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RoleController {

  private final RoleService roleService;

  public RoleController(Vertx vertx, Router router) {
    roleService = new RoleService();

    router.get("/api/roles").handler(this::handleGetRoles);
  }

  private void handleGetRoles(RoutingContext context) {
    roleService.getRoles().onComplete(ar -> {
      if (ar.succeeded()) {
        JsonArray jsonRoles = new JsonArray(ar.result());
        context.response()
          .putHeader("Content-Type", "application/json")
          .setStatusCode(200)
          .end(jsonRoles.encode());
      } else {
        context.response()
          .setStatusCode(500)
          .end("Failed to load roles");
      }
    });
  }
}
