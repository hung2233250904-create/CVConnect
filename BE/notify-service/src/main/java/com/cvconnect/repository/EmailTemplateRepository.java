package com.cvconnect.repository;

import com.cvconnect.dto.EmailTemplateFilterRequest;
import com.cvconnect.entity.EmailTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM EmailTemplate e WHERE e.code = :code AND e.orgId = :orgId")
        boolean existsByCodeAndOrgId(@Param("code") String code, @Param("orgId") Long orgId);

    @Query("SELECT DISTINCT e FROM EmailTemplate e WHERE e.id IN :ids AND e.orgId = :orgId")
        List<EmailTemplate> findByIdsAndOrgId(@Param("ids") List<Long> ids, @Param("orgId") Long orgId);

    @Query("SELECT et FROM EmailTemplate et WHERE " +
            "(:#{#request.code} IS NULL OR LOWER(et.code) LIKE LOWER(CONCAT('%', :#{#request.code}, '%'))) " +
            "AND (:#{#request.name} IS NULL OR LOWER(et.name) LIKE LOWER(CONCAT('%', :#{#request.name}, '%'))) " +
            "AND (:#{#request.subject} IS NULL OR LOWER(et.subject) LIKE LOWER(CONCAT('%', :#{#request.subject}, '%'))) " +
            "AND (:#{#request.isActive} IS NULL OR et.isActive = :#{#request.isActive}) " +
            "AND (et.createdAt >= COALESCE(:#{#request.createdAtStart}, et.createdAt)) " +
            "AND (et.createdAt <= COALESCE(:#{#request.createdAtEnd}, et.createdAt)) " +
            "AND (COALESCE(:#{#request.updatedAtStart}, NULL) IS NULL OR (et.updatedAt IS NOT NULL AND et.updatedAt >= :#{#request.updatedAtStart})) " +
            "AND (COALESCE(:#{#request.updatedAtEnd}, NULL) IS NULL OR (et.updatedAt IS NOT NULL AND et.updatedAt <= :#{#request.updatedAtEnd})) " +
            "AND (:#{#request.createdBy} IS NULL OR LOWER(et.createdBy) LIKE LOWER(CONCAT('%', :#{#request.createdBy}, '%'))) " +
            "AND (:#{#request.updatedBy} IS NULL OR LOWER(et.updatedBy) LIKE LOWER(CONCAT('%', :#{#request.updatedBy}, '%'))) " +
            "AND (:#{#request.orgId} IS NULL OR et.orgId = :#{#request.orgId})"
    )
        Page<EmailTemplate> filter(@Param("request") EmailTemplateFilterRequest request, Pageable pageable);

    @Query("SELECT et FROM EmailTemplate et " +
            "WHERE et.orgId = :orgId " +
            "AND (:active IS NULL OR et.isActive = :active)")
        List<EmailTemplate> findByOrgIdAndIsActive(@Param("orgId") Long orgId, @Param("active") Boolean active);

}
