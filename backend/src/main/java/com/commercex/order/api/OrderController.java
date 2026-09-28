package com.commercex.order.api;

import com.commercex.common.security.CurrentUser;
import com.commercex.common.web.PageResponse;
import com.commercex.order.OrderDetails;
import com.commercex.order.application.OrderService;
import com.commercex.order.application.OrderService.OrderSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in customer's own orders. Placing an order lives in the checkout module. */
@RestController
@RequestMapping("/api/v1/orders")
@Validated
@Tag(name = "Orders")
@SecurityRequirement(name = "bearer")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    PageResponse<OrderSummary> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(orderService.customerOrders(CurrentUser.id(jwt), page, size), s -> s);
    }

    @GetMapping("/{orderNumber}")
    OrderDetails get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Size(max = 20) String orderNumber) {
        return orderService.customerOrder(CurrentUser.id(jwt), orderNumber);
    }

    @PostMapping("/{orderNumber}/cancel")
    @Operation(summary = "Cancel an unpaid order and release its stock")
    OrderDetails cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable @Size(max = 20) String orderNumber) {
        return orderService.cancelByCustomer(CurrentUser.id(jwt), orderNumber);
    }
}
