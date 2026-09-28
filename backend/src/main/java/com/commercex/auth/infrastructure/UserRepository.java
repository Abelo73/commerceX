package com.commercex.auth.infrastructure;

import com.commercex.auth.domain.Role;
import com.commercex.auth.domain.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    /** {@code email} is CITEXT, so this is case-insensitive at the database level. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select count(u) > 0 from User u join u.roles r where r = :role")
    boolean existsWithRole(Role role);

    Page<User> findByEmailContainingIgnoreCase(String emailFragment, Pageable pageable);
}
