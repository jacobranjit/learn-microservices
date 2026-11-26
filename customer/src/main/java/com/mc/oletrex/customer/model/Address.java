package com.mc.oletrex.customer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Delivery address, stored in the customers table. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Column(name = "address_line1", nullable = false, length = 200)
    private String line1;

    @Column(name = "address_line2", length = 200)
    private String line2;

    @Column(name = "address_city", nullable = false, length = 100)
    private String city;

    @Column(name = "address_state", length = 100)
    private String state;

    @Column(name = "address_postal_code", nullable = false, length = 20)
    private String postalCode;

    /** ISO 3166-1 alpha-2, e.g. "US". */
    @Column(name = "address_country", nullable = false, length = 2)
    private String country;
}
