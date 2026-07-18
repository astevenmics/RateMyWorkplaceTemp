package com.myworktea.repository;

import com.myworktea.domain.RantVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RantVoteRepository extends JpaRepository<RantVote, Long> {

    Optional<RantVote> findByRantIdAndVoterId(Long rantId, String voterId);

    @Query("select v from RantVote v where v.voterId = :voterId and v.rant.id in :rantIds")
    List<RantVote> findByVoterIdAndRantIdIn(@Param("voterId") String voterId, @Param("rantIds") List<Long> rantIds);

    @Modifying
    @Query("delete from RantVote v where v.rant.id = :rantId")
    void deleteByRantId(@Param("rantId") Long rantId);
}
