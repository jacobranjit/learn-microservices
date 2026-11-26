package com.mc.oletrex.order.controller;

import com.mc.oletrex.order.dto.CursorPage;
import com.mc.oletrex.order.dto.OrderPlacementResult;
import com.mc.oletrex.order.dto.OrderRequest;
import com.mc.oletrex.order.dto.OrderResponse;
import com.mc.oletrex.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@Tag(name = "Orders", description = "Create, read and delete orders")
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Place an order",
            description = "Saves the order as PLACED, fetches each product from the product service "
                    + "(must exist and be active; its current price is used), deducts stock in the inventory "
                    + "service (all items from one location, recorded as fulfilmentLocationCode), then marks the order CONFIRMED. If a step fails the order is kept as CANCELLED "
                    + "with statusReason. The order is saved in every case, so Location is always set.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Order CONFIRMED"),
                    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "422", description = "Order CANCELLED: unknown or inactive product, mixed currencies, or insufficient stock (see statusReason)"),
                    @ApiResponse(responseCode = "503", description = "Order CANCELLED: product or inventory service unavailable (see statusReason)")
            })
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        OrderPlacementResult result = orderService.createOrder(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{orderNumber}")
                .buildAndExpand(result.order().orderNumber())
                .toUri();
        HttpStatus status = switch (result.outcome()) {
            case CONFIRMED -> HttpStatus.CREATED;
            case REJECTED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status).location(location).body(result.order());
    }

    @Operation(summary = "Get an order by order number",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order found"),
                    @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping("/{orderNumber}")
    public OrderResponse getOrder(@PathVariable String orderNumber) {
        return orderService.getOrder(orderNumber);
    }

    @Operation(summary = "List orders with line items, one page at a time (cursor pagination)",
            description = "Omit cursor for the first page. Then pass the nextCursor from the previous "
                    + "response until hasMore is false. Ordered by creation.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "One page of orders"),
                    @ApiResponse(responseCode = "400", description = "Invalid cursor or limit", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping
    public CursorPage<OrderResponse> getOrders(
            @Parameter(description = "nextCursor from the previous page; omit for the first page")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Page size, 1-100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return orderService.getOrders(cursor, limit);
    }

    @Operation(summary = "Delete an order and its line items",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Order deleted"),
                    @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @DeleteMapping("/{orderNumber}")
    public ResponseEntity<Void> deleteOrder(@PathVariable String orderNumber) {
        orderService.deleteOrder(orderNumber);
        return ResponseEntity.noContent().build();
    }
}
