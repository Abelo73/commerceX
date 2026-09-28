package com.commercex.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "addresses_seq")
    @SequenceGenerator(name = "addresses_seq", sequenceName = "addresses_seq", allocationSize = 50)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    private String label;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String line1;

    private String line2;

    @Column(nullable = false)
    private String city;

    private String region;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 2)
    private String country;

    private String phone;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Address() {
    }

    public static Address create(long userId, AddressDetails details, Instant now) {
        Address address = new Address();
        address.userId = userId;
        address.apply(details);
        address.createdAt = now;
        address.updatedAt = now;
        return address;
    }

    public void update(AddressDetails details, Instant now) {
        apply(details);
        this.updatedAt = now;
    }

    public void markDefault(boolean isDefault, Instant now) {
        this.defaultAddress = isDefault;
        this.updatedAt = now;
    }

    private void apply(AddressDetails d) {
        this.label = d.label();
        this.fullName = d.fullName();
        this.line1 = d.line1();
        this.line2 = d.line2();
        this.city = d.city();
        this.region = d.region();
        this.postalCode = d.postalCode();
        this.country = d.country();
        this.phone = d.phone();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
    }

    public AddressDetails details() {
        return new AddressDetails(label, fullName, line1, line2, city, region, postalCode, country, phone);
    }
}
