package com.commercex.catalog.application;

import com.commercex.catalog.application.CatalogViews.BrandRef;
import com.commercex.catalog.application.CatalogViews.CategorySummary;
import com.commercex.catalog.application.CatalogViews.ImageView;
import com.commercex.catalog.application.CatalogViews.OptionView;
import com.commercex.catalog.application.CatalogViews.ProductDetailView;
import com.commercex.catalog.application.CatalogViews.ProductSummaryView;
import com.commercex.catalog.application.CatalogViews.VariantView;
import com.commercex.catalog.domain.Product;
import com.commercex.catalog.domain.ProductStatus;
import com.commercex.catalog.domain.ProductVariant;
import com.commercex.catalog.infrastructure.BrandRepository;
import com.commercex.catalog.infrastructure.ProductRepository;
import com.commercex.category.CategoryApi;
import com.commercex.category.CategoryRef;
import com.commercex.common.error.NotFoundException;
import com.commercex.common.error.ValidationException;
import com.commercex.common.web.MoneyResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer-facing catalog reads. Only PUBLISHED products are ever visible here. */
@Service
public class ProductQueryService {

    private final ProductSearch search;
    private final ProductRepository products;
    private final BrandRepository brands;
    private final CategoryApi categories;

    ProductQueryService(ProductSearch search, ProductRepository products, BrandRepository brands,
            CategoryApi categories) {
        this.search = search;
        this.products = products;
        this.brands = brands;
        this.categories = categories;
    }

    public record SearchParams(String q, String category, List<String> brands, Long minPrice, Long maxPrice,
            Double minRating, String sort, int page, int size) {
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryView> search(SearchParams params) {
        if (params.minPrice() != null && params.maxPrice() != null && params.minPrice() > params.maxPrice()) {
            throw new ValidationException("minPrice", "must not be greater than maxPrice");
        }
        ProductSort sort;
        try {
            sort = ProductSort.parse(params.sort(), params.q() != null && !params.q().isBlank());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("sort", "must be one of relevance, newest, price_asc, price_desc, rating");
        }
        Set<Long> categoryIds = params.category() == null || params.category().isBlank()
                ? null
                : categories.activeSubtreeIds(params.category().trim().toLowerCase(Locale.ROOT));
        List<String> brandSlugs = params.brands() == null ? null
                : params.brands().stream().map(b -> b.trim().toLowerCase(Locale.ROOT)).toList();

        Page<ProductSummaryRow> rows = search.search(new ProductSearchCriteria(params.q(), categoryIds, brandSlugs,
                params.minPrice(), params.maxPrice(), params.minRating(), sort, params.page(), params.size()));

        Map<Long, CategoryRef> categoryRefs = categories.findByIds(
                rows.getContent().stream().map(ProductSummaryRow::categoryId).distinct().toList());
        return rows.map(r -> new ProductSummaryView(
                r.id(), r.slug(), r.name(), r.shortDescription(),
                MoneyResponse.of(r.priceMinor(), r.currency()),
                MoneyResponse.of(r.compareAtPriceMinor(), r.currency()),
                r.ratingAvg(), r.reviewCount(),
                r.brandSlug() == null ? null : new BrandRef(r.brandName(), r.brandSlug()),
                CategorySummary.from(categoryRefs.get(r.categoryId())),
                r.imageUrl() == null ? null : new ImageView(r.imageUrl(), r.imageAlt())));
    }

    @Transactional(readOnly = true)
    public ProductDetailView detail(String slug) {
        Product p = products.findBySlugAndStatus(slug, ProductStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Product", slug));
        BrandRef brand = p.getBrandId() == null ? null
                : brands.findById(p.getBrandId()).map(b -> new BrandRef(b.getName(), b.getSlug())).orElse(null);
        CategorySummary category = categories.findById(p.getCategoryId()).map(CategorySummary::from).orElse(null);
        ProductVariant cheapest = p.cheapestActiveVariant();
        List<String> optionNames = p.getOptionNames();

        return new ProductDetailView(
                p.getId(), p.getSlug(), p.getName(), p.getShortDescription(), p.getDescription(),
                MoneyResponse.of(p.getMinPriceMinor(), p.getCurrency()),
                cheapest == null ? null : MoneyResponse.of(cheapest.getCompareAtPriceMinor(), p.getCurrency()),
                p.getRatingAvg(), p.getReviewCount(), brand, category,
                p.activeOptionValues().entrySet().stream().map(e -> new OptionView(e.getKey(), e.getValue())).toList(),
                p.activeVariants().stream().map(v -> new VariantView(v.getId(), v.getSku(), v.getOptionValues(),
                        v.label(optionNames), MoneyResponse.of(v.getPriceMinor(), p.getCurrency()),
                        MoneyResponse.of(v.getCompareAtPriceMinor(), p.getCurrency()))).toList(),
                p.getImages().stream().map(ImageView::from).toList(),
                p.getTags().stream().sorted().toList(),
                p.getPublishedAt());
    }
}
