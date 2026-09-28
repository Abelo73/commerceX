package com.commercex.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StockLevelTest {

    @Test
    void classifiesAvailability() {
        assertThat(new StockLevel(1, 0, 5).availability()).isEqualTo(StockLevel.Availability.OUT_OF_STOCK);
        assertThat(new StockLevel(1, 5, 5).availability()).isEqualTo(StockLevel.Availability.LOW_STOCK);
        assertThat(new StockLevel(1, 6, 5).availability()).isEqualTo(StockLevel.Availability.IN_STOCK);
        assertThat(new StockLevel(1, 3, 5).canSatisfy(3)).isTrue();
        assertThat(new StockLevel(1, 3, 5).canSatisfy(4)).isFalse();
    }
}
