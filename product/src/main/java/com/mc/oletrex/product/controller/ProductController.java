package com.mc.oletrex.product.controller;

import com.mc.oletrex.product.dto.CursorPage;
import com.mc.oletrex.product.dto.ProductRequest;
import com.mc.oletrex.product.dto.ProductResponse;
import com.mc.oletrex.product.dto.ProductUpdateRequest;
import com.mc.oletrex.product.service.ProductService;
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
@Tag(name = "Products", description = "Create, read, update and delete products")
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Create a product (optionally with details)",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Product created; Location header points to it"),
                    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "409", description = "A product with this SKU already exists", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.createProduct(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{skuCode}")
                .buildAndExpand(created.skuCode())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Get a product by SKU, including details",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Product found"),
                    @ApiResponse(responseCode = "404", description = "Product not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping("/{skuCode}")
    public ProductResponse getProduct(@PathVariable String skuCode) {
        return productService.getProduct(skuCode);
    }

    @Operation(summary = "List products with details, one page at a time (cursor pagination)",
            description = "Omit cursor for the first page. Then pass the nextCursor from the previous "
                    + "response until hasMore is false. Products are ordered by creation.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "One page of products with their details"),
                    @ApiResponse(responseCode = "400", description = "Invalid cursor or limit", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @GetMapping
    public CursorPage<ProductResponse> getProducts(
            @Parameter(description = "nextCursor from the previous page; omit for the first page")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Page size, 1-100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return productService.getProducts(cursor, limit);
    }

    @Operation(summary = "Replace a product (SKU cannot change)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Product updated"),
                    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "404", description = "Product not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @PutMapping("/{skuCode}")
    public ProductResponse updateProduct(@PathVariable String skuCode,
                                         @Valid @RequestBody ProductUpdateRequest request) {
        return productService.updateProduct(skuCode, request);
    }

    @Operation(summary = "Delete a product and its details",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Product deleted"),
                    @ApiResponse(responseCode = "404", description = "Product not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
            })
    @DeleteMapping("/{skuCode}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String skuCode) {
        productService.deleteProduct(skuCode);
        return ResponseEntity.noContent().build();
    }
}
