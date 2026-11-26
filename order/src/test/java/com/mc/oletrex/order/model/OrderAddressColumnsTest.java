package com.mc.oletrex.order.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The @AttributeOverride columns replace OrderAddress's @Column, so their lengths must be repeated. */
class OrderAddressColumnsTest {

    @Test
    void shippingAndBillingOverridesKeepTheAddressColumnLengths() throws Exception {
        for (String field : new String[]{"shippingAddress", "billingAddress"}) {
            AttributeOverrides overrides = Order.class.getDeclaredField(field).getAnnotation(AttributeOverrides.class);
            assertThat(overrides.value()).hasSize(6);
            for (AttributeOverride override : overrides.value()) {
                int expected = OrderAddress.class.getDeclaredField(override.name()).getAnnotation(Column.class).length();
                assertThat(override.column().length()).as(field + "." + override.name()).isEqualTo(expected);
            }
        }
    }
}
