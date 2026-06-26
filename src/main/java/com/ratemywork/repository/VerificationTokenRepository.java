package com.ratemywork.repository;

import com.ratemywork.domain.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findFirstByUserIdAndChannelAndConsumedFalseOrderByIdDesc(
            Long userId, VerificationToken.Channel channel);

    @Modifying
    @Query("delete from VerificationToken t where t.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
