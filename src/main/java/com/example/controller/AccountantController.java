package com.example.controller;

import com.example.service.InvoiceService;
import com.example.service.OrderService;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class AccountantController {

    private final OrderService orderService;
    private final InvoiceService invoiceService;

    public AccountantController(Vertx vertx, Router router, OrderService orderService, InvoiceService invoiceService) {
        this.orderService = orderService;
        this.invoiceService = invoiceService;

        // Register routes
        router.get("/api/accountant/orders").handler(this::getAllOrders);
        router.post("/api/accountant/invoices").handler(this::createInvoice);
    }

    // Fetch all orders
    private void getAllOrders(RoutingContext ctx) {
        orderService.getAllOrders().onComplete(ar -> {
            if (ar.succeeded()) {
                JsonArray jsonOrders = new JsonArray(ar.result());
                ctx.response().putHeader("Content-Type", "application/json").end(jsonOrders.encode());
            } else {
                ctx.response().setStatusCode(500).end("Failed to fetch orders");
            }
        });
    }

    // Create invoice
    private void createInvoice(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        int orderId = body.getInteger("orderId");
        String invoiceNumber = body.getString("invoiceNumber");
        double total = body.getDouble("total");

        invoiceService.createInvoice(orderId, invoiceNumber, total).onComplete(ar -> {
            if (ar.succeeded()) {
                int invoiceId = ar.result();

                // Get customer info
                invoiceService.getCustomerByOrder(orderId).onComplete(customerRes -> {
                    if (customerRes.succeeded()) {
                        String email = customerRes.result().getString("email");
                        String name = customerRes.result().getString("name");

                        // Generate PDF
                        byte[] pdfBytes = invoiceService.generateInvoicePDF(invoiceId, orderId, total, name);

                        // Send email
                        invoiceService.sendInvoiceEmail(email, "Invoice #" + invoiceId,
                                "Dear " + name + ",\nPlease find your invoice attached.", pdfBytes);
                    }
                });

                JsonObject res = new JsonObject()
                        .put("message", "Invoice created")
                        .put("invoiceId", invoiceId);
                ctx.response().putHeader("Content-Type", "application/json").end(res.encode());

            } else {
                ctx.response().setStatusCode(500).end("Failed to create invoice");
            }
        });
    }
}
