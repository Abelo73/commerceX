package com.commercex.inventory.api;

import com.commercex.common.security.CurrentUser;
import com.commercex.common.web.PageResponse;
import com.commercex.inventory.application.InventoryService;
import com.commercex.inventory.application.InventoryService.AdjustmentType;
import com.commercex.inventory.infrastructure.InventoryRepository.Item;
import com.commercex.inventory.infrastructure.InventoryRepository.Movement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
@Validated
@Tag(name = "Admin: Inventory")
@SecurityRequirement(name = "bearer")
class AdminInventoryController {

    private final InventoryService inventoryService;

    AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    record InventoryItemResponse(long variantId, int onHand, int reserved, int available, long sold,
            int lowStockThreshold, boolean lowStock, Instant updatedAt) {

        static InventoryItemResponse from(Item i) {
            return new InventoryItemResponse(i.variantId(), i.onHand(), i.reserved(), i.available(), i.sold(),
                    i.lowStockThreshold(), i.available() <= i.lowStockThreshold(), i.updatedAt());
        }
    }

    record MovementResponse(long id, String type, int onHandDelta, int reservedDelta, int onHandAfter,
            int reservedAfter, String reason, String reference, Long actorUserId, Instant createdAt) {

        static MovementResponse from(Movement m) {
            return new MovementResponse(m.id(), m.type(), m.onHandDelta(), m.reservedDelta(), m.onHandAfter(),
                    m.reservedAfter(), m.reason(), m.reference(), m.actorUserId(), m.createdAt());
        }
    }

    /** {@code quantity} is a signed delta; only ADJUSTMENT may be negative. */
    record AdjustmentRequest(
            @NotNull AdjustmentType type,
            @Min(-1_000_000) @Max(1_000_000) int quantity,
            @Size(max = 500) String reason) {
    }

    record ThresholdRequest(@Min(0) @Max(1_000_000) int lowStockThreshold) {
    }

    @GetMapping
    @Operation(summary = "Stock levels, lowest availability first")
    PageResponse<InventoryItemResponse> list(
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return PageResponse.from(inventoryService.items(lowStock, page, size), InventoryItemResponse::from);
    }

    @GetMapping("/{variantId}")
    InventoryItemResponse get(@PathVariable long variantId) {
        return InventoryItemResponse.from(inventoryService.item(variantId));
    }

    @PostMapping("/{variantId}/adjustments")
    @Operation(summary = "Restock, return or correct stock; recorded in the movement ledger")
    InventoryItemResponse adjust(@AuthenticationPrincipal Jwt jwt, @PathVariable long variantId,
            @Valid @RequestBody AdjustmentRequest body) {
        return InventoryItemResponse.from(inventoryService.adjust(CurrentUser.id(jwt), variantId, body.type(),
                body.quantity(), body.reason()));
    }

    @PutMapping("/{variantId}/threshold")
    InventoryItemResponse threshold(@AuthenticationPrincipal Jwt jwt, @PathVariable long variantId,
            @Valid @RequestBody ThresholdRequest body) {
        return InventoryItemResponse.from(
                inventoryService.setLowStockThreshold(CurrentUser.id(jwt), variantId, body.lowStockThreshold()));
    }

    @GetMapping("/{variantId}/movements")
    PageResponse<MovementResponse> movements(@PathVariable long variantId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return PageResponse.from(inventoryService.movements(variantId, page, size), MovementResponse::from);
    }
}
