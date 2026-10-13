package com.example.config;

import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;

public class DatabaseConfig {

    public static PgPool getPgClient(Vertx vertx) {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(5432)
                .setHost("localhost")
                .setDatabase("sms1")
                .setUser("vertx_user")
                .setPassword("12345");

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        return PgPool.pool(vertx, connectOptions, poolOptions);
    }
}
