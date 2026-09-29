package com.commercex.admin.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Dashboard snapshot. Period figures cover {@code from} (start of a UTC day) until now. */
public record Dashboard(
        Instant from,
        String currency,
        long revenueMinor,
        long ordersPlaced,
        long paidOrders,
        long averageOrderValueMinor,
        long newCustomers,
        long totalCustomers,
        long failedPayments,
        long publishedProducts,
        long lowStockVariants,
        long awaitingFulfilment,
        long unpaidOrders,
        List<Day> revenueByDay,
        List<Activity> recentActivity) {

    public record Day(LocalDate date, long revenueMinor, long orders) {
    }

    public record Activity(long id, String action, String resourceType, String resourceId, String actorEmail,
            Instant at) {
    }
}
