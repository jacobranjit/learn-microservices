package com.mc.oletrex.product.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Extended product information, one row per product.
 * Shares the product's primary key (@MapsId), so it is loaded by product id
 * and only when needed. Deleting the product deletes its details (ON DELETE CASCADE).
 */
@Entity
@Table(name = "product_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDetails {

    /** Same value as the product's id. */
    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "product_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product product;

    // --- Catalog ---
    private String brand;

    private String category;

    // --- Physical specs ---
    @Column(precision = 10, scale = 3)
    private BigDecimal weight;

    @Enumerated(EnumType.STRING)
    @Column(length = 2)
    private WeightUnit weightUnit;

    @Column(precision = 10, scale = 2)
    private BigDecimal length;

    @Column(precision = 10, scale = 2)
    private BigDecimal width;

    @Column(precision = 10, scale = 2)
    private BigDecimal height;

    @Enumerated(EnumType.STRING)
    @Column(length = 2)
    private DimensionUnit dimensionUnit;

    private String color;

    private String size;

    private String material;

    // --- Flexible attributes, e.g. "RAM" = "16 GB" (list order is kept) ---
    @ElementCollection
    @CollectionTable(name = "product_attributes", joinColumns = @JoinColumn(name = "product_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @OrderColumn(name = "position")
    @Builder.Default
    private List<ProductAttribute> attributes = new ArrayList<>();

    // --- Media: images in display order; the first one is the main image ---
    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @OrderColumn(name = "position")
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();
}
