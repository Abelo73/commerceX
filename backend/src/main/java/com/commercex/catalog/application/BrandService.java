package com.commercex.catalog.application;

import com.commercex.catalog.application.CatalogViews.BrandView;
import com.commercex.catalog.domain.Brand;
import com.commercex.catalog.infrastructure.BrandRepository;
import com.commercex.catalog.infrastructure.ProductRepository;
import com.commercex.common.audit.AuditLog;
import com.commercex.common.error.ConflictException;
import com.commercex.common.error.NotFoundException;
import com.commercex.common.persistence.Versions;
import com.commercex.common.text.Slugs;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrandService {

    private final BrandRepository brands;
    private final ProductRepository products;
    private final AuditLog auditLog;
    private final Clock clock;

    BrandService(BrandRepository brands, ProductRepository products, AuditLog auditLog, Clock clock) {
        this.brands = brands;
        this.products = products;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    public record BrandCommand(String name, String slug, String description, String logoUrl, boolean active,
            Long version) {
    }

    @Transactional(readOnly = true)
    public List<BrandView> listActive() {
        return brands.findAllByActiveTrueOrderByNameAsc().stream().map(BrandView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BrandView> listAll() {
        return brands.findAllByOrderByNameAsc().stream().map(BrandView::from).toList();
    }

    @Transactional
    public BrandView create(long actorId, BrandCommand command) {
        String slug = Slugs.resolve(command.slug(), command.name());
        if (brands.existsBySlug(slug)) {
            throw new ConflictException("Slug '" + slug + "' is already in use");
        }
        Brand brand = brands.save(Brand.create(command.name().trim(), slug, blankToNull(command.description()),
                blankToNull(command.logoUrl()), command.active(), clock.instant()));
        auditLog.record(actorId, "BRAND_CREATED", "brand", brand.getId(), Map.of("slug", slug));
        return BrandView.from(brand);
    }

    @Transactional
    public BrandView update(long actorId, long id, BrandCommand command) {
        Brand brand = brands.findById(id).orElseThrow(() -> new NotFoundException("Brand", id));
        Versions.require(brand.getVersion(), command.version());
        String slug = Slugs.resolve(command.slug(), command.name());
        if (brands.existsBySlugAndIdNot(slug, id)) {
            throw new ConflictException("Slug '" + slug + "' is already in use");
        }
        boolean renamed = !brand.getName().equals(command.name().trim());
        brand.update(command.name().trim(), slug, blankToNull(command.description()), blankToNull(command.logoUrl()),
                command.active(), clock.instant());
        brands.flush();
        if (renamed) {
            brands.refreshProductKeywords(id, brand.getName());
        }
        auditLog.record(actorId, "BRAND_UPDATED", "brand", id, Map.of("slug", slug));
        return BrandView.from(brand);
    }

    @Transactional
    public void delete(long actorId, long id) {
        Brand brand = brands.findById(id).orElseThrow(() -> new NotFoundException("Brand", id));
        if (products.existsByBrandId(id)) {
            throw new ConflictException("Brand is used by products; deactivate it instead");
        }
        brands.delete(brand);
        auditLog.record(actorId, "BRAND_DELETED", "brand", id, Map.of("slug", brand.getSlug()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
