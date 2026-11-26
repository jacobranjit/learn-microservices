package com.mc.oletrex.order.controller;

import com.mc.oletrex.order.dto.CursorPage;
import com.mc.oletrex.order.dto.OrderPlacementResult;
import com.mc.oletrex.order.dto.OrderPlacementResult.Outcome;
import com.mc.oletrex.order.dto.OrderResponse;
import com.mc.oletrex.order.security.JwtRolesConverter;
import com.mc.oletrex.order.security.ProblemDetailAuthHandlers;
import com.mc.oletrex.order.security.SecurityConfig;
import com.mc.oletrex.order.security.TestTokens;
import com.mc.oletrex.order.service.Caller;
import com.mc.oletrex.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtRolesConverter.class, ProblemDetailAuthHandlers.class})
class OrderControllerSecurityTest {

    private static final String ADDR = """
            {"line1":"1 Main St","city":"Austin","state":"TX","postalCode":"73301","country":"US"}""";
    private static final String VALID = """
            {"shippingAddress":%s,"billingAddress":%s,"lineItems":[{"skuCode":"SKU-1","quantity":1}]}"""
            .formatted(ADDR, ADDR);
    private static final String NO_BILLING = """
            {"shippingAddress":%s,"lineItems":[{"skuCode":"SKU-1","quantity":1}]}""".formatted(ADDR);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService orderService;

    private OrderResponse order() {
        return new OrderResponse("ORD-1", null, null, null, Instant.now(), 42L, null, null, List.of());
    }

    @Test
    void userCreatesOrderForOwnCustomerId() throws Exception {
        when(orderService.createOrder(any(), anyLong()))
                .thenReturn(new OrderPlacementResult(order(), Outcome.CONFIRMED));
        mvc.perform(post("/api/orders").with(TestTokens.user(42)).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated());
        verify(orderService).createOrder(any(), eq(42L));
    }

    @Test
    void adminCannotCreateOrder() throws Exception {
        mvc.perform(post("/api/orders").with(TestTokens.admin()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void userWithoutCustomerIdGets403() throws Exception {
        mvc.perform(post("/api/orders").with(TestTokens.userWithoutCustomerId())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void missingBillingAddressIs400() throws Exception {
        mvc.perform(post("/api/orders").with(TestTokens.user(42)).contentType(MediaType.APPLICATION_JSON).content(NO_BILLING))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    void userGetsOwnScopedCaller() throws Exception {
        when(orderService.getOrder(any(), any())).thenReturn(order());
        mvc.perform(get("/api/orders/ORD-1").with(TestTokens.user(42))).andExpect(status().isOk());
        verify(orderService).getOrder("ORD-1", new Caller(42L, false));
    }

    @Test
    void adminGetsUnscopedCaller() throws Exception {
        when(orderService.getOrder(any(), any())).thenReturn(order());
        mvc.perform(get("/api/orders/ORD-1").with(TestTokens.admin())).andExpect(status().isOk());
        verify(orderService).getOrder("ORD-1", new Caller(null, true));
    }

    @Test
    void listUsesCaller() throws Exception {
        when(orderService.getOrders(any(), anyInt(), any())).thenReturn(new CursorPage<>(List.of(), null, false));
        mvc.perform(get("/api/orders").with(TestTokens.user(42))).andExpect(status().isOk());
        verify(orderService).getOrders(isNull(), eq(20), eq(new Caller(42L, false)));
        mvc.perform(get("/api/orders").with(TestTokens.admin())).andExpect(status().isOk());
        verify(orderService).getOrders(isNull(), eq(20), eq(new Caller(null, true)));
    }

    @Test
    void userWithoutCustomerIdCannotRead() throws Exception {
        mvc.perform(get("/api/orders/ORD-1").with(TestTokens.userWithoutCustomerId())).andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void adminDeletes204UserDeletes403() throws Exception {
        mvc.perform(delete("/api/orders/ORD-1").with(TestTokens.admin())).andExpect(status().isNoContent());
        verify(orderService).deleteOrder("ORD-1");
        mvc.perform(delete("/api/orders/ORD-1").with(TestTokens.user(42))).andExpect(status().isForbidden());
    }

    @Test
    void serviceTokenCannotReadOrders() throws Exception {
        mvc.perform(get("/api/orders").with(TestTokens.service())).andExpect(status().isForbidden());
    }

    @Test
    void noTokenIs401Problem() throws Exception {
        mvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"));
    }
}
