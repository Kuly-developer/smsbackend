package com.example.service;

import com.example.model.Role;
import com.example.model.User;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.List;

public class UserService {

    private final PgPool client;

    public UserService(PgPool client) {
        this.client = client;
    }

    // Check if username or email exists
    public Future<String> checkUserExists(String username, String email) {
        Promise<String> promise = Promise.promise();

        String sqlUsername = "SELECT COUNT(*) AS count FROM users WHERE username = $1";
        String sqlEmail = "SELECT COUNT(*) AS count FROM users WHERE email = $1";

        client.preparedQuery(sqlUsername).execute(Tuple.of(username), ar1 -> {
            if (ar1.failed()) {
                promise.fail(ar1.cause());
                return;
            }
            Row row1 = ar1.result().iterator().next();
            boolean usernameExists = row1.getInteger("count") > 0;

            client.preparedQuery(sqlEmail).execute(Tuple.of(email), ar2 -> {
                if (ar2.failed()) {
                    promise.fail(ar2.cause());
                    return;
                }
                Row row2 = ar2.result().iterator().next();
                boolean emailExists = row2.getInteger("count") > 0;

                if (usernameExists) promise.complete("Username already exists");
                else if (emailExists) promise.complete("Email already exists");
                else promise.complete(null);
            });
        });

        return promise.future();
    }

    // Create user (hash incoming password)
    public Future<Void> createUser(User user) {
        Promise<Void> promise = Promise.promise();

        checkUserExists(user.getUsername(), user.getEmail()).onComplete(check -> {
            if (check.succeeded()) {
                String message = check.result();
                if (message != null) {
                    promise.fail(message);
                } else {
                    // 🔹 Hash password from incoming "password" field
                    String hashedPassword = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());

                    String sql = "INSERT INTO users (username, password_hash, role_id, full_name, email) VALUES ($1,$2,$3,$4,$5)";
                    client.preparedQuery(sql).execute(
                            Tuple.of(
                                    user.getUsername(),
                                    hashedPassword,   // ✅ insert hashed password
                                    user.getRoleId(),
                                    user.getFullName(),
                                    user.getEmail()
                            ),
                            ar -> {
                                if (ar.succeeded()) promise.complete();
                                else promise.fail(ar.cause());
                            }
                    );
                }
            } else promise.fail(check.cause());
        });

