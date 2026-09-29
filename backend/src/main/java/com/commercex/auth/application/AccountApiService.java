package com.commercex.auth.application;

import com.commercex.auth.AccountApi;
import com.commercex.auth.CustomerInfo;
import com.commercex.auth.CustomerStats;
import com.commercex.auth.ShippingAddress;
import com.commercex.auth.domain.AddressDetails;
import com.commercex.auth.infrastructure.AddressRepository;
import com.commercex.auth.infrastructure.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountApiService implements AccountApi {

    private final UserRepository users;
    private final AddressRepository addresses;

    AccountApiService(UserRepository users, AddressRepository addresses) {
        this.users = users;
        this.addresses = addresses;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerInfo> customer(long userId) {
        return users.findById(userId).map(u -> new CustomerInfo(u.getId(), u.getEmail(), u.getFirstName(),
                u.getLastName(), u.isActive()));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerStats customerStats(Instant from) {
        return new CustomerStats(users.countCustomers(), users.countCustomersSince(from));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShippingAddress> address(long userId, long addressId) {
        return addresses.findByIdAndUserId(addressId, userId).map(a -> {
            AddressDetails d = a.details();
            return new ShippingAddress(d.fullName(), d.line1(), d.line2(), d.city(), d.region(), d.postalCode(),
                    d.country(), d.phone());
        });
    }
}
