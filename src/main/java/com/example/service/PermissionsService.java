package com.example.service;

import com.example.MainVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class PermissionsService {

    public Future<Boolean> updatePortalAccess(int customerId, boolean accountantAccess,
                                            boolean salespersonAccess, boolean customerAccess) {
        String sql = "UPDATE customers SET " +
                    "accountant_portal_access = $1, " +
                    "salesperson_portal_access = $2, " +
                    "customer_portal_access = $3 " +
                    "WHERE id = $4";

        return MainVerticle.client.preparedQuery(sql)
            .execute(Tuple.of(accountantAccess, salespersonAccess, customerAccess, customerId))
            .map(rows -> rows.rowCount() > 0);
    }

    public Future<JsonObject> getCustomerPermissions(int customerId) {
        String sql = "SELECT id, name, email, accountant_portal_access, " +
                    "salesperson_portal_access, customer_portal_access " +
                    "FROM customers WHERE id = $1";

        return MainVerticle.client.preparedQuery(sql)
            .execute(Tuple.of(customerId))
            .map(rows -> {
                if (rows.size() > 0) {
                    Row row = rows.iterator().next();
                    return new JsonObject()
                        .put("id", row.getInteger("id"))
                        .put("name", row.getString("name"))
                        .put("email", row.getString("email"))
                        .put("accountantAccess", row.getBoolean("accountant_portal_access"))
                        .put("salespersonAccess", row.getBoolean("salesperson_portal_access"))
                        .put("customerAccess", row.getBoolean("customer_portal_access"));
                }
                return null;
            });
    }

    public Future<JsonArray> getAllCustomersWithPermissions() {
        String sql = "SELECT id, name, email, accountant_portal_access, " +
                    "salesperson_portal_access, customer_portal_access " +
                    "FROM customers ORDER BY name";

        return MainVerticle.client.preparedQuery(sql)
            .execute()
            .map(rows -> {
                JsonArray customers = new JsonArray();
                for (Row row : rows) {
                    JsonObject customer = new JsonObject()
                        .put("id", row.getInteger("id"))
                        .put("name", row.getString("name"))
                        .put("email", row.getString("email"))
                        .put("accountantAccess", row.getBoolean("accountant_portal_access"))
                        .put("salespersonAccess", row.getBoolean("salesperson_portal_access"))
                        .put("customerAccess", row.getBoolean("customer_portal_access"));
                    customers.add(customer);
                }
                return customers;
            });
    }
}

