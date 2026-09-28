package com.commercex.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "brands")
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "brands_seq")
    @SequenceGenerator(name = "brands_seq", sequenceName = "brands_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    private String description;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Brand() {
    }

    public static Brand create(String name, String slug, String description, String logoUrl, boolean active,
            Instant now) {
        Brand brand = new Brand();
        brand.update(name, slug, description, logoUrl, active, now);
        brand.createdAt = now;
        return brand;
    }

    public void update(String name, String slug, String description, String logoUrl, boolean active, Instant now) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.logoUrl = logoUrl;
        this.active = active;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
