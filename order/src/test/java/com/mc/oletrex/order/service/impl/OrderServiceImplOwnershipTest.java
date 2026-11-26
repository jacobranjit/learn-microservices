package com.mc.oletrex.order.service.impl;

import com.mc.oletrex.order.client.InventoryClient;
import com.mc.oletrex.order.client.ProductClient;
import com.mc.oletrex.order.client.ProductInfo;
import com.mc.oletrex.order.dto.AddressDto;
import com.mc.oletrex.order.dto.CursorPage;
import com.mc.oletrex.order.dto.OrderLineItemRequest;
import com.mc.oletrex.order.dto.OrderPlacementResult;
import com.mc.oletrex.order.dto.OrderRequest;
import com.mc.oletrex.order.dto.OrderResponse;
import com.mc.oletrex.order.exception.OrderNotFoundException;
import com.mc.oletrex.order.model.Order;
import com.mc.oletrex.order.model.OrderStatus;
import com.mc.oletrex.order.repository.OrderRepository;
import com.mc.oletrex.order.service.Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Limit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplOwnershipTest {

    @Mock OrderRepository repo;
    @Mock ProductClient productClient;
    @Mock InventoryClient inventoryClient;

    OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(repo, productClient, inventoryClient);
    }

    private Order order(long id, long customerId) {
        return Order.builder().id(id).orderNumber("ORD-1").status(OrderStatus.PLACED)
                .createdAt(java.time.Instant.now()).customerId(customerId).build();
    }

    @Test
    void userGetOrderIsScopedToCustomer() {
        when(repo.findByOrderNumberAndCustomerId("ORD-1", 42L)).thenReturn(Optional.of(order(1, 42)));
        assertThat(service.getOrder("ORD-1", new Caller(42L, false)).customerId()).isEqualTo(42L);
        verify(repo, never()).findByOrderNumber(any());
    }

    @Test
    void otherCustomersOrderIsNotFound() {
        when(repo.findByOrderNumberAndCustomerId("ORD-1", 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getOrder("ORD-1", new Caller(7L, false)))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void adminGetOrderIsUnscoped() {
        when(repo.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order(1, 42)));
        assertThat(service.getOrder("ORD-1", new Caller(null, true)).orderNumber()).isEqualTo("ORD-1");
        verify(repo, never()).findByOrderNumberAndCustomerId(any(), any());
    }

    @Test
    void userListUsesCustomerFilteredQuery() {
        when(repo.findByCustomerIdAndIdGreaterThanOrderByIdAsc(eq(42L), eq(0L), any(Limit.class)))
                .thenReturn(List.of(order(1, 42)));
        CursorPage<OrderResponse> page = service.getOrders(null, 10, new Caller(42L, false));
        assertThat(page.items()).hasSize(1);
        verify(repo).findAllWithLineItemsByIdIn(anyCollection());
        verify(repo, never()).findByIdGreaterThanOrderByIdAsc(anyLong(), any());
    }

    @Test
    void adminListIsUnfiltered() {
        when(repo.findByIdGreaterThanOrderByIdAsc(eq(0L), any(Limit.class))).thenReturn(List.of(order(1, 42)));
        assertThat(service.getOrders(null, 10, new Caller(null, true)).items()).hasSize(1);
        verify(repo, never()).findByCustomerIdAndIdGreaterThanOrderByIdAsc(any(), anyLong(), any());
    }

    @Test
    void createOrderStoresCustomerAndAddresses() {
        AddressDto ship = new AddressDto("1 Main St", null, "Austin", "TX", "73301", "US");
        AddressDto bill = new AddressDto("9 Bill Rd", "Apt 2", "Dallas", "TX", "75001", "US");
        when(repo.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(productClient.findProduct("SKU-1"))
                .thenReturn(Optional.of(new ProductInfo("SKU-1", "Widget", new BigDecimal("5.00"), "USD", true)));
        when(inventoryClient.deductStock(any())).thenReturn("LOC-1");

        OrderPlacementResult result = service.createOrder(
                new OrderRequest(ship, bill, List.of(new OrderLineItemRequest("SKU-1", 1))), 42L);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(repo, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Order saved = captor.getAllValues().getFirst();
        assertThat(saved.getCustomerId()).isEqualTo(42L);
        assertThat(saved.getShippingAddress().getLine1()).isEqualTo("1 Main St");
        assertThat(saved.getBillingAddress().getLine2()).isEqualTo("Apt 2");
        assertThat(result.order().customerId()).isEqualTo(42L);
        assertThat(result.order().shippingAddress()).isEqualTo(ship);
        assertThat(result.order().billingAddress()).isEqualTo(bill);
    }
}
