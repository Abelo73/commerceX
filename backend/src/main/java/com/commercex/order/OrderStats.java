package com.commercex.order;

import java.time.LocalDate;
import java.util.List;

/**
 * Order figures for the admin dashboard. Revenue counts orders that were paid and not refunded, placed in
 * the period; the fulfilment and unpaid counts are current totals (not limited to the period).
 */
public record OrderStats(
        long revenueMinor,
        String currency,
        long ordersPlaced,
        long paidOrders,
        long awaitingFulfilment,
        long unpaidOrders,
        List<Day> byDay) {

    public record Day(LocalDate date, long revenueMinor, long orders) {
    }
}
