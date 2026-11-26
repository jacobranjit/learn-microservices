package com.mc.oletrex.masterdata.csv;

/** One line of customers.csv: a customer account and delivery address to seed. */
public record CustomerRow(String email, String name, String phone, String line1, String line2,
                          String city, String state, String postalCode, String country) {
}
