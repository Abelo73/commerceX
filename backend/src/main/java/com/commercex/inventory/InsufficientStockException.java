package com.commercex.inventory;

import com.commercex.common.error.ConflictException;
import com.commercex.common.error.ErrorCode;
import java.util.Map;

public class InsufficientStockException extends ConflictException {

    private final long variantId;
    private final int requested;
    private final int available;

    public InsufficientStockException(long variantId, int requested, int available) {
        super(ErrorCode.INSUFFICIENT_STOCK, "Only " + Math.max(available, 0) + " left for variant " + variantId,
                Map.of("variantId", variantId, "requested", requested, "available", Math.max(available, 0)));
        this.variantId = variantId;
        this.requested = requested;
        this.available = Math.max(available, 0);
    }

    public long variantId() {
        return variantId;
    }

    public int requested() {
        return requested;
    }

    public int available() {
        return available;
    }
}
