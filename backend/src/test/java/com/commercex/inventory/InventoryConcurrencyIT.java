package com.commercex.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.commercex.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TRD §8: "Stock = 1, Customer A buys 1, Customer B buys 1 — the system must not allow both."
 * Every buyer runs in its own thread and its own database transaction, released at the same instant.
 */
class InventoryConcurrencyIT extends AbstractIntegrationTest {

    private static final int BUYERS = 50;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InventoryApi inventory;

    @Autowired
    TransactionTemplate tx;

    InventoryTestSupport support;

    @BeforeEach
    void setUp() throws Exception {
        support = new InventoryTestSupport(mvc, json, jdbc, inventory, tx);
    }

    @Test
    void fiftyBuyersForTheLastUnitExactlyOneWins() throws Exception {
        long variantId = support.variantWithStock(1);

        Outcome outcome = race(BUYERS, i -> () -> {
            support.reserve(support.newOrderId(), new ReservationLine(variantId, 1));
            return null;
        });

        assertThat(outcome.successes()).isEqualTo(1);
        assertThat(outcome.insufficientStock()).isEqualTo(BUYERS - 1);
        assertThat(outcome.unexpected()).isEmpty();
        assertThat(support.reserved(variantId)).isEqualTo(1);
        assertThat(support.onHand(variantId)).isEqualTo(1);
        assertThat(support.count("SELECT count(*) FROM inventory_reservations WHERE variant_id = ?", variantId))
                .as("losers' reservation rows were rolled back").isEqualTo(1);
        assertThat(support.count(
                "SELECT count(*) FROM inventory_movements WHERE variant_id = ? AND type = 'RESERVE'", variantId))
                .isEqualTo(1);
    }

    @Test
    void limitedStockIsNeverOversold() throws Exception {
        long variantId = support.variantWithStock(10);

        Outcome outcome = race(BUYERS, i -> () -> {
            support.reserve(support.newOrderId(), new ReservationLine(variantId, 1));
            return null;
        });

        assertThat(outcome.successes()).isEqualTo(10);
        assertThat(outcome.insufficientStock()).isEqualTo(BUYERS - 10);
        assertThat(outcome.unexpected()).isEmpty();
        assertThat(support.reserved(variantId)).isEqualTo(10);
    }

    @Test
    void multiLineOrdersInOpposingOrderDoNotDeadlock() throws Exception {
        long a = support.variantWithStock(100);
        long b = support.variantWithStock(100);

        // Half the buyers list A then B, the other half B then A: unsorted locking would deadlock.
        Outcome outcome = race(40, i -> () -> {
            ReservationLine first = new ReservationLine(i % 2 == 0 ? a : b, 1);
            ReservationLine second = new ReservationLine(i % 2 == 0 ? b : a, 1);
            support.reserve(support.newOrderId(), first, second);
            return null;
        });

        assertThat(outcome.unexpected()).isEmpty();
        assertThat(outcome.successes()).isEqualTo(40);
        assertThat(support.reserved(a)).isEqualTo(40);
        assertThat(support.reserved(b)).isEqualTo(40);
    }

    @Test
    void databaseRejectsOversellingEvenIfCodeIsBypassed() throws Exception {
        long variantId = support.variantWithStock(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                        "UPDATE inventory_items SET reserved = 2 WHERE variant_id = ?", variantId))
                .hasMessageContaining("ck_inventory_reserved_within_on_hand");
    }

    // ---------------------------------------------------------------------------------------------

    interface Buyer {
        Callable<Void> attempt(int index);
    }

    record Outcome(int successes, int insufficientStock, List<Throwable> unexpected) {
    }

    private static Outcome race(int buyers, Buyer buyer) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch ready = new CountDownLatch(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < buyers; i++) {
                Callable<Void> attempt = buyer.attempt(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return attempt.call();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            int successes = 0;
            int insufficient = 0;
            List<Throwable> unexpected = new ArrayList<>();
            for (Future<Void> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                    successes++;
                } catch (java.util.concurrent.ExecutionException e) {
                    if (e.getCause() instanceof InsufficientStockException) {
                        insufficient++;
                    } else {
                        unexpected.add(e.getCause());
                    }
                }
            }
            return new Outcome(successes, insufficient, unexpected);
        } finally {
            pool.shutdownNow();
        }
    }
}
