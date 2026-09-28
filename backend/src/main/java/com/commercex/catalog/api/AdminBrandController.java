package com.commercex.catalog.api;

import com.commercex.catalog.application.BrandService;
import com.commercex.catalog.application.BrandService.BrandCommand;
import com.commercex.catalog.application.CatalogViews.BrandView;
import com.commercex.common.security.CurrentUser;
import com.commercex.common.text.Slugs;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/brands")
@PreAuthorize("hasAnyRole('ADMIN', 'CATALOG_MANAGER')")
@Tag(name = "Admin: Catalog")
@SecurityRequirement(name = "bearer")
class AdminBrandController {

    private final BrandService brandService;

    AdminBrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    record BrandRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = Slugs.MAX_LENGTH) @Pattern(regexp = Slugs.PATTERN) String slug,
            @Size(max = 2000) String description,
            @Size(max = 2048) @Pattern(regexp = CatalogPatterns.HTTP_URL, message = "must be an http(s) URL")
            String logoUrl,
            Boolean active,
            Long version) {

        BrandCommand command() {
            return new BrandCommand(name, slug, description, logoUrl, active == null || active, version);
        }
    }

    @GetMapping
    List<BrandView> list() {
        return brandService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BrandView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BrandRequest body) {
        return brandService.create(CurrentUser.id(jwt), body.command());
    }

    @PutMapping("/{id}")
    BrandView update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody BrandRequest body) {
        return brandService.update(CurrentUser.id(jwt), id, body.command());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        brandService.delete(CurrentUser.id(jwt), id);
    }
}
