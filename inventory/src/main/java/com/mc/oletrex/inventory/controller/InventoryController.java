package com.mc.oletrex.inventory.controller;

import com.mc.oletrex.inventory.dto.CursorPage;
import com.mc.oletrex.inventory.dto.InventoryRequest;
import com.mc.oletrex.inventory.dto.InventoryResponse;
import com.mc.oletrex.inventory.dto.InventoryUpsertResult;
import com.mc.oletrex.inventory.dto.StockDeductionRequest;
import com.mc.oletrex.inventory.service.InventoryService;
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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@Tag(name = "Inventory", description = "Create, read, update and delete stock levels per SKU")
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "Create or update the stock level of a SKU",
            description = "Creates the inventory record if the SKU has none (201), otherwise sets its "
                    + "quantity (200). Creating first checks that the product exists in the product service "
                    + "(inactive products are allowed); updating an existing record does not call it.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "New record created; Location header points to it"),
                    @ApiResponse(responseCode = "200", description = "Existing record updated"),
                    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "422", description = "New SKU, but no such product in the product service", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "503", description = "New SKU, and the product service is unavailable", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping
    public ResponseEntity<InventoryResponse> saveInventory(@Valid @RequestBody InventoryRequest request) {
        InventoryUpsertResult result = inventoryService.saveInventory(request);
        if (!result.created()) {
            return ResponseEntity.ok(result.inventory());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{skuCode}")
                .buildAndExpand(result.inventory().skuCode())
                .toUri();
        return ResponseEntity.created(location).body(result.inventory());
    }

    @Operation(summary = "Deduct stock for several SKUs, all or nothing",
            description = "Used by the order service when an order is placed. Either every item is deducted "
                    + "or none is. Quantities for the same SKU are added together.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Stock deducted; returns the new stock levels"),
                    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "409", description = "Not enough stock (or no record) for at least one SKU; nothing was deducted", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping("/deductions")
    public List<InventoryResponse> deductStock(@Valid @RequestBody StockDeductionRequest request) {
        return inventoryService.deductStock(request);
    }

    @Operation(summary = "Get the stock level for a SKU",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inventory found"),
                    @ApiResponse(responseCode = "404", description = "No inventory record for this SKU", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping("/{skuCode}")
    public InventoryResponse getInventory(@PathVariable String skuCode) {
        return inventoryService.getInventory(skuCode);
    }

    @Operation(summary = "List inventory records, one page at a time (cursor pagination)",
            description = "Omit cursor for the first page. Then pass the nextCursor from the previous "
                    + "response until hasMore is false. Ordered by creation.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "One page of inventory records"),
                    @ApiResponse(responseCode = "400", description = "Invalid cursor or limit", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping
    public CursorPage<InventoryResponse> getInventoryPage(
            @Parameter(description = "nextCursor from the previous page; omit for the first page")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Page size, 1-100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return inventoryService.getInventoryPage(cursor, limit);
    }
}
