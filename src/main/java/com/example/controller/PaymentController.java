package com.example.controller;

import com.example.MainVerticle;
import com.example.service.PaymentService;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class PaymentController {

    private final PaymentService paymentService;
    private static final String STRIPE_WEBHOOK_SECRET ="whsec_3db26b780cdd94ad1fd8838910a8c7da34378d0095242f4c53bd454333cd8d0a";

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public void registerRoutes(Router router) {
        router.post("/api/payment/checkout").handler(this::createCheckout);
        router.post("/api/payment/webhook").handler(this::handleStripeWebhook);
        router.post("/api/payment/verify-session").handler(this::verifySession);
        router.get("/api/payment/debug-webhook").handler(this::debugWebhook);
        router.post("/api/payment/test-webhook").handler(this::testWebhook);
        router.get("/api/payment/payments").handler(this::getAllPayments);
    }

    // 1️⃣ Create Checkout
    private void createCheckout(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        double amount = body.getDouble("amount");
        String currency = body.getString("currency");
        long customerId = body.getLong("customerId");

        if (customerId == 0) {
            ctx.response().setStatusCode(400).end("Customer not logged in");
            return;
        }

        String insertOrderSQL = "INSERT INTO orders (customer_id, total, paid) VALUES ($1, $2, false) RETURNING id";
        MainVerticle.client.preparedQuery(insertOrderSQL)
            .execute(Tuple.of(customerId, amount))
            .onSuccess(orderRes -> {
                Long orderId = orderRes.iterator().next().getLong("id");

                String insertItemsSQL = "INSERT INTO order_items (order_id, product_id, quantity, price) " +
                        "SELECT $1, product_id, quantity, price FROM cart WHERE customer_id = $2";
                MainVerticle.client.preparedQuery(insertItemsSQL)
                    .execute(Tuple.of(orderId, customerId))
                    .onSuccess(rows -> {
                        ctx.vertx().<JsonObject>executeBlocking(promise -> {
                            try {
                                JsonObject sessionData = paymentService.createCheckoutSession(amount, currency, orderId);
                                String sessionId = sessionData.getString("sessionId");
                                String url = sessionData.getString("url");

                                // Save sessionId to orders table
                                String updateSql = "UPDATE orders SET session_id=$1 WHERE id=$2";
                                MainVerticle.client.preparedQuery(updateSql)
                                    .execute(Tuple.of(sessionId, orderId))
                                    .onFailure(err -> System.err.println("Failed to save session_id: " + err.getMessage()));

                                promise.complete(sessionData.put("orderId", orderId));
                            } catch (Exception e) {
                                promise.fail(e);
                            }
                        }).onSuccess(result -> {
                            ctx.response().putHeader("Content-Type", "application/json").end(result.encode());
                        }).onFailure(err -> {
                            err.printStackTrace();
                            ctx.response().setStatusCode(500).end("Failed to create Stripe session: " + err.getMessage());
                        });
                    })
                    .onFailure(err -> ctx.response().setStatusCode(500).end("Error inserting order items"));
            })
            .onFailure(err -> ctx.response().setStatusCode(500).end("Error creating order"));
    }

    // 2️⃣ Webhook
    private void handleStripeWebhook(RoutingContext ctx) {
        System.out.println("=== WEBHOOK RECEIVED ===");
        Buffer bodyBuffer = ctx.getBody();
        String payload = bodyBuffer.toString();
        String sigHeader = ctx.request().getHeader("Stripe-Signature");

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, STRIPE_WEBHOOK_SECRET);
            System.out.println("✅ Webhook signature verified");
        } catch (Exception e) {
            System.err.println("❌ Webhook signature verification failed: " + e.getMessage());
            ctx.response().setStatusCode(400).end("Webhook Error: " + e.getMessage());
            return;
        }

        System.out.println("🎯 Event type: " + event.getType());

        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
            if (session != null) {
                System.out.println("💳 Checkout session completed: " + session.getId());
                handleCheckoutSession(session);
            }
        }

        ctx.response().setStatusCode(200).end();
    }

    private void handleCheckoutSession(Session session) {
        String orderIdStr = session.getMetadata().get("orderId");
        if (orderIdStr == null) {
            System.err.println("⚠️ No orderId found in Stripe session metadata");
            return;
        }

        Long orderId = Long.parseLong(orderIdStr);
        double amountPaid = session.getAmountTotal() / 100.0;

        System.out.println("🌐 Webhook received for orderId=" + orderId + ", stripeSessionId=" + session.getId());
        System.out.println("💰 Amount paid: " + amountPaid);
        System.out.println("💳 Payment status: " + session.getPaymentStatus());

        // ✅ Update order as paid
        String sql = "UPDATE orders SET paid = true, amount_paid = $1, status='completed' WHERE id = $2";
        MainVerticle.client.preparedQuery(sql)
            .execute(Tuple.of(amountPaid, orderId))
            .onSuccess(res -> {
                System.out.println("✅ Order marked as paid: " + orderId);

                // ✅ Find customer_id for this order
                String findCustomerSql = "SELECT customer_id FROM orders WHERE id=$1";
                MainVerticle.client.preparedQuery(findCustomerSql)
                    .execute(Tuple.of(orderId))
                    .onSuccess(rows -> {
                        if (rows.size() > 0) {
                            Long customerId = rows.iterator().next().getLong("customer_id");

                            // ✅ REDUCE PRODUCT STOCK (NEW)
                            reduceProductStock(orderId);

                            // ✅ Clear the cart for this customer
                            String clearCartSql = "DELETE FROM cart WHERE customer_id=$1";
                            MainVerticle.client.preparedQuery(clearCartSql)
                                .execute(Tuple.of(customerId))
                                .onSuccess(r -> System.out.println("🛒 Cart cleared for customerId=" + customerId))
                                .onFailure(err -> System.err.println("❌ Failed to clear cart: " + err.getMessage()));
                        }
                    })
                    .onFailure(err -> System.err.println("❌ Failed to fetch customer_id for order: " + err.getMessage()));
            })
            .onFailure(err -> System.err.println("❌ Failed to update order: " + err.getMessage()));
    }

    // NEW: Reduce product stock after successful payment
    private void reduceProductStock(Long orderId) {
        System.out.println("📦 Reducing product stock for order: " + orderId);

        // Simple SQL to reduce stock based on order items
        String reduceStockSql = "UPDATE products p " +
                               "SET stock = stock - oi.quantity " +
                               "FROM order_items oi " +
                               "WHERE p.id = oi.product_id AND oi.order_id = $1";

        MainVerticle.client.preparedQuery(reduceStockSql)
            .execute(Tuple.of(orderId))
            .onSuccess(result -> {
                System.out.println("✅ Product stock reduced for order: " + orderId);

                // Log which products were updated
                logUpdatedProducts(orderId);
            })
            .onFailure(err -> {
                System.err.println("❌ Failed to reduce product stock: " + err.getMessage());
            });
    }

    // NEW: Log updated products for debugging
    private void logUpdatedProducts(Long orderId) {
        String logSql = "SELECT oi.product_id, p.name, oi.quantity, p.stock as new_stock " +
                       "FROM order_items oi " +
                       "JOIN products p ON p.id = oi.product_id " +
                       "WHERE oi.order_id = $1";

        MainVerticle.client.preparedQuery(logSql)
            .execute(Tuple.of(orderId))
            .onSuccess(rows -> {
                System.out.println("📊 Stock update details for order " + orderId + ":");
                for (Row row : rows) {
                    System.out.println("   Product: " + row.getString("name") +
                                     " | Quantity sold: " + row.getInteger("quantity") +
                                     " | New stock: " + row.getInteger("new_stock"));
                }
            })
            .onFailure(err -> {
                System.err.println("❌ Failed to log stock updates: " + err.getMessage());
            });
    }

    // 3️⃣ Verify Session
    private void verifySession(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        String sessionId = body.getString("sessionId");

        if (sessionId == null || sessionId.isEmpty()) {
            ctx.response().setStatusCode(400)
                .end(new JsonObject().put("paid", false).encode());
            return;
        }

        System.out.println("🔍 Verifying sessionId: " + sessionId);

        // First check database
        String sql = "SELECT paid, customer_id FROM orders WHERE session_id=$1 LIMIT 1";
        MainVerticle.client.preparedQuery(sql)
            .execute(Tuple.of(sessionId))
            .onSuccess(rows -> {
                if (rows.size() > 0) {
                    boolean paid = rows.iterator().next().getBoolean("paid");
                    System.out.println("➡️ Database result: paid=" + paid);

                    if (paid) {
                        // Already paid in database
                        ctx.response().putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("paid", true).encode());
                    } else {
                        // Not paid in database, check with Stripe directly
                        checkWithStripeDirectly(sessionId, ctx);
                    }
                } else {
                    // No order found with this session ID
                    ctx.response().setStatusCode(404)
                        .end(new JsonObject().put("paid", false).encode());
                }
            })
            .onFailure(err -> {
                System.err.println("❌ Database query failed: " + err.getMessage());
                checkWithStripeDirectly(sessionId, ctx);
            });
    }

    private void checkWithStripeDirectly(String sessionId, RoutingContext ctx) {
        System.out.println("🔍 Checking with Stripe directly for session: " + sessionId);

        ctx.vertx().executeBlocking(promise -> {
            try {
                Session session = Session.retrieve(sessionId);
                String paymentStatus = session.getPaymentStatus();
                System.out.println("➡️ Stripe direct check - Payment status: " + paymentStatus);

                boolean paid = "paid".equals(paymentStatus);

                if (paid) {
                    // Update our database since payment is actually complete
                    String updateSql = "UPDATE orders SET paid = true, status='completed' WHERE session_id=$1";
                    MainVerticle.client.preparedQuery(updateSql)
                        .execute(Tuple.of(sessionId))
                        .onSuccess(res -> {
                            System.out.println("✅ Updated database from Stripe direct check");

                            // Also reduce stock for this order
                            reduceStockForSession(sessionId);
                        })
                        .onFailure(err -> System.err.println("❌ Failed to update database: " + err.getMessage()));
                }

                promise.complete(paid);
            } catch (Exception e) {
                System.err.println("❌ Stripe direct check failed: " + e.getMessage());
                promise.fail(e);
            }
        }).onSuccess(paid -> {
            ctx.response().putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("paid", paid).encode());
        }).onFailure(err -> {
            ctx.response().setStatusCode(500)
                .end(new JsonObject().put("paid", false).encode());
        });
    }

    // NEW: Reduce stock when payment is verified via direct Stripe check
    private void reduceStockForSession(String sessionId) {
        System.out.println("📦 Reducing stock for session: " + sessionId);

        // First get the order ID for this session
        String getOrderSql = "SELECT id FROM orders WHERE session_id = $1";
        MainVerticle.client.preparedQuery(getOrderSql)
            .execute(Tuple.of(sessionId))
            .onSuccess(rows -> {
                if (rows.size() > 0) {
                    Long orderId = rows.iterator().next().getLong("id");
                    System.out.println("✅ Found order ID: " + orderId + " for session: " + sessionId);
                    reduceProductStock(orderId);
                }
            })
            .onFailure(err -> {
                System.err.println("❌ Failed to find order for session: " + err.getMessage());
            });
    }

    // 4️⃣ Get All Payments
    private void getAllPayments(RoutingContext ctx) {
        System.out.println("📊 Fetching all payments...");

        String sql = "SELECT o.id, o.customer_id, o.total as amount, o.paid, " +
                     "o.session_id, o.status, o.created_at, o.amount_paid " +
                     "FROM orders o " +
                     "ORDER BY o.created_at DESC";

        MainVerticle.client.preparedQuery(sql)
            .execute()
            .onSuccess(rows -> {
                JsonArray payments = new JsonArray();
                for (Row row : rows) {
                    JsonObject payment = new JsonObject()
                        .put("id", row.getLong("id"))
                        .put("orderId", row.getLong("id"))
                        .put("customerId", row.getLong("customer_id"))
                        .put("amount", row.getDouble("amount"))
                        .put("paid", row.getBoolean("paid"))
                        .put("sessionId", row.getString("session_id"))
                        .put("status", row.getString("status"))
                        .put("createdAt", row.getLocalDateTime("created_at").toString());

                    // Handle amount_paid which might be null
                    Double amountPaid = row.getDouble("amount_paid");
                    if (amountPaid != null) {
                        payment.put("amountPaid", amountPaid);
                    }

                    payments.add(payment);
                }

                System.out.println("✅ Found " + payments.size() + " payments");
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .putHeader("Access-Control-Allow-Origin", "*")
                    .end(payments.encode());
            })
            .onFailure(err -> {
                System.err.println("❌ Failed to fetch payments: " + err.getMessage());
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                        .put("error", "Failed to fetch payments")
                        .put("details", err.getMessage())
                        .encode());
            });
    }

    // 5️⃣ Debug endpoint
    private void debugWebhook(RoutingContext ctx) {
        System.out.println("=== WEBHOOK DEBUG INFO ===");
        System.out.println("Webhook secret: " + STRIPE_WEBHOOK_SECRET);
        System.out.println("Server running on: http://localhost:8889");
        System.out.println("Webhook endpoint: http://localhost:8889/api/payment/webhook");

        ctx.response().putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("status", "debug")
                .put("webhookEndpoint", "http://localhost:8889/api/payment/webhook")
                .put("message", "Check your Stripe CLI or webhook configuration")
                .encode());
    }

    // 6️⃣ Test webhook endpoint
    private void testWebhook(RoutingContext ctx) {
        System.out.println("🧪 Testing webhook manually...");

        // Create a mock webhook payload
        String mockPayload = "{\n" +
            "  \"id\": \"evt_test_webhook\",\n" +
            "  \"object\": \"event\",\n" +
            "  \"api_version\": \"2020-08-27\",\n" +
            "  \"created\": 1614577258,\n" +
            "  \"data\": {\n" +
            "    \"object\": {\n" +
            "      \"id\": \"cs_test_a1fLv5A1Z1Dy31SX3ZDCXr4z6brLYmN2Nxy2s4Vm3gDsmnlrbIS8uhLSD5\",\n" +
            "      \"object\": \"checkout.session\",\n" +
            "      \"amount_total\": 2000,\n" +
            "      \"currency\": \"usd\",\n" +
            "      \"customer\": \"cus_test\",\n" +
            "      \"metadata\": {\n" +
            "        \"orderId\": \"123\"\n" +
            "      },\n" +
            "      \"payment_status\": \"paid\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"type\": \"checkout.session.completed\"\n" +
            "}";

        // Create mock signature (for testing only)
        String mockSignature = "whsec_test_signature";

        // Test the webhook handling
        Buffer bodyBuffer = Buffer.buffer(mockPayload);
        String sigHeader = mockSignature;

        try {
            Event event = Webhook.constructEvent(mockPayload, sigHeader, STRIPE_WEBHOOK_SECRET);
            System.out.println("✅ Webhook verification successful in test");

            if ("checkout.session.completed".equals(event.getType())) {
                Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                if (session != null) {
                    System.out.println("✅ Would handle session: " + session.getId());
                    System.out.println("✅ Payment status: " + session.getPaymentStatus());
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Webhook test failed: " + e.getMessage());
        }

        ctx.response().end("Webhook test completed");
    }
}
