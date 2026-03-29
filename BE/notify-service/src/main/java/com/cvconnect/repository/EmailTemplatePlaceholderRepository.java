package com.cvconnect.repository;

import com.cvconnect.entity.EmailTemplatePlaceholder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailTemplatePlaceholderRepository extends JpaRepository<EmailTemplatePlaceholder, Long> {

    @Modifying
    @Query("DELETE FROM EmailTemplatePlaceholder etp WHERE etp.emailTemplateId = :emailTemplateId AND etp.placeholderId IN :placeholderIds")
    void deleteByEmailTemplateIdAndPlaceholderIdIn(@Param("emailTemplateId") Long emailTemplateId, @Param("placeholderIds") List<Long> placeholderIds);
}
