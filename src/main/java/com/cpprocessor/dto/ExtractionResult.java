package com.cpprocessor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExtractionResult(
        @JsonProperty(required = true, value = "items") List<ExtractedItem> items,
        @JsonProperty(required = true, value = "total_positions") int totalPositions
) {
    public record ExtractedItem(
            @JsonProperty(required = true, value = "product_name") String productName,
            @JsonProperty(value = "article") String article,
            @JsonProperty(value = "quantity") Double quantity,
            @JsonProperty(value = "unit") String unit,
            @JsonProperty(value = "price") Double price,
            @JsonProperty(value = "supplier") String supplier,
            @JsonProperty(value = "notes") String notes
    ) {}
}
