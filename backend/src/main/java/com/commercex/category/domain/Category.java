package com.commercex.category.domain;

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
@Table(name = "categories")
public class Category {

    /** Root = depth 1. Keeps navigation menus and breadcrumb queries bounded. */
    public static final int MAX_DEPTH = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "categories_seq")
    @SequenceGenerator(name = "categories_seq", sequenceName = "categories_seq", allocationSize = 50)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    private String description;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Category() {
    }

    public static Category create(Long parentId, String name, String slug, String description, int position,
            boolean active, Instant now) {
        Category category = new Category();
        category.apply(parentId, name, slug, description, position, active, now);
        category.createdAt = now;
        return category;
    }

    public void update(Long parentId, String name, String slug, String description, int position, boolean active,
            Instant now) {
        apply(parentId, name, slug, description, position, active, now);
    }

    private void apply(Long parentId, String name, String slug, String description, int position, boolean active,
            Instant now) {
        this.parentId = parentId;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.position = position;
        this.active = active;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
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

    public int getPosition() {
        return position;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
