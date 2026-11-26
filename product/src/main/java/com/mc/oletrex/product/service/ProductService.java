package com.mc.oletrex.product.service;

import com.mc.oletrex.product.dto.CursorPage;
import com.mc.oletrex.product.dto.ProductRequest;
import com.mc.oletrex.product.dto.ProductResponse;
import com.mc.oletrex.product.dto.ProductUpdateRequest;

public interface ProductService {

    /** @throws com.mc.oletrex.product.exception.ProductAlreadyExistsException if the SKU is taken */
    ProductResponse createProduct(ProductRequest request);

    /** @throws com.mc.oletrex.product.exception.ProductNotFoundException if no product has this SKU */
    ProductResponse getProduct(String skuCode);

    /**
     * One page of products with their details, ordered by creation (id).
     *
     * @param cursor null/blank for the first page, otherwise the nextCursor of the previous page
     * @param limit  page size
     * @throws com.mc.oletrex.product.exception.InvalidCursorException if the cursor cannot be decoded
     */
    CursorPage<ProductResponse> getProducts(String cursor, int limit);

    /** @throws com.mc.oletrex.product.exception.ProductNotFoundException if no product has this SKU */
    ProductResponse updateProduct(String skuCode, ProductUpdateRequest request);

    /** @throws com.mc.oletrex.product.exception.ProductNotFoundException if no product has this SKU */
    void deleteProduct(String skuCode);
}
