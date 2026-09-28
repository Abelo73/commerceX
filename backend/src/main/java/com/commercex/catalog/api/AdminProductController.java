package com.commercex.catalog.api;

import com.commercex.catalog.application.CatalogViews.AdminProductSummary;
import com.commercex.catalog.application.CatalogViews.AdminProductView;
import com.commercex.catalog.application.ProductAdminService;
import com.commercex.catalog.application.ProductCommand;
import com.commercex.catalog.domain.ProductImage;
import com.commercex.catalog.domain.ProductStatus;
import com.commercex.catalog.domain.VariantSpec;
import com.commercex.common.security.CurrentUser;
import com.commercex.common.text.Slugs;
import com.commercex.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/products")
@PreAuthorize("hasAnyRole('ADMIN', 'CATALOG_MANAGER')")
@Validated
@Tag(name = "Admin: Catalog")
@SecurityRequirement(name = "bearer")
class AdminProductController {

    private final ProductAdminService productAdminService;

    AdminProductController(ProductAdminService productAdminService) {
        this.productAdminService = productAdminService;
    }

    record ImageRequest(
            @NotBlank @Size(max = 2048) @Pattern(regexp = CatalogPatterns.HTTP_URL, message = "must be an http(s) URL")
            String url,
            @Size(max = 300) String altText) {
    }

    record VariantRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = CatalogPatterns.SKU) String sku,
            @NotNull @Size(max = 3) Map<@NotBlank @Size(max = 50) String, @NotBlank @Size(max = 50) String> options,
            @Positive @Max(CatalogPatterns.MAX_PRICE_MINOR) long price,
            @Positive @Max(CatalogPatterns.MAX_PRICE_MINOR) Long compareAtPrice,
            Boolean active) {

        VariantSpec spec() {
            Map<String, String> trimmed = new LinkedHashMap<>();
            options.forEach((k, v) -> trimmed.put(k.trim(), v.trim()));
            return new VariantSpec(sku.trim().toUpperCase(Locale.ROOT), trimmed, price, compareAtPrice,
                    active == null || active);
        }
    }

    /** Full product document (PUT semantics). Variants omitted on update are deactivated. */
    record ProductRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = Slugs.MAX_LENGTH) @Pattern(regexp = Slugs.PATTERN) String slug,
            @Size(max = 500) String shortDescription,
            @Size(max = 20_000) String description,
            Long brandId,
            @NotNull Long categoryId,
            @Size(max = 3) List<@NotBlank @Size(max = 50) String> options,
            @Size(max = 20) List<@Valid @NotNull ImageRequest> images,
            @Size(max = 20) List<@NotBlank @Size(max = 50) @Pattern(regexp = CatalogPatterns.TAG) String> tags,
            @NotEmpty @Size(max = 100) List<@Valid @NotNull VariantRequest> variants,
            Long version) {

        ProductCommand command() {
            return new ProductCommand(name, slug, shortDescription, description, brandId, categoryId,
                    options == null ? List.of() : options,
                    images == null ? List.of() : images.stream()
                            .map(i -> new ProductImage(i.url().trim(), i.altText())).toList(),
                    tags == null ? List.of() : tags,
                    variants.stream().map(VariantRequest::spec).toList(),
                    version);
        }
    }

    @GetMapping
    PageResponse<AdminProductSummary> list(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(productAdminService.list(status, q, page, size), view -> view);
    }

    @GetMapping("/{id}")
    AdminProductView get(@PathVariable long id) {
        return productAdminService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a DRAFT product with its variants")
    AdminProductView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProductRequest body) {
        return productAdminService.create(CurrentUser.id(jwt), body.command());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a product; requires the current version (optimistic locking)")
    AdminProductView update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
            @Valid @RequestBody ProductRequest body) {
        return productAdminService.update(CurrentUser.id(jwt), id, body.command());
    }

    @PostMapping("/{id}/publish")
    AdminProductView publish(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return productAdminService.publish(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/unpublish")
    AdminProductView unpublish(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return productAdminService.unpublish(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/archive")
    AdminProductView archive(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return productAdminService.archive(CurrentUser.id(jwt), id);
    }
}
