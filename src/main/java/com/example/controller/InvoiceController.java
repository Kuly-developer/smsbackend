package com.example.controller;

import com.example.service.InvoiceService;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class InvoiceController {

    private final InvoiceService invoiceService;
    private final Router router;

    public InvoiceController(Vertx vertx, InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
        this.router = Router.router(vertx);

        // Define routes with /invoices base path
        router.get("/invoices/:customerId").handler(this::getInvoicesByCustomer);
        router.delete("/invoices/:id").handler(this::deleteInvoice);

        System.out.println("✅ InvoiceController routes registered:");
        System.out.println("   GET /api/invoices/:customerId");
        System.out.println("   DELETE /api/invoices/:id");
    }

    public Router getRouter() {
        return router;
    }

    private void getInvoicesByCustomer(RoutingContext ctx) {
        try {
            int customerId = Integer.parseInt(ctx.pathParam("customerId"));
            System.out.println("📋 Getting invoices for customer: " + customerId);

            invoiceService.getInvoicesByCustomer(customerId)
                .onSuccess(invoices -> {
                    System.out.println("✅ Found " + invoices.size() + " invoices for customer " + customerId);
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(Json.encodePrettily(invoices));
                })
                .onFailure(err -> {
                    System.err.println("❌ Failed to get invoices: " + err.getMessage());
                    ctx.response()
                        .setStatusCode(500)
                        .end("Failed to load invoices: " + err.getMessage());
                });
        } catch (NumberFormatException e) {
            ctx.response()
                .setStatusCode(400)
                .end("Invalid customer ID");
        }
    }

    private void deleteInvoice(RoutingContext ctx) {
        try {
            int invoiceId = Integer.parseInt(ctx.pathParam("id"));
            System.out.println("🗑️ Attempting to delete invoice ID: " + invoiceId);

            invoiceService.deleteInvoice(invoiceId)
                .onSuccess(result -> {
                    System.out.println("✅ Invoice deleted successfully, ID: " + invoiceId);
                    ctx.response()
                        .setStatusCode(204) // No Content
                        .end();
                })
                .onFailure(err -> {
                    System.err.println("❌ Failed to delete invoice: " + err.getMessage());
                    ctx.response()
                        .setStatusCode(500)
                        .end("Failed to delete invoice: " + err.getMessage());
                });
        } catch (NumberFormatException e) {
            System.err.println("❌ Invalid invoice ID format");
            ctx.response()
                .setStatusCode(400)
                .end("Invalid invoice ID");
        }
    }
}
