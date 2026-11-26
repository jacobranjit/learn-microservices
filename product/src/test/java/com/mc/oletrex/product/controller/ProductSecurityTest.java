package com.mc.oletrex.product.controller;

import com.mc.oletrex.product.dto.CursorPage;
import com.mc.oletrex.product.dto.ProductResponse;
import com.mc.oletrex.product.security.JwtRolesConverter;
import com.mc.oletrex.product.security.ProblemDetailAuthHandlers;
import com.mc.oletrex.product.security.SecurityConfig;
import com.mc.oletrex.product.security.TestTokens;
import com.mc.oletrex.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtRolesConverter.class, ProblemDetailAuthHandlers.class})
class ProductSecurityTest {

    private static final String CREATE = """
            {"skuCode":"SKU-1","name":"Widget","price":9.99,"currency":"USD"}""";
    private static final String UPDATE = """
            {"name":"Widget","price":9.99,"currency":"USD","active":true}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProductService productService;

    private ProductResponse sample() {
        return new ProductResponse("SKU-1", "Widget", null, new BigDecimal("9.99"), "USD", true, null, null, null);
    }

    private List<RequestPostProcessor> readers() {
        return List.of(TestTokens.user(42), TestTokens.admin(), TestTokens.service());
    }

    @Test
    void readsAllowedForUserAdminAndService() throws Exception {
        when(productService.getProduct("SKU-1")).thenReturn(sample());
        when(productService.getProducts(isNull(), anyInt())).thenReturn(new CursorPage<>(List.of(sample()), null, false));
        for (RequestPostProcessor token : readers()) {
            mvc.perform(get("/api/products").with(token)).andExpect(status().isOk());
            mvc.perform(get("/api/products/SKU-1").with(token)).andExpect(status().isOk());
        }
    }

    @Test
    void readWithoutTokenIs401ProblemDetail() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
        verifyNoInteractions(productService);
    }

    @Test
    void tokenWithoutRolesIs403() throws Exception {
        mvc.perform(get("/api/products").with(TestTokens.noRoles()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void adminCanCreate() throws Exception {
        when(productService.createProduct(any())).thenReturn(sample());
        mvc.perform(post("/api/products").with(TestTokens.admin()).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isCreated());
    }

    @Test
    void userAndServiceCannotCreate() throws Exception {
        for (RequestPostProcessor token : List.of(TestTokens.user(42), TestTokens.service())) {
            mvc.perform(post("/api/products").with(token).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Forbidden"));
        }
        verifyNoInteractions(productService);
    }

    @Test
    void userAndServiceCannotUpdateOrDelete() throws Exception {
        for (RequestPostProcessor token : List.of(TestTokens.user(42), TestTokens.service())) {
            mvc.perform(put("/api/products/SKU-1").with(token).contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/products/SKU-1").with(token)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(productService);
    }

    @Test
    void adminCanUpdateAndDelete() throws Exception {
        when(productService.updateProduct(anyString(), any())).thenReturn(sample());
        mvc.perform(put("/api/products/SKU-1").with(TestTokens.admin()).contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/products/SKU-1").with(TestTokens.admin())).andExpect(status().isNoContent());
    }
}
