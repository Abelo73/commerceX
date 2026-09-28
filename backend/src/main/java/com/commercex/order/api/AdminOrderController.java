package com.commercex.order.api;

import com.commercex.common.security.CurrentUser;
import com.commercex.common.web.PageResponse;
import com.commercex.order.OrderDetails;
import com.commercex.order.application.OrderService;
import com.commercex.order.application.OrderService.OrderSummary;
import com.commercex.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
@Validated
@Tag(name = "Admin: Orders")
@SecurityRequirement(name = "bearer")
class AdminOrderController {

    private final OrderService orderService;

    AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    record TransitionRequest(@NotNull OrderStatus toStatus, @Size(max = 500) String reason) {
    }

    @GetMapping
    PageResponse<OrderSummary> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @Size(max = 254) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(orderService.staffOrders(status, q, page, size), s -> s);
    }

    @GetMapping("/{orderNumber}")
    OrderDetails get(@PathVariable @Size(max = 20) String orderNumber) {
        return orderService.staffOrder(orderNumber);
    }

    @PostMapping("/{orderNumber}/transitions")
    @Operation(summary = "Move an order through fulfilment (PROCESSING → PACKED → SHIPPED → DELIVERED) or cancel "
            + "an unpaid one")
    OrderDetails transition(@AuthenticationPrincipal Jwt jwt, @PathVariable @Size(max = 20) String orderNumber,
            @Valid @RequestBody TransitionRequest body) {
        return orderService.transitionByStaff(CurrentUser.id(jwt), orderNumber, body.toStatus(), body.reason());
    }
}
