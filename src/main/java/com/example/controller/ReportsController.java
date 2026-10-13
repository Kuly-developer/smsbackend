package com.example.controller;

import com.example.MainVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class ReportsController {

    public void registerRoutes(Router router) {
        router.get("/api/reports/dashboard-stats").handler(this::getDashboardStats);
        router.get("/api/reports/sales-overview").handler(this::getSalesOverview);
        router.get("/api/reports/payment-analytics").handler(this::getPaymentAnalytics);
        router.get("/api/reports/customer-analytics").handler(this::getCustomerAnalytics);
        router.get("/api/reports/sales-trends").handler(this::getSalesTrends);
        router.get("/api/reports/product-performance").handler(this::getProductPerformance);
    }

    // 1️⃣ Dashboard Statistics
    private void getDashboardStats(RoutingContext ctx) {
        String sql = "SELECT " +
                "(SELECT COALESCE(SUM(amount_paid), 0) FROM orders WHERE paid = true) as total_revenue, " +
                "(SELECT COUNT(*) FROM orders WHERE paid = true) as total_orders, " +
                "(SELECT COALESCE(AVG(amount_paid), 0) FROM orders WHERE paid = true) as average_payment, " +
                "(SELECT COALESCE(MAX(amount_paid), 0) FROM orders WHERE paid = true) as highest_payment, " +
                "(SELECT COALESCE(MIN(amount_paid), 0) FROM orders WHERE paid = true AND amount_paid > 0) as lowest_payment, " +
                "(SELECT COUNT(*) FROM customers) as total_customers, " +
                "(SELECT COALESCE(SUM(amount_paid), 0) FROM orders WHERE paid = true AND created_at >= date_trunc('month', CURRENT_DATE)) as monthly_revenue, " +
                "(SELECT COALESCE(SUM(amount_paid), 0) FROM orders WHERE paid = true AND created_at::date = CURRENT_DATE) as today_revenue";

        MainVerticle.client.preparedQuery(sql)
            .execute()
            .onSuccess(rows -> {
                if (rows.iterator().hasNext()) {
                    Row row = rows.iterator().next();
                    JsonObject stats = new JsonObject()
                        .put("totalRevenue", row.getDouble("total_revenue"))
                        .put("totalOrders", row.getLong("total_orders"))
                        .put("averagePayment", row.getDouble("average_payment"))
                        .put("highestPayment", row.getDouble("highest_payment"))
                        .put("lowestPayment", row.getDouble("lowest_payment"))
                        .put("totalCustomers", row.getLong("total_customers"))
                        .put("monthlyRevenue", row.getDouble("monthly_revenue"))
                        .put("todayRevenue", row.getDouble("today_revenue"));

                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(stats.encode());
                } else {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().encode());
                }
            })
            .onFailure(err -> handleError(ctx, err));
    }

    // 2️⃣ Payment Analytics
    private void getPaymentAnalytics(RoutingContext ctx) {
        String sql = "SELECT " +
                "COUNT(*) as total_payments, " +
                "COALESCE(SUM(amount_paid), 0) as total_amount, " +
                "COALESCE(AVG(amount_paid), 0) as average_amount, " +
                "COALESCE(MAX(amount_paid), 0) as max_amount, " +
                "COALESCE(MIN(amount_paid), 0) as min_amount, " +
                "SUM(CASE WHEN amount_paid < 50 THEN 1 ELSE 0 END) as under_50, " +
                "SUM(CASE WHEN amount_paid BETWEEN 50 AND 100 THEN 1 ELSE 0 END) as between_50_100, " +
                "SUM(CASE WHEN amount_paid BETWEEN 100 AND 200 THEN 1 ELSE 0 END) as between_100_200, " +
                "SUM(CASE WHEN amount_paid > 200 THEN 1 ELSE 0 END) as over_200, " +
                "(COUNT(*) * 100.0 / NULLIF((SELECT COUNT(*) FROM orders), 0)) as success_rate " +
                "FROM orders WHERE paid = true AND amount_paid > 0";

        MainVerticle.client.preparedQuery(sql)
            .execute()
            .onSuccess(rows -> {
                if (rows.iterator().hasNext()) {
                    Row row = rows.iterator().next();
                    JsonObject analytics = new JsonObject()
                        .put("totalPayments", row.getLong("total_payments"))
                        .put("totalAmount", row.getDouble("total_amount"))
                        .put("averageAmount", row.getDouble("average_amount"))
                        .put("maxAmount", row.getDouble("max_amount"))
                        .put("minAmount", row.getDouble("min_amount"))
                        .put("paymentRanges", new JsonObject()
                            .put("under50", row.getLong("under_50"))
                            .put("between50_100", row.getLong("between_50_100"))
                            .put("between100_200", row.getLong("between_100_200"))
                            .put("over200", row.getLong("over_200")))
                        .put("successRate", row.getDouble("success_rate"));

                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(analytics.encode());
                }
            })
            .onFailure(err -> handleError(ctx, err));
    }

    // 3️⃣ Sales Overview
    private void getSalesOverview(RoutingContext ctx) {
        String period = ctx.request().getParam("period") != null ? ctx.request().getParam("period") : "month";

        String sql = "SELECT " +
                "DATE_TRUNC(?, created_at) as period, " +
                "COUNT(*) as order_count, " +
                "COALESCE(SUM(amount_paid), 0) as revenue, " +
                "AVG(amount_paid) as avg_order_value, " +
                "COUNT(DISTINCT customer_id) as unique_customers " +
                "FROM orders WHERE paid = true " +
                "GROUP BY period " +
                "ORDER BY period DESC " +
                "LIMIT 12";

        MainVerticle.client.preparedQuery(sql)
            .execute(Tuple.of(period))
            .onSuccess(rows -> {
                JsonArray trends = new JsonArray();
                for (Row row : rows) {
                    JsonObject trend = new JsonObject()
                        .put("period", row.getLocalDateTime("period").toString())
                        .put("orderCount", row.getLong("order_count"))
                        .put("revenue", row.getDouble("revenue"))
                        .put("avgOrderValue", row.getDouble("avg_order_value"))
                        .put("uniqueCustomers", row.getLong("unique_customers"));
                    trends.add(trend);
                }
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(trends.encode());
            })
            .onFailure(err -> handleError(ctx, err));
    }

    // 4️⃣ Customer Analytics
    private void getCustomerAnalytics(RoutingContext ctx) {
        String sql = "SELECT " +
                "COUNT(DISTINCT o.customer_id) as paying_customers, " +
                "AVG(order_count) as avg_orders_per_customer, " +
                "MAX(order_count) as max_orders, " +
                "AVG(total_spent) as avg_customer_value, " +
                "MAX(total_spent) as best_customer_value " +
                "FROM ( " +
                "SELECT customer_id, COUNT(*) as order_count, SUM(amount_paid) as total_spent " +
                "FROM orders WHERE paid = true GROUP BY customer_id" +
                ") o";

        MainVerticle.client.preparedQuery(sql)
            .execute()
            .onSuccess(rows -> {
                if (rows.iterator().hasNext()) {
                    Row row = rows.iterator().next();
                    JsonObject customerStats = new JsonObject()
                        .put("payingCustomers", row.getLong("paying_customers"))
                        .put("avgOrdersPerCustomer", row.getDouble("avg_orders_per_customer"))
                        .put("maxOrders", row.getLong("max_orders"))
                        .put("avgCustomerValue", row.getDouble("avg_customer_value"))
                        .put("bestCustomerValue", row.getDouble("best_customer_value"));

                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(customerStats.encode());
                }
            })
            .onFailure(err -> handleError(ctx, err));
    }

    // 5️⃣ Sales Trends - FIXED VERSION
    private void getSalesTrends(RoutingContext ctx) {
        String daysParam = ctx.request().getParam("days");
        int days = 30; // default
        try {
            if (daysParam != null) {
                days = Integer.parseInt(daysParam);
            }
        } catch (NumberFormatException e) {
            days = 30;
        }

        // Use string concatenation for the interval since prepared statements don't work well with dynamic intervals
        String sql = "SELECT " +
                "DATE(created_at) as date, " +
                "COUNT(*) as orders, " +
                "COALESCE(SUM(amount_paid), 0) as revenue, " +
                "AVG(amount_paid) as avg_order_value " +
                "FROM orders WHERE paid = true " +
                "AND created_at >= CURRENT_DATE - INTERVAL '" + days + " days' " +
                "GROUP BY DATE(created_at) " +
                "ORDER BY date";

        MainVerticle.client.preparedQuery(sql)
            .execute()
            .onSuccess(rows -> {
                JsonArray trends = new JsonArray();
                for (Row row : rows) {
                    JsonObject daily = new JsonObject()
                        .put("date", row.getLocalDate("date").toString())
                        .put("orders", row.getLong("orders"))
                        .put("revenue", row.getDouble("revenue"))
                        .put("avgOrderValue", row.getDouble("avg_order_value"));
                    trends.add(daily);
                }
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(trends.encode());
            })
            .onFailure(err -> {
                System.err.println("Error in sales trends: " + err.getMessage());
                handleError(ctx, err);
            });
    }

    // 6️⃣ Product Performance
    private void getProductPerformance(RoutingContext ctx) {
        String sql = "SELECT " +
                "p.name as product_name, " +
                "COUNT(oi.id) as units_sold, " +
                "SUM(oi.quantity) as total_quantity, " +
                "SUM(oi.price * oi.quantity) as total_revenue, " +
                "AVG(oi.price) as avg_price " +
                "FROM order_items oi " +
                "JOIN products p ON p.id = oi.product_id " +
                "JOIN orders o ON o.id = oi.order_id " +
                "WHERE o.paid = true " +
                "GROUP BY p.id, p.name " +
                "ORDER BY total_revenue DESC " +
                "LIMIT 10";

        MainVerticle.client.preparedQuery(sql)
            .execute()
            .onSuccess(rows -> {
                JsonArray products = new JsonArray();
                for (Row row : rows) {
                    JsonObject product = new JsonObject()
                        .put("productName", row.getString("product_name"))
                        .put("unitsSold", row.getLong("units_sold"))
                        .put("totalQuantity", row.getLong("total_quantity"))
                        .put("totalRevenue", row.getDouble("total_revenue"))
                        .put("avgPrice", row.getDouble("avg_price"));
                    products.add(product);
                }
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(products.encode());
            })
            .onFailure(err -> handleError(ctx, err));
    }

    private void handleError(RoutingContext ctx, Throwable err) {
        System.err.println("Report error: " + err.getMessage());
        ctx.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("error", "Failed to generate report")
                .put("details", err.getMessage())
                .encode());
    }
}
