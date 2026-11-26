package com.mc.oletrex.inventory.controller;

import com.mc.oletrex.inventory.dto.CursorPage;
import com.mc.oletrex.inventory.dto.InventoryRecordResponse;
import com.mc.oletrex.inventory.dto.InventoryRequest;
import com.mc.oletrex.inventory.dto.InventoryResponse;
import com.mc.oletrex.inventory.dto.InventoryUpsertResult;
import com.mc.oletrex.inventory.dto.StockDeductionRequest;
import com.mc.oletrex.inventory.dto.StockDeductionResponse;
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

@RestController
@Tag(name = "Inventory", description = "Stock per SKU and location")
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "Create or update the stock of a SKU at a location",
            description = "Creates the record if the SKU has none at this location (201), otherwise sets its "
                    + "quantity (200). The location must exist and be active. The first stock for a SKU "
                    + "(at any location) is checked against the product service; later saves are not.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "New record created; Location header points to the SKU"),
                    @ApiResponse(responseCode = "200", description = "Existing record updated"),
                    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "422", description = "Unknown or inactive location, or no such product in the product service", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "503", description = "First stock for a SKU, and the product service is unavailable", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping
    public ResponseEntity<InventoryRecordResponse> saveInventory(@Valid @RequestBody InventoryRequest request) {
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

    @Operation(summary = "Deduct stock for an order from a single location, all or nothing",
            description = "Used by the order service. Picks the active location with the lowest priority number "
                    + "that has enough of every item, and deducts everything from it. Quantities for the same "
                    + "SKU are added together.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Stock deducted; returns the supplying location and what is left there"),
                    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "409", description = "Insufficient Stock (not enough across active locations) or No Single Location (enough in total, but split); nothing was deducted", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping("/deductions")
    public StockDeductionResponse deductStock(@Valid @RequestBody StockDeductionRequest request) {
        return inventoryService.deductStock(request);
    }

    @Operation(summary = "Get a SKU's stock: total plus per-location breakdown",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inventory found"),
                    @ApiResponse(responseCode = "404", description = "No inventory records for this SKU", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping("/{skuCode}")
    public InventoryResponse getInventory(@PathVariable String skuCode) {
        return inventoryService.getInventory(skuCode);
    }

    @Operation(summary = "List stock records (one per SKU and location), one page at a time",
            description = "Omit cursor for the first page. Then pass the nextCursor from the previous "
                    + "response until hasMore is false. Ordered by creation.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "One page of stock records"),
                    @ApiResponse(responseCode = "400", description = "Invalid cursor or limit", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping
    public CursorPage<InventoryRecordResponse> getInventoryPage(
            @Parameter(description = "nextCursor from the previous page; omit for the first page")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Page size, 1-100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @Parameter(description = "Only records of this SKU")
            @RequestParam(required = false) String skuCode,
            @Parameter(description = "Only records at this location")
            @RequestParam(required = false) String locationCode) {
        return inventoryService.getInventoryPage(cursor, limit, skuCode, locationCode);
    }
}
