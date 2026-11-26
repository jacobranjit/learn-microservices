package com.mc.oletrex.order.service;

import com.mc.oletrex.order.dto.CursorPage;
import com.mc.oletrex.order.dto.OrderRequest;
import com.mc.oletrex.order.dto.OrderResponse;

public interface OrderService {

    OrderResponse createOrder(OrderRequest request);

    /** @throws com.mc.oletrex.order.exception.OrderNotFoundException if no order has this number */
    OrderResponse getOrder(String orderNumber);

    /**
     * One page of orders with their line items, ordered by creation (id).
     *
     * @param cursor null/blank for the first page, otherwise the nextCursor of the previous page
     * @param limit  page size
     * @throws com.mc.oletrex.order.exception.InvalidCursorException if the cursor cannot be decoded
     */
    CursorPage<OrderResponse> getOrders(String cursor, int limit);

    /** @throws com.mc.oletrex.order.exception.OrderNotFoundException if no order has this number */
    void deleteOrder(String orderNumber);
}
