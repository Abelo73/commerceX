package com.commercex.auth.api;

import com.commercex.auth.application.CustomerAdminService;
import com.commercex.auth.application.UserView;
import com.commercex.auth.domain.Role;
import com.commercex.auth.domain.UserStatus;
import com.commercex.common.security.CurrentUser;
import com.commercex.common.web.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/customers")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: Customers")
@SecurityRequirement(name = "bearer")
class AdminCustomerController {

    private final CustomerAdminService customerAdminService;

    AdminCustomerController(CustomerAdminService customerAdminService) {
        this.customerAdminService = customerAdminService;
    }

    @GetMapping
    PageResponse<UserView> list(@RequestParam(required = false) @Size(max = 254) String q,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(customerAdminService.list(q, pageable), view -> view);
    }

    /** Both fields optional; omitted fields are left unchanged. */
    record UpdateCustomerRequest(UserStatus status, @Size(min = 1) Set<Role> roles) {
    }

    @PatchMapping("/{id}")
    UserView update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody UpdateCustomerRequest body) {
        return customerAdminService.update(CurrentUser.id(jwt), id, body.status(), body.roles());
    }
}
