package com.example.middleware;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import io.vertx.ext.web.RoutingContext;

public class JwtAuthHandler {

    private static final String SECRET = "your-very-secret-key"; // Keep this safe and consistent

    public static void handle(RoutingContext ctx) {
        String authHeader = ctx.request().getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ctx.response().setStatusCode(401).end("Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring("Bearer ".length());

        try {
            Algorithm algorithm = Algorithm.HMAC256(SECRET);
            DecodedJWT decodedJWT = JWT.require(algorithm).build().verify(token);

            String userId = decodedJWT.getSubject();
            if (userId == null) {
                ctx.response().setStatusCode(401).end("Invalid token: no subject");
                return;
            }

            ctx.put("userId", Integer.parseInt(userId));
            ctx.next(); // Continue to route
        } catch (Exception e) {
            ctx.response().setStatusCode(401).end("Invalid token: " + e.getMessage());
        }
    }
}
