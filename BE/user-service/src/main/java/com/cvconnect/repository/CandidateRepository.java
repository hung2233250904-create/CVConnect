package com.cvconnect.repository;

import com.cvconnect.entity.Candidate;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    Optional<Candidate> findByUserId(@NotNull Long userId);

    @Query("""
            select count(distinct c) from Candidate c
            where c.createdAt between :startTime and :endTime
    """)
    Long numberOfNewCandidate(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
}