        return promise.future();
    }

    // Find user by username
    public Future<User> findByUsername(String username) {
        Promise<User> promise = Promise.promise();
        String sql = "SELECT * FROM users WHERE username = $1";

        client.preparedQuery(sql).execute(Tuple.of(username), ar -> {
            if (ar.succeeded()) {
                RowSet<Row> rows = ar.result();
                if (!rows.iterator().hasNext()) {
                    promise.complete(null);
                    return;
                }
                Row row = rows.iterator().next();
                User user = new User();
                user.setId(row.getInteger("id"));
                user.setUsername(row.getString("username"));
                user.setPasswordHash(row.getString("password_hash"));
                user.setEmail(row.getString("email"));
                user.setFullName(row.getString("full_name"));
                user.setRoleId(row.getInteger("role_id"));
                promise.complete(user);
            } else promise.fail(ar.cause());
        });

        return promise.future();
    }

    // Get all roles
    public Future<List<Role>> getAllRoles() {
        Promise<List<Role>> promise = Promise.promise();
        client.query("SELECT id, name FROM roles ORDER BY id").execute(ar -> {
            if (ar.succeeded()) {
                List<Role> roles = new ArrayList<>();
                for (Row row : ar.result()) {
                    roles.add(new Role(row.getInteger("id"), row.getString("name")));
                }
                promise.complete(roles);
            } else promise.fail(ar.cause());
        });
        return promise.future();
    }

    // Update user role
    public Future<Void> updateUserRole(int userId, int roleId) {
        Promise<Void> promise = Promise.promise();
        String sql = "UPDATE users SET role_id = $1 WHERE id = $2";
        client.preparedQuery(sql).execute(Tuple.of(roleId, userId), ar -> {
            if (ar.succeeded()) promise.complete();
            else promise.fail(ar.cause());
        });
        return promise.future();
    }

    // Update user details
    public Future<Void> updateUserDetails(int userId, String username, String email, String password) {
        Promise<Void> promise = Promise.promise();
        String checkSql = "SELECT id FROM users WHERE (username = $1 OR email = $2) AND id != $3";

        client.preparedQuery(checkSql).execute(Tuple.of(username, email, userId), arCheck -> {
            if (arCheck.failed()) {
                promise.fail(arCheck.cause());
                return;
            }
            if (arCheck.result().size() > 0) {
                promise.fail("Username or email already exists");
                return;
            }

            String sql;
            Tuple params;

            if (password != null && !password.isEmpty()) {
                sql = "UPDATE users SET username = $1, email = $2, password_hash = $3 WHERE id = $4";
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
                params = Tuple.of(username, email, hashedPassword, userId);
            } else {
                sql = "UPDATE users SET username = $1, email = $2 WHERE id = $3";
                params = Tuple.of(username, email, userId);
            }

            client.preparedQuery(sql).execute(params, arUpdate -> {
                if (arUpdate.succeeded()) promise.complete();
                else promise.fail(arUpdate.cause());
            });
        });

        return promise.future();
    }

    // Get user by ID
    public Future<JsonObject> getUserById(int id) {
        Promise<JsonObject> promise = Promise.promise();
        String sql = "SELECT u.id, u.username, u.full_name, u.email, r.id AS role_id, r.name AS role_name " +
                     "FROM users u LEFT JOIN roles r ON u.role_id = r.id WHERE u.id = $1";
        client.preparedQuery(sql).execute(Tuple.of(id), ar -> {
            if (ar.succeeded()) {
                RowSet<Row> rows = ar.result();
                if (!rows.iterator().hasNext()) {
                    promise.complete(null);
                } else {
                    Row row = rows.iterator().next();
                    JsonObject userJson = new JsonObject()
                            .put("id", row.getInteger("id"))
                            .put("username", row.getString("username"))
                            .put("fullName", row.getString("full_name"))
                            .put("email", row.getString("email"))
                            .put("role", new JsonObject()
                                    .put("id", row.getInteger("role_id"))
                                    .put("name", row.getString("role_name")));
                    promise.complete(userJson);
                }
            } else promise.fail(ar.cause());
        });
        return promise.future();
    }

    // Get all users
    public Future<List<JsonObject>> getAllUsers() {
        Promise<List<JsonObject>> promise = Promise.promise();
        String sql = "SELECT u.id, u.username, u.full_name, u.email, r.id AS role_id, r.name AS role_name " +
                     "FROM users u LEFT JOIN roles r ON u.role_id = r.id ORDER BY u.id";

        client.query(sql).execute(ar -> {
            if (ar.succeeded()) {
                List<JsonObject> users = new ArrayList<>();
                for (Row row : ar.result()) {
                    JsonObject userJson = new JsonObject()
                            .put("id", row.getInteger("id"))
                            .put("username", row.getString("username"))
                            .put("fullName", row.getString("full_name"))
                            .put("email", row.getString("email"))
                            .put("role", new JsonObject()
                                    .put("id", row.getInteger("role_id"))
                                    .put("name", row.getString("role_name")));
                    users.add(userJson);
                }
                promise.complete(users);
            } else promise.fail(ar.cause());
        });

        return promise.future();
    }

    // Delete user
    public Future<Void> deleteUser(int userId) {
        Promise<Void> promise = Promise.promise();
        String sql = "DELETE FROM users WHERE id = $1";
        client.preparedQuery(sql).execute(Tuple.of(userId), ar -> {
            if (ar.succeeded()) promise.complete();
            else promise.fail(ar.cause());
        });
        return promise.future();
    }

    // Search users
    public Future<List<JsonObject>> searchUsers(String term) {
        Promise<List<JsonObject>> promise = Promise.promise();
        String sql = "SELECT u.id, u.username, u.full_name, u.email, r.id AS role_id, r.name AS role_name " +
                     "FROM users u LEFT JOIN roles r ON u.role_id = r.id " +
                     "WHERE u.username ILIKE $1 OR u.email ILIKE $1 ORDER BY u.id";

        client.preparedQuery(sql).execute(Tuple.of("%" + term + "%"), ar -> {
            if (ar.succeeded()) {
                List<JsonObject> users = new ArrayList<>();
                for (Row row : ar.result()) {
                    JsonObject userJson = new JsonObject()
                            .put("id", row.getInteger("id"))
                            .put("username", row.getString("username"))
                            .put("fullName", row.getString("full_name"))
                            .put("email", row.getString("email"))
                            .put("role", new JsonObject()
                                    .put("id", row.getInteger("role_id"))
                                    .put("name", row.getString("role_name")));
                    users.add(userJson);
                }
                promise.complete(users);
            } else promise.fail(ar.cause());
        });

        return promise.future();
    }
}
