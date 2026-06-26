package com.ratemywork.repository;

import com.ratemywork.domain.Role;
import com.ratemywork.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Page<User> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            String username, String displayName, Pageable pageable);

    long countByEmailVerifiedTrueAndPhoneVerifiedTrue();

    long countByRole(Role role);

    long countByCreatedAtAfter(Instant instant);
}
