package com.mc.oletrex.product.service.impl;

import com.mc.oletrex.product.dto.CursorPage;
import com.mc.oletrex.product.dto.ProductAttributeDto;
import com.mc.oletrex.product.dto.ProductDetailsDto;
import com.mc.oletrex.product.dto.ProductImageDto;
import com.mc.oletrex.product.dto.ProductRequest;
import com.mc.oletrex.product.dto.ProductResponse;
import com.mc.oletrex.product.dto.ProductUpdateRequest;
import com.mc.oletrex.product.exception.ProductAlreadyExistsException;
import com.mc.oletrex.product.exception.ProductNotFoundException;
import com.mc.oletrex.product.model.Product;
import com.mc.oletrex.product.model.ProductAttribute;
import com.mc.oletrex.product.model.ProductDetails;
import com.mc.oletrex.product.model.ProductImage;
import com.mc.oletrex.product.repository.ProductDetailsRepository;
import com.mc.oletrex.product.repository.ProductRepository;
import com.mc.oletrex.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductDetailsRepository productDetailsRepository;

    @Override
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySkuCode(request.skuCode())) {
            throw new ProductAlreadyExistsException(request.skuCode());
        }
        Product product = productRepository.saveAndFlush(Product.builder()
                .skuCode(request.skuCode())
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .currency(request.currency())
                .active(request.active() == null || request.active())
                .build());

        ProductDetails details = null;
        if (request.details() != null) {
            details = new ProductDetails();
            details.setProduct(product);
            applyDetails(details, request.details());
            details = productDetailsRepository.save(details);
        }
        return toResponse(product, details);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProduct(String skuCode) {
        Product product = findProduct(skuCode);
        return toResponse(product, productDetailsRepository.findById(product.getId()).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ProductResponse> getProducts(String cursor, int limit) {
        long afterId = ProductCursor.decode(cursor);

        // Ask for one extra row: if it comes back, there is a next page.
        List<Product> rows = productRepository.findByIdGreaterThanOrderByIdAsc(afterId, Limit.of(limit + 1));
        boolean hasMore = rows.size() > limit;
        List<Product> page = hasMore ? rows.subList(0, limit) : rows;
        if (page.isEmpty()) {
            return new CursorPage<>(List.of(), null, false);
        }

        // 3 queries per page in total, regardless of page size:
        // products (above), details + attributes, details + images.
        List<Long> ids = page.stream().map(Product::getId).toList();
        Map<Long, ProductDetails> detailsById = productDetailsRepository.findAllWithAttributesByIdIn(ids).stream()
                .collect(Collectors.toMap(ProductDetails::getId, Function.identity()));
        productDetailsRepository.findAllWithImagesByIdIn(ids);

        List<ProductResponse> items = page.stream()
                .map(p -> toResponse(p, detailsById.get(p.getId())))
                .toList();
        String nextCursor = hasMore ? ProductCursor.encode(page.getLast().getId()) : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    @Override
    public ProductResponse updateProduct(String skuCode, ProductUpdateRequest request) {
        Product product = findProduct(skuCode);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCurrency(request.currency());
        product.setActive(request.active());

        // PUT replaces the whole product: no "details" in the body means no details.
        ProductDetails details = productDetailsRepository.findById(product.getId()).orElse(null);
        if (request.details() == null) {
            if (details != null) {
                productDetailsRepository.delete(details);
                details = null;
            }
        } else {
            if (details == null) {
                details = new ProductDetails();
                details.setProduct(product);
            }
            applyDetails(details, request.details());
            details = productDetailsRepository.save(details);
        }
        // Flush so updatedAt reflects this change in the response.
        productRepository.flush();
        return toResponse(product, details);
    }

    @Override
    public void deleteProduct(String skuCode) {
        // Details, attributes and images are removed by the database (ON DELETE CASCADE).
        productRepository.delete(findProduct(skuCode));
    }

    private Product findProduct(String skuCode) {
        return productRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new ProductNotFoundException(skuCode));
    }

    private void applyDetails(ProductDetails details, ProductDetailsDto dto) {
        details.setBrand(dto.brand());
        details.setCategory(dto.category());
        details.setWeight(dto.weight());
        details.setWeightUnit(dto.weightUnit());
        details.setLength(dto.length());
        details.setWidth(dto.width());
        details.setHeight(dto.height());
        details.setDimensionUnit(dto.dimensionUnit());
        details.setColor(dto.color());
        details.setSize(dto.size());
        details.setMaterial(dto.material());
        // Replace list contents in place so Hibernate keeps tracking the same collections.
        details.getAttributes().clear();
        if (dto.attributes() != null) {
            dto.attributes().forEach(a -> details.getAttributes().add(new ProductAttribute(a.name(), a.value())));
        }
        details.getImages().clear();
        if (dto.images() != null) {
            dto.images().forEach(i -> details.getImages().add(new ProductImage(i.url(), i.altText())));
        }
    }

    private ProductResponse toResponse(Product p, ProductDetails d) {
        return new ProductResponse(p.getSkuCode(), p.getName(), p.getDescription(), p.getPrice(), p.getCurrency(),
                p.isActive(), p.getCreatedAt(), p.getUpdatedAt(), d == null ? null : toDto(d));
    }

    private ProductDetailsDto toDto(ProductDetails d) {
        return new ProductDetailsDto(d.getBrand(), d.getCategory(), d.getWeight(), d.getWeightUnit(),
                d.getLength(), d.getWidth(), d.getHeight(), d.getDimensionUnit(),
                d.getColor(), d.getSize(), d.getMaterial(),
                d.getAttributes().stream().map(a -> new ProductAttributeDto(a.getName(), a.getValue())).toList(),
                d.getImages().stream().map(i -> new ProductImageDto(i.getUrl(), i.getAltText())).toList());
    }
}
