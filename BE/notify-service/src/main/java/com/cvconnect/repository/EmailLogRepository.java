package com.cvconnect.repository;

import com.cvconnect.entity.EmailLog;
import com.cvconnect.enums.SendEmailStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    @Query("SELECT e FROM EmailLog e WHERE e.status = :status AND e.createdAt <= CURRENT_TIMESTAMP - 1 MINUTE " +
            "ORDER BY e.createdAt ASC LIMIT :limit")
        List<EmailLog> findByStatus(@Param("status") SendEmailStatus status, @Param("limit") Long limit);

    @Query("SELECT e FROM EmailLog e WHERE e.candidateInfoId = :candidateInfoId AND e.jobAdId = :jobAdId AND e.orgId = :orgId")
    List<EmailLog> findByCandidateInfoIdAndJobAdIdAndOrgId(@Param("candidateInfoId") Long candidateInfoId,
                                                            @Param("jobAdId") Long jobAdId,
                                                            @Param("orgId") Long orgId);
}
