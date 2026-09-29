package com.commercex.admin.application;

import com.commercex.auth.AccountApi;
import com.commercex.auth.CustomerInfo;
import com.commercex.auth.CustomerStats;
import com.commercex.catalog.CatalogApi;
import com.commercex.catalog.VariantSnapshot;
import com.commercex.common.audit.AuditLog;
import com.commercex.common.web.PageResponse;
import com.commercex.inventory.InventoryApi;
import com.commercex.inventory.StockPage;
import com.commercex.order.OrderApi;
import com.commercex.order.OrderStats;
import com.commercex.payment.PaymentApi;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Read-only back-office views. Each figure comes from the owning module's API, so the numbers are
 * individually consistent but not a single snapshot (acceptable for a dashboard).
 */
@Service
public class AdminDashboardService {

    static final int ACTIVITY_LIMIT = 10;

    private final OrderApi orders;
    private final AccountApi accounts;
    private final CatalogApi catalog;
    private final InventoryApi inventory;
    private final PaymentApi payments;
    private final AuditLog auditLog;
    private final Clock clock;

    AdminDashboardService(OrderApi orders, AccountApi accounts, CatalogApi catalog, InventoryApi inventory,
            PaymentApi payments, AuditLog auditLog, Clock clock) {
        this.orders = orders;
        this.accounts = accounts;
        this.catalog = catalog;
        this.inventory = inventory;
        this.payments = payments;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /** {@code days} includes today: 1 = today only. */
    public Dashboard dashboard(int days) {
        Instant from = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(days - 1L)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        OrderStats o = orders.stats(from);
        CustomerStats c = accounts.customerStats(from);
        return new Dashboard(from, o.currency(), o.revenueMinor(), o.ordersPlaced(), o.paidOrders(),
                o.paidOrders() == 0 ? 0 : o.revenueMinor() / o.paidOrders(),
                c.newSince(), c.total(), payments.failedPaymentsSince(from), catalog.publishedProductCount(),
                inventory.lowStockCount(), o.awaitingFulfilment(), o.unpaidOrders(),
                o.byDay().stream().map(d -> new Dashboard.Day(d.date(), d.revenueMinor(), d.orders())).toList(),
                activity());
    }

    public PageResponse<StockRow> stock(boolean lowStockOnly, int page, int size) {
        StockPage stock = inventory.stockItems(lowStockOnly, page, size);
        Map<Long, VariantSnapshot> variants = catalog.variants(
                stock.items().stream().map(StockPage.Item::variantId).toList());
        List<StockRow> rows = stock.items().stream().map(i -> {
            VariantSnapshot v = variants.get(i.variantId());
            return new StockRow(i.variantId(), v == null ? null : v.productId(), v == null ? null : v.productName(),
                    v == null ? null : v.sku(), v == null ? null : v.label(), v == null ? null : v.imageUrl(),
                    i.onHand(), i.reserved(), i.available(), i.sold(), i.lowStockThreshold(),
                    i.available() <= i.lowStockThreshold(), i.updatedAt());
        }).toList();
        int totalPages = (int) ((stock.totalElements() + size - 1) / size);
        return new PageResponse<>(rows, page, size, stock.totalElements(), totalPages);
    }

    private List<Dashboard.Activity> activity() {
        List<AuditLog.Entry> entries = auditLog.recent(ACTIVITY_LIMIT);
        Map<Long, String> emails = new HashMap<>();
        entries.stream().map(AuditLog.Entry::actorUserId).filter(Objects::nonNull).distinct()
                .forEach(id -> accounts.customer(id).map(CustomerInfo::email).ifPresent(e -> emails.put(id, e)));
        return entries.stream().map(e -> new Dashboard.Activity(e.id(), e.action(), e.resourceType(),
                e.resourceId(), e.actorUserId() == null ? null : emails.get(e.actorUserId()), e.at())).toList();
    }
}
