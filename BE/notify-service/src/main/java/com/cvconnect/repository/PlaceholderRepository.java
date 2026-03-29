package com.cvconnect.repository;

import com.cvconnect.entity.Placeholder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaceholderRepository extends JpaRepository<Placeholder, Long> {
    @Query("SELECT p FROM Placeholder p " +
            "JOIN EmailTemplatePlaceholder el ON el.placeholderId = p.id " +
            "WHERE el.emailTemplateId = :emailTemplateId")
    List<Placeholder> findByEmailTemplateId(@Param("emailTemplateId") Long emailTemplateId);
}
