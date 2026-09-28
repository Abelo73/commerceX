package com.commercex.catalog.api;

import com.commercex.catalog.application.BrandService;
import com.commercex.catalog.application.CatalogViews.BrandView;
import com.commercex.catalog.application.CatalogViews.ProductDetailView;
import com.commercex.catalog.application.CatalogViews.ProductSummaryView;
import com.commercex.catalog.application.ProductQueryService;
import com.commercex.catalog.application.ProductQueryService.SearchParams;
import com.commercex.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Catalog")
class ProductController {

    private static final CacheControl PUBLIC_SHORT = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    private final ProductQueryService productQueryService;
    private final BrandService brandService;

    ProductController(ProductQueryService productQueryService, BrandService brandService) {
        this.productQueryService = productQueryService;
        this.brandService = brandService;
    }

    @GetMapping("/products")
    @Operation(summary = "Search and browse published products. Prices are in minor units.")
    ResponseEntity<PageResponse<ProductSummaryView>> search(
            @RequestParam(required = false) @Size(max = 200) String q,
            @Parameter(description = "Category slug; includes subcategories")
            @RequestParam(required = false) @Size(max = 160) String category,
            @Parameter(description = "Brand slug(s), comma separated or repeated")
            @RequestParam(required = false) @Size(max = 20) List<@Size(max = 160) String> brand,
            @RequestParam(required = false) @PositiveOrZero Long minPrice,
            @RequestParam(required = false) @PositiveOrZero Long maxPrice,
            @RequestParam(required = false) @DecimalMin("0") @DecimalMax("5") Double minRating,
            @Parameter(description = "relevance | newest | price_asc | price_desc | rating")
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        var result = productQueryService.search(
                new SearchParams(q, category, brand, minPrice, maxPrice, minRating, sort, page, size));
        return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(PageResponse.from(result, view -> view));
    }

    @GetMapping("/products/{slug}")
    ResponseEntity<ProductDetailView> detail(@PathVariable @Size(max = 160) String slug) {
        return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(productQueryService.detail(slug));
    }

    @GetMapping("/brands")
    ResponseEntity<List<BrandView>> brands() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(brandService.listActive());
    }
}
