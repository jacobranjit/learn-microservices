package com.mc.oletrex.inventory.controller;

import com.mc.oletrex.inventory.dto.CursorPage;
import com.mc.oletrex.inventory.dto.InventoryRecordResponse;
import com.mc.oletrex.inventory.dto.InventoryResponse;
import com.mc.oletrex.inventory.dto.InventoryUpsertResult;
import com.mc.oletrex.inventory.dto.LocationResponse;
import com.mc.oletrex.inventory.dto.StockDeductionResponse;
import com.mc.oletrex.inventory.security.JwtRolesConverter;
import com.mc.oletrex.inventory.security.ProblemDetailAuthHandlers;
import com.mc.oletrex.inventory.security.SecurityConfig;
import com.mc.oletrex.inventory.security.TestTokens;
import com.mc.oletrex.inventory.service.InventoryService;
import com.mc.oletrex.inventory.service.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({InventoryController.class, LocationController.class})
@Import({SecurityConfig.class, JwtRolesConverter.class, ProblemDetailAuthHandlers.class})
class InventorySecurityTest {

    private static final String UPSERT = """
            {"skuCode":"SKU-1","locationCode":"BLR-WH1","quantity":5}""";
    private static final String DEDUCT = """
            {"items":[{"skuCode":"SKU-1","quantity":1}]}""";
    private static final String LOC_CREATE = """
            {"code":"BLR-WH1","name":"Warehouse","type":"WAREHOUSE","country":"IN","priority":1}""";
    private static final String LOC_UPDATE = """
            {"name":"Warehouse","type":"WAREHOUSE","country":"IN","priority":1,"active":true}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @MockitoBean
    LocationService locationService;

    private InventoryRecordResponse record() {
        return new InventoryRecordResponse("SKU-1", "BLR-WH1", 5, true);
    }

    private LocationResponse location() {
        return new LocationResponse("BLR-WH1", "Warehouse", null, null, null, null, null, "IN", 1, true, null, null);
    }

    @Test
    void adminCanUpsertListAndGetInventory() throws Exception {
        when(inventoryService.saveInventory(any())).thenReturn(new InventoryUpsertResult(record(), false));
        when(inventoryService.getInventory("SKU-1")).thenReturn(new InventoryResponse("SKU-1", 5, true, List.of()));
        when(inventoryService.getInventoryPage(isNull(), anyInt(), isNull(), isNull()))
                .thenReturn(new CursorPage<>(List.of(record()), null, false));

        mvc.perform(post("/api/inventory").with(TestTokens.admin())
                .contentType(MediaType.APPLICATION_JSON).content(UPSERT)).andExpect(status().isOk());
        mvc.perform(get("/api/inventory").with(TestTokens.admin())).andExpect(status().isOk());
        mvc.perform(get("/api/inventory/SKU-1").with(TestTokens.admin())).andExpect(status().isOk());
    }

    @Test
    void userCannotTouchInventory() throws Exception {
        mvc.perform(post("/api/inventory").with(TestTokens.user(42))
                .contentType(MediaType.APPLICATION_JSON).content(UPSERT)).andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory").with(TestTokens.user(42))).andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory/SKU-1").with(TestTokens.user(42))).andExpect(status().isForbidden());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void serviceCanDeductButNotReadOrUpsert() throws Exception {
        when(inventoryService.deductStock(any())).thenReturn(new StockDeductionResponse("BLR-WH1", List.of(record())));

        mvc.perform(post("/api/inventory/deductions").with(TestTokens.service())
                .contentType(MediaType.APPLICATION_JSON).content(DEDUCT)).andExpect(status().isOk());
        mvc.perform(get("/api/inventory/SKU-1").with(TestTokens.service())).andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory").with(TestTokens.service())
                .contentType(MediaType.APPLICATION_JSON).content(UPSERT)).andExpect(status().isForbidden());
    }

    @Test
    void adminAndUserCannotDeduct() throws Exception {
        mvc.perform(post("/api/inventory/deductions").with(TestTokens.admin())
                .contentType(MediaType.APPLICATION_JSON).content(DEDUCT))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Forbidden"));
        mvc.perform(post("/api/inventory/deductions").with(TestTokens.user(42))
                .contentType(MediaType.APPLICATION_JSON).content(DEDUCT)).andExpect(status().isForbidden());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void adminCanManageLocations() throws Exception {
        when(locationService.createLocation(any())).thenReturn(location());
        when(locationService.getLocation("BLR-WH1")).thenReturn(location());
        when(locationService.updateLocation(anyString(), any())).thenReturn(location());
        when(locationService.getLocations(isNull(), anyInt())).thenReturn(new CursorPage<>(List.of(location()), null, false));

        mvc.perform(get("/api/locations").with(TestTokens.admin())).andExpect(status().isOk());
        mvc.perform(get("/api/locations/BLR-WH1").with(TestTokens.admin())).andExpect(status().isOk());
        mvc.perform(post("/api/locations").with(TestTokens.admin())
                .contentType(MediaType.APPLICATION_JSON).content(LOC_CREATE)).andExpect(status().isCreated());
        mvc.perform(put("/api/locations/BLR-WH1").with(TestTokens.admin())
                .contentType(MediaType.APPLICATION_JSON).content(LOC_UPDATE)).andExpect(status().isOk());
    }

    @Test
    void serviceAndUserCannotUseLocations() throws Exception {
        for (var token : List.of(TestTokens.service(), TestTokens.user(42))) {
            mvc.perform(get("/api/locations").with(token)).andExpect(status().isForbidden());
            mvc.perform(post("/api/locations").with(token)
                    .contentType(MediaType.APPLICATION_JSON).content(LOC_CREATE)).andExpect(status().isForbidden());
            mvc.perform(put("/api/locations/BLR-WH1").with(token)
                    .contentType(MediaType.APPLICATION_JSON).content(LOC_UPDATE)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(locationService);
    }

    @Test
    void noTokenIs401ProblemDetail() throws Exception {
        mvc.perform(get("/api/inventory"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
        mvc.perform(post("/api/inventory/deductions").contentType(MediaType.APPLICATION_JSON).content(DEDUCT))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/locations")).andExpect(status().isUnauthorized());
    }

    @Test
    void noRolesTokenIsForbidden() throws Exception {
        mvc.perform(get("/api/inventory").with(TestTokens.noRoles())).andExpect(status().isForbidden());
    }
}
