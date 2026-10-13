package com.example;

import java.util.Set;

import com.example.config.DatabaseConfig;
import com.example.controller.AccountantController;
import com.example.controller.AuthController;
import com.example.controller.CartController;
import com.example.controller.CustomerController;
import com.example.controller.InvoiceController;
import com.example.controller.OrderController;
import com.example.controller.PaymentController;
import com.example.controller.PermissionsController; // ADD THIS IMPORT
import com.example.controller.ProductController;
import com.example.controller.ReportsController;
import com.example.controller.RoleController;
import com.example.controller.UserController;
import com.example.service.AuthService;
import com.example.service.CartService;
import com.example.service.CustomerService;
import com.example.service.InvoiceService;
import com.example.service.OrderService;
import com.example.service.PaymentService;
import com.example.service.PermissionsService; // ADD THIS IMPORT
import com.example.service.ProductService;
import com.example.service.RoleService;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.pgclient.PgPool;

public class MainVerticle extends AbstractVerticle {

    // Make client accessible globally for services
    public static PgPool client;

    @Override
    public void start(Promise<Void> startPromise) {

        // Initialize database
        client = DatabaseConfig.getPgClient(vertx);

        Router router = Router.router(vertx);

        // Global CORS
        router.route().handler(
            CorsHandler.create("*")
                .allowedMethods(Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS))
                .allowedHeaders(Set.of("Content-Type", "Authorization", "Access-Control-Allow-Credentials"))
                .allowCredentials(true)
        );

        // Pre-flight OPTIONS
        router.options().handler(ctx -> ctx.response().setStatusCode(204).end());

        // Initialize services
        AuthService authService = new AuthService(client);
        RoleService roleService = new RoleService();
        CustomerService customerService = new CustomerService(client);
        ProductService productService = new ProductService(client);
        CartService cartService = new CartService(client);
        PaymentService paymentService = new PaymentService();
        OrderService orderService = new OrderService(client);
        InvoiceService invoiceService = new InvoiceService(client, vertx);
        PermissionsService permissionsService = new PermissionsService(); // ADD THIS

        // ✅ ADD PERMISSIONS CONTROLLER REGISTRATION
        PermissionsController permissionsController = new PermissionsController(permissionsService);
        permissionsController.registerRoutes(router);
        System.out.println("PermissionsController registered with routes:");
        System.out.println("   - PUT /api/permissions/:customerId");
        System.out.println("   - GET /api/permissions/:customerId");
        System.out.println("   - GET /api/permissions");

        // ✅ ADD REPORTS CONTROLLER REGISTRATION
        ReportsController reportsController = new ReportsController();
        reportsController.registerRoutes(router);
        System.out.println("ReportsController registered with routes:");
        System.out.println("   - GET /api/reports/dashboard-stats");
        System.out.println("   - GET /api/reports/sales-overview");
        System.out.println("   - GET /api/reports/payment-analytics");
        System.out.println("   - GET /api/reports/customer-analytics");
        System.out.println("   - GET /api/reports/sales-trends");
        System.out.println("   - GET /api/reports/product-performance");

        // ✅ Order Controller Registration
        OrderController orderController = new OrderController(vertx, router, orderService);
        System.out.println("OrderController registered with routes:");
        System.out.println("   - GET /api/orders");
        System.out.println("   - GET /api/orders/:id");
        System.out.println("   - GET /api/orders/:id/items");
        System.out.println("   - POST /api/orders");
        System.out.println("   - PUT /api/orders/:id/status");
        System.out.println("   - DELETE /api/orders/:id");

        // Mount controllers
        new AuthController(vertx, router, authService, roleService);

        CustomerController customerController = new CustomerController(vertx, customerService);
        router.mountSubRouter("/api/customers", customerController.getRouter());

        new RoleController(vertx, router);

        UserController userController = new UserController(client);
        userController.mountRoutes(router);

        ProductController productController = new ProductController(vertx, productService);
        router.mountSubRouter("/api/products", productController.getRouter());

        new CartController(vertx, router, cartService);

        PaymentController paymentController = new PaymentController(paymentService);
        paymentController.registerRoutes(router);

        // AccountantController (Orders + Invoices combined view)
        new AccountantController(vertx, router, orderService, invoiceService);

        // InvoiceController
        InvoiceController invoiceController = new InvoiceController(vertx, invoiceService);
        router.mountSubRouter("/api", invoiceController.getRouter());

        // Start HTTP server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8889)
            .onSuccess(server -> {
                System.out.println("Server running at http://localhost:8889");
                System.out.println("Orders API available at: http://localhost:8889/api/orders");
                System.out.println("Accountant API available at: http://localhost:8889/api/accountant");
                System.out.println("Reports API available at: http://localhost:8889/api/reports");
                System.out.println("Permissions API available at: http://localhost:8889/api/permissions");
                System.out.println("\nAvailable Reports Endpoints:");
                System.out.println("   - GET /api/reports/dashboard-stats");
                System.out.println("   - GET /api/reports/sales-overview?period=month");
                System.out.println("   - GET /api/reports/payment-analytics");
                System.out.println("   - GET /api/reports/customer-analytics");
                System.out.println("   - GET /api/reports/sales-trends?days=30");
                System.out.println("   - GET /api/reports/product-performance");
                System.out.println("\nAvailable Permissions Endpoints:");
                System.out.println("   - PUT /api/permissions/:customerId");
                System.out.println("   - GET /api/permissions/:customerId");
                System.out.println("   - GET /api/permissions");
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }
}
