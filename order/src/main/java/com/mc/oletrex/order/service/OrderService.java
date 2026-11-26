package com.mc.oletrex.order.service;

import com.mc.oletrex.order.dto.OrderRequest;
import com.mc.oletrex.order.dto.OrderResponse;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(OrderRequest request);

    /** @throws com.mc.oletrex.order.exception.OrderNotFoundException if no order has this number */
    OrderResponse getOrder(String orderNumber);

    List<OrderResponse> getAllOrders();

    /** @throws com.mc.oletrex.order.exception.OrderNotFoundException if no order has this number */
    void deleteOrder(String orderNumber);
}
