package com.commercex.auth.application;

import com.commercex.auth.domain.Address;
import com.commercex.auth.domain.AddressDetails;

public record AddressView(long id, AddressDetails details, boolean isDefault) {

    static AddressView from(Address address) {
        return new AddressView(address.getId(), address.details(), address.isDefaultAddress());
    }
}
