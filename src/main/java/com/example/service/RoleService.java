package com.example.service;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

public class RoleService {

    public Future<List<JsonObject>> getRoles() {
        Promise<List<JsonObject>> promise = Promise.promise();

        // Create role list
        List<JsonObject> roles = new ArrayList<>();
        roles.add(new JsonObject().put("id", 1).put("name", "SALESPERSON"));
        roles.add(new JsonObject().put("id", 2).put("name", "ACCOUNTANT"));

        // Return roles asynchronously (simulate DB fetch)
        promise.complete(roles);

        return promise.future();
    }
}
