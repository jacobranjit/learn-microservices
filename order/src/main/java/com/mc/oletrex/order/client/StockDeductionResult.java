package com.mc.oletrex.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The part of inventory's deduction response the order service needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockDeductionResult(String locationCode) {
}
