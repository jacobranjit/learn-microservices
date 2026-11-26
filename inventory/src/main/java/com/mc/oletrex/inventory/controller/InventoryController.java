package com.mc.oletrex.inventory.controller;

import com.mc.oletrex.inventory.dto.CursorPage;
import com.mc.oletrex.inventory.dto.InventoryRequest;
import com.mc.oletrex.inventory.dto.InventoryResponse;
import com.mc.oletrex.inventory.dto.InventoryUpdateRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@Tag(name = "Inventory", description = "Create, read, update and delete stock levels per SKU")
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "Create an inventory record for a SKU",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Inventory created; Location header points to it"),
                    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "409", description = "This SKU already has an inventory record", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping
    public ResponseEntity<InventoryResponse> createInventory(@Valid @RequestBody InventoryRequest request) {
        InventoryResponse created = inventoryService.createInventory(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{skuCode}")
                .buildAndExpand(created.skuCode())
                .toUri();
        return ResponseEntity.created(location).body(created);
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

    @Operation(summary = "Set the stock level for a SKU",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inventory updated"),
                    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "404", description = "No inventory record for this SKU", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PutMapping("/{skuCode}")
    public InventoryResponse updateInventory(@PathVariable String skuCode,
                                             @Valid @RequestBody InventoryUpdateRequest request) {
        return inventoryService.updateInventory(skuCode, request);
    }

    @Operation(summary = "Delete the inventory record for a SKU",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Inventory deleted"),
                    @ApiResponse(responseCode = "404", description = "No inventory record for this SKU", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @DeleteMapping("/{skuCode}")
    public ResponseEntity<Void> deleteInventory(@PathVariable String skuCode) {
        inventoryService.deleteInventory(skuCode);
        return ResponseEntity.noContent().build();
    }
}
