package com.mc.oletrex.masterdata.csv;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerCsvTest {

    @Test
    void loadsThreeHundredWellFormedCustomersFromTheBundledCsvs() {
        MasterData data = new MasterDataCsvReader(new DefaultResourceLoader(), "classpath:master-data/").read();

        assertThat(data.customers()).hasSize(300);
        assertThat(data.customers()).allSatisfy(c -> {
            assertThat(c.email()).isNotBlank().endsWith("@example.com");
            assertThat(c.country()).isEqualTo("US");
            assertThat(c.postalCode()).matches("\\d{5}");
        });
        assertThat(data.customers().stream().map(CustomerRow::email).distinct()).hasSize(300);
    }
}
