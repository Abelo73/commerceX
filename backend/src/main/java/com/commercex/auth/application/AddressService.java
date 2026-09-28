package com.commercex.auth.application;

import com.commercex.auth.domain.Address;
import com.commercex.auth.domain.AddressDetails;
import com.commercex.auth.infrastructure.AddressRepository;
import com.commercex.common.error.ConflictException;
import com.commercex.common.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Address book. Every query is scoped by owner, so users can never reach each other's addresses. */
@Service
public class AddressService {

    static final int MAX_ADDRESSES = 20;

    private final AddressRepository addresses;
    private final Clock clock;

    AddressService(AddressRepository addresses, Clock clock) {
        this.addresses = addresses;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AddressView> list(long userId) {
        return addresses.findAllByUserIdOrderByIdAsc(userId).stream().map(AddressView::from).toList();
    }

    @Transactional(readOnly = true)
    public AddressView get(long userId, long addressId) {
        return AddressView.from(load(userId, addressId));
    }

    @Transactional
    public AddressView create(long userId, AddressDetails details, boolean makeDefault) {
        long existing = addresses.countByUserId(userId);
        if (existing >= MAX_ADDRESSES) {
            throw new ConflictException("An account can store at most " + MAX_ADDRESSES + " addresses");
        }
        Instant now = clock.instant();
        Address address = addresses.save(Address.create(userId, normalize(details), now));
        if (makeDefault || existing == 0) {
            address = makeDefault(userId, address, now);
        }
        return AddressView.from(address);
    }

    @Transactional
    public AddressView update(long userId, long addressId, AddressDetails details, boolean makeDefault) {
        Instant now = clock.instant();
        Address address = load(userId, addressId);
        address.update(normalize(details), now);
        if (makeDefault && !address.isDefaultAddress()) {
            address = makeDefault(userId, address, now);
        }
        return AddressView.from(address);
    }

    /** Deleting the default address promotes the oldest remaining one. */
    @Transactional
    public void delete(long userId, long addressId) {
        Address address = load(userId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        addresses.delete(address);
        addresses.flush();
        if (wasDefault) {
            addresses.findAllByUserIdOrderByIdAsc(userId).stream().findFirst()
                    .ifPresent(next -> next.markDefault(true, clock.instant()));
        }
    }

    private Address makeDefault(long userId, Address address, Instant now) {
        // Clear first, then set: the partial unique index allows only one default per user.
        addresses.clearDefault(userId);
        Address reloaded = load(userId, address.getId());
        reloaded.markDefault(true, now);
        return addresses.saveAndFlush(reloaded);
    }

    private Address load(long userId, long addressId) {
        return addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("Address", addressId));
    }

    private static AddressDetails normalize(AddressDetails d) {
        return new AddressDetails(trim(d.label()), d.fullName().trim(), d.line1().trim(), trim(d.line2()),
                d.city().trim(), trim(d.region()), d.postalCode().trim(), d.country().trim().toUpperCase(),
                trim(d.phone()));
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
