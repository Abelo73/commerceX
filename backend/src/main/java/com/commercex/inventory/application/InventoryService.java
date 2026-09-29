package com.commercex.inventory.application;

import com.commercex.common.audit.AuditLog;
import com.commercex.common.error.ConflictException;
import com.commercex.common.error.ErrorCode;
import com.commercex.common.error.NotFoundException;
import com.commercex.common.error.ValidationException;
import com.commercex.inventory.InsufficientStockException;
import com.commercex.inventory.InventoryApi;
import com.commercex.inventory.LowStockReached;
import com.commercex.inventory.ReservationLine;
import com.commercex.inventory.StockLevel;
import com.commercex.inventory.StockPage;
import com.commercex.inventory.StockStatusChanged;
import com.commercex.inventory.infrastructure.InventoryRepository;
import com.commercex.inventory.infrastructure.InventoryRepository.Item;
import com.commercex.inventory.infrastructure.InventoryRepository.Levels;
import com.commercex.inventory.infrastructure.InventoryRepository.Movement;
import com.commercex.inventory.infrastructure.InventoryRepository.Reservation;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService implements InventoryApi {

    /** Default for variants that have no inventory record yet (matches the column default). */
    static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    public enum AdjustmentType { RESTOCK, ADJUSTMENT, RETURN }

    private final InventoryRepository inventory;
    private final ApplicationEventPublisher events;
    private final AuditLog auditLog;
    private final MeterRegistry meters;

    InventoryService(InventoryRepository inventory, ApplicationEventPublisher events, AuditLog auditLog,
            MeterRegistry meters) {
        this.inventory = inventory;
        this.events = events;
        this.auditLog = auditLog;
        this.meters = meters;
    }

    // ---- InventoryApi ----------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Map<Long, StockLevel> stockLevels(Collection<Long> variantIds) {
        Map<Long, StockLevel> result = new HashMap<>();
        for (long id : variantIds) {
            result.put(id, new StockLevel(id, 0, DEFAULT_LOW_STOCK_THRESHOLD));
        }
        for (Levels levels : inventory.levels(variantIds)) {
            result.put(levels.variantId(),
                    new StockLevel(levels.variantId(), levels.available(), levels.lowStockThreshold()));
        }
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(long orderId, List<ReservationLine> lines, Instant expiresAt) {
        // Merge duplicate variants and sort: every transaction locks rows in the same order → no deadlocks.
        TreeMap<Long, Integer> merged = new TreeMap<>();
        for (ReservationLine line : lines) {
            merged.merge(line.variantId(), line.quantity(), Math::addExact);
        }
        String reference = "order:" + orderId;
        for (Map.Entry<Long, Integer> line : merged.entrySet()) {
            long variantId = line.getKey();
            int qty = line.getValue();
            if (!inventory.insertReservation(orderId, variantId, qty, expiresAt)) {
                continue; // already reserved by an earlier attempt of this order
            }
            Levels after = inventory.reserve(variantId, qty).orElseThrow(() -> insufficient(variantId, qty));
            inventory.recordMovement(after, "RESERVE", 0, qty, null, reference, null);
            publishTransitions(after, after.available() + qty);
        }
        meters.counter("commercex.inventory.reservations", "outcome", "reserved").increment();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int commit(long orderId) {
        List<Reservation> reservations = inventory.lockActiveReservations(orderId);
        for (Reservation r : reservations) {
            Levels after = inventory.commit(r.variantId(), r.quantity())
                    .orElseThrow(() -> new IllegalStateException("Missing inventory for " + r.variantId()));
            inventory.markReservation(orderId, r.variantId(), "COMMITTED");
            inventory.recordMovement(after, "COMMIT", -r.quantity(), -r.quantity(), null, "order:" + orderId, null);
            // available is unchanged by a commit, so no stock-status transition.
        }
        return reservations.size();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int release(long orderId, String reason) {
        List<Reservation> reservations = inventory.lockActiveReservations(orderId);
        for (Reservation r : reservations) {
            Levels after = inventory.release(r.variantId(), r.quantity())
                    .orElseThrow(() -> new IllegalStateException("Missing inventory for " + r.variantId()));
            inventory.markReservation(orderId, r.variantId(), "RELEASED");
            inventory.recordMovement(after, "RELEASE", 0, -r.quantity(), reason, "order:" + orderId, null);
            publishTransitions(after, after.available() - r.quantity());
        }
        return reservations.size();
    }

    @Override
    @Transactional(readOnly = true)
    public StockPage stockItems(boolean lowStockOnly, int page, int size) {
        return new StockPage(inventory.items(lowStockOnly, size, (long) page * size).stream()
                .map(i -> new StockPage.Item(i.variantId(), i.onHand(), i.reserved(), i.available(), i.sold(),
                        i.lowStockThreshold(), i.updatedAt()))
                .toList(), page, size, inventory.countItems(lowStockOnly));
    }

    @Override
    @Transactional(readOnly = true)
    public long lowStockCount() {
        return inventory.countItems(true);
    }

    // ---- admin -----------------------------------------------------------------------------------

    /**
     * Manual stock change. RESTOCK and RETURN add stock; ADJUSTMENT corrects in either direction
     * (damage, shrinkage, stock count). Stock can never drop below what customers have reserved.
     */
    @Transactional
    public Item adjust(long actorId, long variantId, AdjustmentType type, int delta, String reason) {
        if (delta == 0) {
            throw new ValidationException("quantity", "must not be zero");
        }
        if (type != AdjustmentType.ADJUSTMENT && delta < 0) {
            throw new ValidationException("quantity", type + " must be positive");
        }
        Levels after;
        if (delta > 0) {
            try {
                after = inventory.increaseOnHand(variantId, delta);
            } catch (DataIntegrityViolationException unknownVariant) {
                throw new NotFoundException("Variant", variantId);
            }
        } else {
            after = inventory.decreaseOnHand(variantId, -delta).orElseThrow(() -> new ConflictException(
                    ErrorCode.INSUFFICIENT_STOCK, "Cannot remove " + (-delta) + " units: stock would drop below"
                            + " the quantity reserved for customers, or the variant has no stock record"));
        }
        inventory.recordMovement(after, type.name(), delta, 0, reason, null, actorId);
        publishTransitions(after, after.available() - delta);
        auditLog.record(actorId, "INVENTORY_ADJUSTED", "variant", variantId,
                Map.of("type", type.name(), "delta", delta, "onHandAfter", after.onHand()));
        return item(variantId);
    }

    @Transactional
    public Item setLowStockThreshold(long actorId, long variantId, int threshold) {
        try {
            inventory.setThreshold(variantId, threshold);
        } catch (DataIntegrityViolationException unknownVariant) {
            throw new NotFoundException("Variant", variantId);
        }
        auditLog.record(actorId, "INVENTORY_THRESHOLD_SET", "variant", variantId, Map.of("threshold", threshold));
        return item(variantId);
    }

    @Transactional(readOnly = true)
    public Item item(long variantId) {
        return inventory.item(variantId).orElseThrow(() -> new NotFoundException("Inventory for variant", variantId));
    }

    @Transactional(readOnly = true)
    public Page<Item> items(boolean lowStockOnly, int page, int size) {
        long total = inventory.countItems(lowStockOnly);
        return new PageImpl<>(inventory.items(lowStockOnly, size, (long) page * size), PageRequest.of(page, size),
                total);
    }

    @Transactional(readOnly = true)
    public Page<Movement> movements(long variantId, int page, int size) {
        long total = inventory.countMovements(variantId);
        return new PageImpl<>(inventory.movements(variantId, size, (long) page * size), PageRequest.of(page, size),
                total);
    }

    // ---- internals -------------------------------------------------------------------------------

    private InsufficientStockException insufficient(long variantId, int requested) {
        meters.counter("commercex.inventory.reservations", "outcome", "insufficient_stock").increment();
        int available = inventory.levels(List.of(variantId)).stream().findFirst().map(Levels::available).orElse(0);
        return new InsufficientStockException(variantId, requested, available);
    }

    private void publishTransitions(Levels after, int availableBefore) {
        int availableAfter = after.available();
        if ((availableBefore > 0) != (availableAfter > 0)) {
            events.publishEvent(new StockStatusChanged(after.variantId(), availableAfter > 0));
        }
        if (availableBefore > after.lowStockThreshold() && availableAfter <= after.lowStockThreshold()) {
            events.publishEvent(new LowStockReached(after.variantId(), availableAfter, after.lowStockThreshold()));
        }
    }
}
