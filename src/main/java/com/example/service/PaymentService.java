package com.example.service;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

import io.vertx.core.json.JsonObject;

public class PaymentService {

    public PaymentService() {
        Stripe.apiKey = "sk_test_51RydaB33g94wJ2e0TklGOPAJoMdsicTqH28jn5WGGMVozKrfn2S1piRZbqjivhH4UvFcEIgAPqjuGMi9erzUirEW00LbE19zmz";
    }


    public JsonObject createCheckoutSession(double amount, String currency, Long orderId) throws Exception {
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl("http://localhost:4200/payment-success?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl("http://localhost:4200/payment-cancelled")
            .putMetadata("orderId", orderId.toString())
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(currency)
                            .setUnitAmount((long) (amount * 100))
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName("Order #" + orderId)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build();

        Session session = Session.create(params);

        // Return a JsonObject instead of String
        return new JsonObject()
            .put("url", session.getUrl())
            .put("sessionId", session.getId());
    }
}
