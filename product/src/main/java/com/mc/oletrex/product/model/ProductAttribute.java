package com.mc.oletrex.product.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A free-form spec such as "RAM" = "16 GB", so product types can differ without schema changes. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAttribute {

    @Column(name = "attr_name", nullable = false)
    private String name;

    @Column(name = "attr_value", nullable = false)
    private String value;
}
