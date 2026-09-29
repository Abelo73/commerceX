package com.commercex.admin.api;

import com.commercex.admin.application.AdminDashboardService;
import com.commercex.admin.application.Dashboard;
import com.commercex.admin.application.StockRow;
import com.commercex.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS')")
@Validated
@Tag(name = "Admin: Dashboard")
@SecurityRequirement(name = "bearer")
class AdminDashboardController {

    private final AdminDashboardService dashboard;

    AdminDashboardController(AdminDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Sales, customer, stock and activity figures for the last N days (UTC, including today)")
    Dashboard dashboard(@RequestParam(defaultValue = "30") @Min(1) @Max(90) int days) {
        return dashboard.dashboard(days);
    }

    @GetMapping("/stock")
    @Operation(summary = "Inventory with product name and SKU, lowest availability first")
    PageResponse<StockRow> stock(
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return dashboard.stock(lowStockOnly, page, size);
    }
}
