package com.commercex.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @Test
    void addsSubtractsAndMultiplies() {
        Money price = Money.of(12_000, "USD");

        assertThat(price.times(3)).isEqualTo(Money.of(36_000, "USD"));
        assertThat(price.plus(Money.of(500, "USD"))).isEqualTo(Money.of(12_500, "USD"));
        assertThat(price.minus(Money.of(12_500, "USD")).isNegative()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "10000, 1000, 1000",  // 10% of 100.00 = 10.00
            "999,   1000, 100",   // 10% of 9.99 = 0.999 -> 1.00
            "995,   1000, 100",   // 0.995 -> 1.00 (half up)
            "994,   1000, 99",    // 0.994 -> 0.99
            "12345, 1250, 1543",  // 12.5% of 123.45 = 15.43125 -> 15.43
            "5000,  10000, 5000", // 100%
            "5000,  0,    0"
    })
    void percentageRoundsHalfUp(long amount, int basisPoints, long expected) {
        assertThat(Money.of(amount, "USD").percentage(basisPoints)).isEqualTo(Money.of(expected, "USD"));
    }

    @Test
    void rejectsCurrencyMismatch() {
        assertThatThrownBy(() -> Money.of(100, "USD").plus(Money.of(100, "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void rejectsOverflow() {
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "USD").plus(Money.of(1, "USD")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void rejectsInvalidPercentage() {
        assertThatThrownBy(() -> Money.of(100, "USD").percentage(10_001))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
