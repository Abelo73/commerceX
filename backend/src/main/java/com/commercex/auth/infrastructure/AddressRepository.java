package com.commercex.auth.infrastructure;

import com.commercex.auth.domain.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AddressRepository extends JpaRepository<Address, Long> {

    /** Scoped by owner: another user's address is indistinguishable from a missing one. */
    Optional<Address> findByIdAndUserId(long id, long userId);

    List<Address> findAllByUserIdOrderByIdAsc(long userId);

    long countByUserId(long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Address a set a.defaultAddress = false where a.userId = :userId and a.defaultAddress = true")
    void clearDefault(long userId);
}
