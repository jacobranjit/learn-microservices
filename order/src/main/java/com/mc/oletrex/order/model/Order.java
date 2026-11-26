package com.mc.oletrex.order.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders") // "order" is a reserved SQL keyword
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    /** The customer who placed the order (customerId claim of the caller's token). */
    @Column(nullable = false)
    private Long customerId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "line1", column = @Column(name = "shipping_line1", nullable = false)),
            @AttributeOverride(name = "line2", column = @Column(name = "shipping_line2")),
            @AttributeOverride(name = "city", column = @Column(name = "shipping_city", nullable = false)),
            @AttributeOverride(name = "state", column = @Column(name = "shipping_state")),
            @AttributeOverride(name = "postalCode", column = @Column(name = "shipping_postal_code", nullable = false)),
            @AttributeOverride(name = "country", column = @Column(name = "shipping_country", nullable = false))
    })
    private OrderAddress shippingAddress;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "line1", column = @Column(name = "billing_line1", nullable = false)),
            @AttributeOverride(name = "line2", column = @Column(name = "billing_line2")),
            @AttributeOverride(name = "city", column = @Column(name = "billing_city", nullable = false)),
            @AttributeOverride(name = "state", column = @Column(name = "billing_state")),
            @AttributeOverride(name = "postalCode", column = @Column(name = "billing_postal_code", nullable = false)),
            @AttributeOverride(name = "country", column = @Column(name = "billing_country", nullable = false))
    })
    private OrderAddress billingAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    /** Code of the inventory location that supplied every item; set when the order is CONFIRMED. */
    @Column(length = 50)
    private String fulfilmentLocationCode;

    /** Why the order was cancelled (unknown product, insufficient stock, ...); null otherwise. */
    @Column(length = 1000)
    private String statusReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderLineItem> lineItems = new ArrayList<>();

    public void addLineItem(OrderLineItem item) {
        lineItems.add(item);
        item.setOrder(this);
    }

    public void removeLineItem(OrderLineItem item) {
        lineItems.remove(item);
        item.setOrder(null);
    }
}
