package com.example.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.util.Date;

public class JwtUtil {
    private static final String SECRET = "your-very-secret-key";  // Replace with your secret!
    private static final Algorithm algorithm = Algorithm.HMAC256(SECRET);
    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000; // 1 day expiry

    public static String generateToken(int userId, String username, String role) {
        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withClaim("username", username)
                .withClaim("role", role)
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .sign(algorithm);
    }
}
