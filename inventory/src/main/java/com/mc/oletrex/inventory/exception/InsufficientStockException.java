package com.mc.oletrex.inventory.exception;

import com.mc.oletrex.inventory.dto.StockShortage;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class InsufficientStockException extends RuntimeException {

    private final List<StockShortage> shortages;

    public InsufficientStockException(List<StockShortage> shortages) {
        super("Insufficient stock for: " + shortages.stream()
                .map(s -> s.skuCode() + " (requested " + s.requested() + ", available " + s.available() + ")")
                .collect(Collectors.joining(", ")));
        this.shortages = shortages;
    }
}
