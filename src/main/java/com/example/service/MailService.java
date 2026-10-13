package com.example.service;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;

public class MailService {
    private final MailClient mailClient;
    private final String defaultSender;

    public MailService(Vertx vertx) {
        // ✅ configure your SMTP (Gmail example)
        MailConfig config = new MailConfig()
            .setHostname("smtp.gmail.com")
            .setPort(587)
            .setStarttls(io.vertx.ext.mail.StartTLSOptions.REQUIRED)
            .setUsername(System.getenv("yewaleshetk@gmail.com")) // set via PowerShell: $env:EMAIL_USER="your-email@gmail.com"
            .setPassword(System.getenv("abcd efgh ijkl mnop")); // set via PowerShell: $env:EMAIL_PASS="your-app-password"

        this.defaultSender = System.getenv("EMAIL_USER");
        this.mailClient = MailClient.createShared(vertx, config);
    }

    public Future<Void> sendInvoiceEmail(String to, String subject, String body) {
        MailMessage message = new MailMessage()
            .setFrom(defaultSender)  // ✅ must not be null
            .setTo(to)
            .setSubject(subject)
            .setText(body);

        return mailClient.sendMail(message).mapEmpty();
    }
}
