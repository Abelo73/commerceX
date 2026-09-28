package com.commercex.common.web;

import com.commercex.common.money.Money;

/** Wire format for money: {@code {"amount": 12000, "currency": "USD"}} in minor units. */
public record MoneyResponse(long amount, String currency) {

    public static MoneyResponse of(Money money) {
        return money == null ? null : new MoneyResponse(money.amountMinor(), money.currency().getCurrencyCode());
    }

    public static MoneyResponse of(Long amountMinor, String currency) {
        return amountMinor == null ? null : new MoneyResponse(amountMinor, currency);
    }
}
