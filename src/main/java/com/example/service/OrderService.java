package com.example.service;

import java.util.ArrayList;
import java.util.List;

import com.example.model.Order;
import com.example.model.OrderItem;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;

public class OrderService {

    private final PgPool client;

    public OrderService(PgPool client) {
        this.client = client;
    }

    // -------------------------------
    // 1️⃣ Create Order
    // -------------------------------
    public Future<Integer> createOrder(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return Future.failedFuture("Order items are required!");
        }

        System.out.println("🛒 Creating order for customer ID: " + order.getCustomerId());
        System.out.println("📦 Order items count: " + order.getItems().size());

        return client.withTransaction(conn -> {
            System.out.println("🔐 Transaction started");
            return insertOrderWithItems(conn, order)
                .onSuccess(orderId -> System.out.println("✅ Transaction committed for order ID: " + orderId))
                .onFailure(err -> System.err.println("❌ Transaction rolled back: " + err.getMessage()));
        });
    }

    private Future<Integer> insertOrderWithItems(SqlConnection conn, Order order) {
        String insertOrderSql = "INSERT INTO orders (customer_id, total, status) VALUES ($1, $2, $3) RETURNING id";

        return conn.preparedQuery(insertOrderSql)
            .execute(Tuple.of(order.getCustomerId(), order.getTotal(), "PENDING"))
            .compose(rows -> {
                Integer orderId = rows.iterator().next().getInteger("id");
                System.out.println("📋 Order inserted with ID: " + orderId);

                List<Future> itemFutures = new ArrayList<>();
                for (OrderItem item : order.getItems()) {
                    System.out.println("   ➡ Processing item - Product ID: " + item.getProductId() + ", Qty: " + item.getQuantity());
                    Future<Void> itemFuture = reduceStockAndInsertItem(conn, orderId, item);
                    itemFutures.add(itemFuture);
                }

                return CompositeFuture.all(itemFutures)
                    .compose(v -> {
                        System.out.println("✅ All " + itemFutures.size() + " order items processed successfully");
                        return Future.succeededFuture(orderId);
                    })
                    .recover(err -> {
                        System.err.println("❌ Error processing order items: " + err.getMessage());
                        return Future.failedFuture(err);
                    });
            })
            .recover(err -> {
                System.err.println("❌ Failed to insert order: " + err.getMessage());
                return Future.failedFuture(err);
            });
    }

    private Future<Void> reduceStockAndInsertItem(SqlConnection conn, Integer orderId, OrderItem item) {
        System.out.println("🔄 Starting stock reduction for Product ID: " + item.getProductId() + ", Qty: " + item.getQuantity());

        return checkStockAvailability(conn, item)
            .compose(v -> reduceStock(conn, item))
            .compose(v -> insertOrderItem(conn, orderId, item))
            .onSuccess(v -> System.out.println("✅ Successfully processed item for Product ID: " + item.getProductId()))
            .onFailure(err -> System.err.println("❌ Failed to process item for Product ID: " + item.getProductId() + ": " + err.getMessage()));
    }

    private Future<Void> checkStockAvailability(SqlConnection conn, OrderItem item) {
        String checkStockSql = "SELECT stock, name FROM products WHERE id = $1";

        return conn.preparedQuery(checkStockSql)
            .execute(Tuple.of(item.getProductId()))
            .compose(result -> {
                if (result.size() == 0) {
                    return Future.failedFuture("Product not found with ID: " + item.getProductId());
                }

                Row productRow = result.iterator().next();
                Integer currentStock = productRow.getInteger("stock");
                String productName = productRow.getString("name");

                System.out.println("📊 Product: " + productName + " (ID: " + item.getProductId() + ") - Current stock: " + currentStock + ", Required: " + item.getQuantity());

                if (currentStock < item.getQuantity()) {
                    return Future.failedFuture("Insufficient stock for product: " + productName + ". Available: " + currentStock + ", Required: " + item.getQuantity());
                }

                return Future.succeededFuture();
            });
    }

    private Future<Void> reduceStock(SqlConnection conn, OrderItem item) {
        String reduceStockSql = "UPDATE products SET stock = stock - $1 WHERE id = $2";
        System.out.println("📉 Reducing stock for Product ID: " + item.getProductId() + " by " + item.getQuantity());

        return conn.preparedQuery(reduceStockSql)
            .execute(Tuple.of(item.getQuantity(), item.getProductId()))
            .compose(result -> {
                if (result.rowCount() == 0) {
                    return Future.failedFuture("Failed to update stock for product ID: " + item.getProductId());
                }
                System.out.println("✅ Stock reduced for Product ID: " + item.getProductId());
                return Future.succeededFuture();
            });
    }

    private Future<Void> insertOrderItem(SqlConnection conn, Integer orderId, OrderItem item) {
        String insertItemSql = "INSERT INTO order_items (order_id, product_id, quantity, price) VALUES ($1, $2, $3, $4)";
        System.out.println("📦 Inserting order item for Product ID: " + item.getProductId());

        Future<Void> future = conn.preparedQuery(insertItemSql)
            .execute(Tuple.of(orderId, item.getProductId(), item.getQuantity(), item.getPrice()))
            .mapEmpty();

        future.onSuccess(v -> System.out.println("✅ Order item inserted for Product ID: " + item.getProductId()));
        return future;
    }

    // -------------------------------
    // 2️⃣ Get All Orders
    // -------------------------------
    public Future<List<Order>> getAllOrders() {
        Promise<List<Order>> promise = Promise.promise();
        String query = "SELECT * FROM orders ORDER BY id DESC";

        client.query(query).execute(ar -> {
            if (ar.succeeded()) {
                List<Order> orders = new ArrayList<>();
                for (Row row : ar.result()) {
                    Order order = new Order();
                    order.setId(row.getInteger("id"));
                    order.setCustomerId(row.getInteger("customer_id"));
                    order.setTotal(row.getDouble("total"));
                    order.setStatus(row.getString("status"));
                    orders.add(order);
                }
                promise.complete(orders);
            } else {
                promise.fail(ar.cause());
            }
        });

        return promise.future();
    }

    // -------------------------------
    // 3️⃣ Update Order Status
    // -------------------------------
    public Future<Void> updateOrderStatus(Integer orderId, String status) {
        String query = "UPDATE orders SET status = $1 WHERE id = $2";
        return client.preparedQuery(query)
            .execute(Tuple.of(status, orderId))
            .mapEmpty();
    }

    // -------------------------------
    // 4️⃣ Create Invoice
    // -------------------------------
    public Future<Integer> createInvoice(Integer orderId, String invoiceNumber, Double total) {
        Promise<Integer> promise = Promise.promise();
        String query = "INSERT INTO invoices (order_id, invoice_number, total) VALUES ($1, $2, $3) RETURNING id";

        client.preparedQuery(query)
            .execute(Tuple.of(orderId, invoiceNumber, total))
            .onSuccess(rows -> {
                Integer invoiceId = rows.iterator().next().getInteger("id");
                promise.complete(invoiceId);
            })
            .onFailure(promise::fail);

        return promise.future();
    }

    // -------------------------------
    // 5️⃣ Get Order Items
    // -------------------------------
    public Future<List<OrderItem>> getOrderItems(Integer orderId) {
        Promise<List<OrderItem>> promise = Promise.promise();
        String query = "SELECT * FROM order_items WHERE order_id = $1";

        client.preparedQuery(query)
            .execute(Tuple.of(orderId))
            .onSuccess(rows -> {
                List<OrderItem> items = new ArrayList<>();
                for (Row row : rows) {
                    OrderItem item = new OrderItem();
                    item.setId(row.getInteger("id"));
                    item.setOrderId(row.getInteger("order_id"));
                    item.setProductId(row.getInteger("product_id"));
                    item.setQuantity(row.getInteger("quantity"));
                    item.setPrice(row.getDouble("price"));
                    items.add(item);
                }
                promise.complete(items);
            })
            .onFailure(promise::fail);

        return promise.future();
    }

    // -------------------------------
    // 6️⃣ Get Order by ID
    // -------------------------------
    public Future<Order> getOrderById(Integer orderId) {
        Promise<Order> promise = Promise.promise();
        String query = "SELECT * FROM orders WHERE id = $1";

        client.preparedQuery(query)
            .execute(Tuple.of(orderId))
            .onSuccess(rows -> {
                if (rows.size() > 0) {
                    Row row = rows.iterator().next();
                    Order order = new Order();
                    order.setId(row.getInteger("id"));
                    order.setCustomerId(row.getInteger("customer_id"));
                    order.setTotal(row.getDouble("total"));
                    order.setStatus(row.getString("status"));
                    promise.complete(order);
                } else {
                    promise.fail("Order not found with id: " + orderId);
                }
            })
            .onFailure(promise::fail);

        return promise.future();
    }

    // -------------------------------
    // 7️⃣ Delete Order
    // -------------------------------
    public Future<Void> deleteOrder(Integer orderId) {
        Promise<Void> promise = Promise.promise();

        String deleteItems = "DELETE FROM order_items WHERE order_id = $1";
        client.preparedQuery(deleteItems)
            .execute(Tuple.of(orderId))
            .compose(v -> {
                String deleteOrder = "DELETE FROM orders WHERE id = $1";
                return client.preparedQuery(deleteOrder).execute(Tuple.of(orderId));
            })
            .onSuccess(rows -> promise.complete())
            .onFailure(promise::fail);

        return promise.future();
    }

    // -------------------------------
    // 8️⃣ Get Orders by Customer ID
    // -------------------------------
    public Future<List<Order>> getOrdersByCustomerId(Integer customerId) {
        Promise<List<Order>> promise = Promise.promise();
        String query = "SELECT * FROM orders WHERE customer_id = $1 ORDER BY id DESC";

        client.preparedQuery(query)
            .execute(Tuple.of(customerId))
            .onSuccess(rows -> {
                List<Order> orders = new ArrayList<>();
                for (Row row : rows) {
                    Order order = new Order();
                    order.setId(row.getInteger("id"));
                    order.setCustomerId(row.getInteger("customer_id"));
                    order.setTotal(row.getDouble("total"));
                    order.setStatus(row.getString("status"));
                    orders.add(order);
                }
                promise.complete(orders);
            })
            .onFailure(promise::fail);

        return promise.future();
    }

    // -------------------------------
    // 9️⃣ Get Orders by Status
    // -------------------------------
    public Future<List<Order>> getOrdersByStatus(String status) {
        Promise<List<Order>> promise = Promise.promise();
        String query = "SELECT * FROM orders WHERE status = $1 ORDER BY id DESC";

        client.preparedQuery(query)
            .execute(Tuple.of(status))
            .onSuccess(rows -> {
                List<Order> orders = new ArrayList<>();
                for (Row row : rows) {
                    Order order = new Order();
                    order.setId(row.getInteger("id"));
                    order.setCustomerId(row.getInteger("customer_id"));
                    order.setTotal(row.getDouble("total"));
                    order.setStatus(row.getString("status"));
                    orders.add(order);
                }
                promise.complete(orders);
            })
            .onFailure(promise::fail);

        return promise.future();
    }
}
