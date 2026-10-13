package com.example.service;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailAttachment;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;

public class InvoiceService {

    private final PgPool client;
    private final Vertx vertx;

    public InvoiceService(PgPool client, Vertx vertx) {
        this.client = client;
        this.vertx = vertx;
    }

    public Future<Integer> createInvoice(int orderId, String invoiceNumber, double total) {
        Promise<Integer> promise = Promise.promise();
        String sql = "INSERT INTO invoices(order_id, invoice_number, total) VALUES($1, $2, $3) RETURNING id";

        client.preparedQuery(sql)
              .execute(Tuple.of(orderId, invoiceNumber, total))
              .onSuccess(rows -> {
                  Integer id = rows.iterator().next().getInteger("id");
                  promise.complete(id);
              })
              .onFailure(promise::fail);

        return promise.future();
    }

    public byte[] generateInvoicePDF(int invoiceId, int orderId, double total, String customerName) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.add(new Paragraph("Invoice #" + invoiceId));
            document.add(new Paragraph("Customer: " + customerName));
            document.add(new Paragraph("Order ID: " + orderId));
            document.add(new Paragraph("Total: $" + total));
            document.add(new Paragraph("Thank you for your purchase!"));

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void sendInvoiceEmail(String toEmail, String subject, String body, byte[] pdfBytes) {
        MailConfig config = new MailConfig();
        config.setHostname("smtp.gmail.com");
        config.setPort(587);
        config.setStarttls(StartTLSOptions.REQUIRED);
        config.setUsername(System.getenv("EMAIL_USER"));
        config.setPassword(System.getenv("EMAIL_PASS"));

        MailClient mailClient = MailClient.createShared(vertx, config);

        MailMessage message = new MailMessage();
        message.setFrom(System.getenv("EMAIL_USER"));
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        MailAttachment attachment = MailAttachment.create()
                .setName("invoice.pdf")
                .setData(Buffer.buffer(pdfBytes));

        message.setAttachment(Collections.singletonList(attachment));

        mailClient.sendMail(message, ar -> {
            if (ar.succeeded()) {
                System.out.println("✅ Invoice sent to " + toEmail);
            } else {
                System.err.println("❌ Failed to send invoice: " + ar.cause().getMessage());
            }
        });
    }

    public Future<JsonObject> getCustomerByOrder(int orderId) {
        Promise<JsonObject> promise = Promise.promise();
        String sql = "SELECT c.name, c.email FROM customers c " +
                     "JOIN orders o ON o.customer_id = c.id WHERE o.id=$1";

        client.preparedQuery(sql)
              .execute(Tuple.of(orderId))
              .onSuccess(rows -> {
                  if (rows.rowCount() > 0) {
                      JsonObject customer = new JsonObject()
                              .put("name", rows.iterator().next().getString("name"))
                              .put("email", rows.iterator().next().getString("email"));
                      promise.complete(customer);
                  } else {
                      promise.fail("Customer not found for order " + orderId);
                  }
              })
              .onFailure(promise::fail);

        return promise.future();
    }

    public Future<List<JsonObject>> getInvoicesByCustomer(int customerId) {
        Promise<List<JsonObject>> promise = Promise.promise();

        // FIXED: Removed i.status from SELECT clause
        String sql = "SELECT i.id, i.invoice_number, i.total, i.created_at, " +
                     "o.id as order_id, c.name as customer_name, c.email as customer_email " +
                     "FROM invoices i " +
                     "JOIN orders o ON i.order_id = o.id " +
                     "JOIN customers c ON o.customer_id = c.id " +
                     "WHERE c.id = $1 " +
                     "ORDER BY i.created_at DESC";

        System.out.println("🔍 Executing SQL for customer " + customerId);

        client.preparedQuery(sql)
              .execute(Tuple.of(customerId))
              .onSuccess(rows -> {
                  System.out.println("📊 Found " + rows.rowCount() + " invoice rows for customer " + customerId);

                  List<JsonObject> invoices = new java.util.ArrayList<>();
                  for (var row : rows) {
                      JsonObject invoice = new JsonObject()
                              .put("id", row.getInteger("id"))
                              .put("invoice_number", row.getString("invoice_number"))
                              .put("total", row.getDouble("total"))
                              .put("created_at", row.getValue("created_at").toString())
                              .put("order_id", row.getInteger("order_id"))
                              .put("customer_name", row.getString("customer_name"))
                              .put("customer_email", row.getString("customer_email"));
                      invoices.add(invoice);
                  }
                  System.out.println("✅ Processed " + invoices.size() + " invoices for customer " + customerId);
                  promise.complete(invoices);
              })
              .onFailure(err -> {
                  System.err.println("❌ Database error for customer " + customerId + ": " + err.getMessage());
                  promise.fail(err);
              });

        return promise.future();
    }

    public Future<Boolean> verifyInvoiceOwnership(int invoiceId, int customerId) {
        Promise<Boolean> promise = Promise.promise();

        String sql = "SELECT COUNT(*) FROM invoices i " +
                     "JOIN orders o ON i.order_id = o.id " +
                     "WHERE i.id = $1 AND o.customer_id = $2";

        client.preparedQuery(sql)
              .execute(Tuple.of(invoiceId, customerId))
              .onSuccess(rows -> {
                  long count = rows.iterator().next().getLong("count");
                  System.out.println("🔐 Ownership verification - Invoice " + invoiceId +
                                   " belongs to customer " + customerId + ": " + (count > 0));
                  promise.complete(count > 0);
              })
              .onFailure(err -> {
                  System.err.println("❌ Error verifying ownership: " + err.getMessage());
                  promise.fail(err);
              });

        return promise.future();
    }

    public Future<Buffer> downloadInvoice(int invoiceId) {
        Promise<Buffer> promise = Promise.promise();

        String sql = "SELECT i.id, i.order_id, i.total, c.name AS customer_name " +
                     "FROM invoices i " +
                     "JOIN orders o ON i.order_id = o.id " +
                     "JOIN customers c ON o.customer_id = c.id " +
                     "WHERE i.id=$1";

        client.preparedQuery(sql)
              .execute(Tuple.of(invoiceId))
              .onSuccess(rows -> {
                  if (rows.rowCount() > 0) {
                      var row = rows.iterator().next();
                      int id = row.getInteger("id");
                      int orderId = row.getInteger("order_id");
                      double total = row.getDouble("total");
                      String customerName = row.getString("customer_name");

                      byte[] pdf = generateInvoicePDF(id, orderId, total, customerName);
                      if (pdf != null) {
                          promise.complete(Buffer.buffer(pdf));
                      } else {
                          promise.fail("Failed to generate PDF");
                      }
                  } else {
                      promise.fail("Invoice not found: " + invoiceId);
                  }
              })
              .onFailure(promise::fail);

        return promise.future();
    }

    public Future<Boolean> deleteInvoice(int invoiceId) {
        Promise<Boolean> promise = Promise.promise();

        String sql = "DELETE FROM invoices WHERE id = $1";

        client.preparedQuery(sql)
            .execute(Tuple.of(invoiceId))
            .onSuccess(rows -> {
                if (rows.rowCount() > 0) {
                    System.out.println("✅ Invoice deleted successfully, ID: " + invoiceId);
                    promise.complete(true);
                } else {
                    System.out.println("❌ Invoice not found, ID: " + invoiceId);
                    promise.fail("Invoice not found");
                }
            })
            .onFailure(err -> {
                System.err.println("❌ Failed to delete invoice: " + err.getMessage());
                promise.fail("Database error: " + err.getMessage());
            });

        return promise.future();
    }
}
