package com.myworktea.repository;

import com.myworktea.domain.Role;
import com.myworktea.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Page<User> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(String username, String displayName, Pageable pageable);

    long countByEmailVerifiedTrue();

    long countByRole(Role role);

    long countByCreatedAtAfter(Instant instant);

    /** Self-deleted accounts whose grace period has elapsed and are due for permanent purge. */
    List<User> findByDeletionRequestedAtNotNullAndDeletionRequestedAtBefore(Instant cutoff);
}