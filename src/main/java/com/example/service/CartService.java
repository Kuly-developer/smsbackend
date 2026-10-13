package com.example.service;

import java.util.ArrayList;
import java.util.List;

import com.example.model.Cart;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class CartService {

    private final PgPool client;

    public CartService(PgPool client) {
        this.client = client;
    }

    // Add or update cart item
    public Future<Void> addCartItem(Cart cart) {
        Promise<Void> promise = Promise.promise();

        String checkQuery = "SELECT id, quantity FROM cart WHERE customer_id = $1 AND product_id = $2";

        client.preparedQuery(checkQuery)
            .execute(Tuple.of(cart.getCustomerId(), cart.getProductId()))
            .compose(rows -> {
                if (rows.size() > 0) {
                    // Item exists → update quantity & price
                    Row row = rows.iterator().next();
                    Integer existingId = row.getInteger("id");
                    Integer existingQty = row.getInteger("quantity");
                    Integer newQty = existingQty + cart.getQuantity();

                    String updateQuery = "UPDATE cart SET quantity=$1, price=$2 WHERE id=$3";
                    return client.preparedQuery(updateQuery)
                            .execute(Tuple.of(newQty, cart.getPrice(), existingId))
                            .mapEmpty();
                } else {
                    // Insert new row
                    String insertQuery = "INSERT INTO cart (customer_id, product_id, quantity, name, price, image_url) " +
                                         "VALUES ($1,$2,$3,$4,$5,$6)";
                    return client.preparedQuery(insertQuery)
                            .execute(Tuple.of(
                                    cart.getCustomerId(),
                                    cart.getProductId(),
                                    cart.getQuantity(),
                                    cart.getName(),
                                    cart.getPrice(),
                                    cart.getImageUrl()
                            ))
                            .mapEmpty();
                }
            })
            .onSuccess(v -> promise.complete())
            .onFailure(promise::fail);

        return promise.future();
    }

    // Get all cart items for a customer
    public Future<List<Cart>> getCartByCustomerId(Integer customerId) {
        Promise<List<Cart>> promise = Promise.promise();
        String query = "SELECT * FROM cart WHERE customer_id=$1 ORDER BY id";

        client.preparedQuery(query)
                .execute(Tuple.of(customerId))
                .onSuccess(rows -> {
                    List<Cart> list = new ArrayList<>();
                    for (Row row : rows) {
                        Cart cart = new Cart();
                        cart.setId(row.getInteger("id"));
                        cart.setCustomerId(row.getInteger("customer_id"));
                        cart.setProductId(row.getInteger("product_id"));
                        cart.setQuantity(row.getInteger("quantity"));
                        cart.setPrice(row.getDouble("price"));
                        cart.setName(row.getString("name"));
                        cart.setImageUrl(row.getString("image_url"));
                        list.add(cart);
                    }
                    promise.complete(list);
                })
                .onFailure(promise::fail);

        return promise.future();
    }

    // Update quantity of a cart item
    public Future<Void> updateCartItem(Integer id, Integer quantity) {
        if (quantity <= 0) return deleteCartItem(id);

        Promise<Void> promise = Promise.promise();
        String sql = "UPDATE cart SET quantity=$1, price=(SELECT price*$1 FROM products WHERE id=cart.product_id) WHERE id=$2";

        client.preparedQuery(sql)
                .execute(Tuple.of(quantity, id))
                .onSuccess(res -> promise.complete())
                .onFailure(promise::fail);

        return promise.future();
    }

    // Delete a single cart item
    public Future<Void> deleteCartItem(Integer id) {
        Promise<Void> promise = Promise.promise();
        String sql = "DELETE FROM cart WHERE id=$1";

        client.preparedQuery(sql)
                .execute(Tuple.of(id))
                .onSuccess(res -> promise.complete())
                .onFailure(promise::fail);

        return promise.future();
    }

    // Clear entire cart for a customer
    public Future<Void> clearCartByCustomer(Integer customerId) {
        Promise<Void> promise = Promise.promise();
        String sql = "DELETE FROM cart WHERE customer_id=$1";

        client.preparedQuery(sql)
              .execute(Tuple.of(customerId))
              .onSuccess(res -> promise.complete())
              .onFailure(promise::fail);

        return promise.future();
    }
}
