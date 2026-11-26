package com.mc.oletrex.order.controller;

import com.mc.oletrex.order.dto.OrderRequest;
import com.mc.oletrex.order.dto.OrderResponse;
import com.mc.oletrex.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        OrderResponse created = orderService.createOrder(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{orderNumber}")
                .buildAndExpand(created.orderNumber())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse getOrder(@PathVariable String orderNumber) {
        return orderService.getOrder(orderNumber);
    }

    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }

    @DeleteMapping("/{orderNumber}")
    public ResponseEntity<Void> deleteOrder(@PathVariable String orderNumber) {
        orderService.deleteOrder(orderNumber);
        return ResponseEntity.noContent().build();
    }
}
