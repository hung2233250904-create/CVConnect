package com.cvconnect.repository;

import com.cvconnect.dto.jobAd.JobAdOrgFilterProjection;
import com.cvconnect.dto.jobAd.JobAdOrgFilterRequest;
import com.cvconnect.dto.jobAd.JobAdOutsideFilterRequest;
import com.cvconnect.dto.jobAd.JobAdProjection;
import com.cvconnect.entity.JobAd;
import com.cvconnect.enums.JobAdStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobAdRepository extends JpaRepository<JobAd, Integer> {

    @Query(value = "SELECT MAX(CAST(SUBSTRING(code, LENGTH(:prefix) + 1) AS BIGINT)) " +
            "FROM job_ad " +
            "WHERE org_id = :orgId AND code LIKE CONCAT(:prefix, '%')",
            nativeQuery = true)
        Long getSuffixCodeMax(@Param("orgId") Long orgId, @Param("prefix") String prefix);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END " +
            "FROM Department d " +
            "JOIN Position p ON p.departmentId = d.id AND p.isActive = true " +
            "WHERE d.isActive = true AND d.orgId = :orgId AND p.id = :positionId")
        boolean existsByOrgIdAndPositionId(@Param("orgId") Long orgId, @Param("positionId") Long positionId);

    @Query("SELECT ja FROM JobAd ja WHERE ja.id = :id")
        JobAd findById(@Param("id") Long id);

    @Query(value = """
        SELECT distinct
            ja.id as id,
            ja.code as code,
            ja.title AS title,
            p.id AS positionId,
            p.name AS positionName,
            d.id AS departmentId,
            d.name AS departmentName,
            ja.dueDate AS dueDate,
            ja.quantity AS quantity,
            ja.hrContactId AS hrContactId,
            ja.jobAdStatus AS jobAdStatus,
            ja.isPublic AS isPublic,
            ja.keyCodeInternal AS keyCodeInternal,
            ja.createdBy AS createdBy,
            ja.createdAt AS createdAt,
            ja.updatedBy AS updatedBy,
            ja.updatedAt AS updatedAt
        FROM JobAd ja
        JOIN Position p ON ja.positionId = p.id
        JOIN Department d ON p.departmentId = d.id
        JOIN JobAdProcess jap ON jap.jobAdId = ja.id
        LEFT JOIN Calendar c ON c.jobAdProcessId = jap.id
        LEFT JOIN InterviewPanel ip ON ip.calendarId = c.id
        WHERE ja.orgId = :#{#request.orgId}
          AND (:#{#request.keyword} IS NULL OR LOWER(ja.title) LIKE LOWER(CONCAT('%', :#{#request.keyword}, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :#{#request.keyword}, '%')))
          AND (:#{#request.jobAdStatus?.name()} IS NULL OR ja.jobAdStatus = :#{#request.jobAdStatus?.name()})
          AND (:#{#request.isPublic} IS NULL OR ja.isPublic = :#{#request.isPublic})
          AND (:#{#request.departmentIds} IS NULL OR d.id IN :#{#request.departmentIds})
          AND (:#{#request.hrContactId} IS NULL OR ja.hrContactId = :#{#request.hrContactId})
          AND (:#{#request.createdBy} IS NULL OR LOWER(ja.createdBy) LIKE LOWER(CONCAT('%', :#{#request.createdBy}, '%')))
          AND (ja.createdAt >= COALESCE(:#{#request.createdAtStart}, ja.createdAt))
          AND (ja.createdAt <= COALESCE(:#{#request.createdAtEnd}, ja.createdAt))
          AND (ja.dueDate >= COALESCE(:#{#request.dueDateStart}, ja.dueDate))
          AND (ja.dueDate <= COALESCE(:#{#request.dueDateEnd}, ja.dueDate))
          AND (:participantId IS NULL OR ja.hrContactId = :participantId OR (ip.interviewerId IS NOT NULL AND ip.interviewerId = :participantId))
    """,
    countQuery = """
        SELECT count(distinct ja.id)
        FROM JobAd ja
        JOIN Position p ON ja.positionId = p.id
        JOIN Department d ON p.departmentId = d.id
        JOIN JobAdProcess jap ON jap.jobAdId = ja.id
        LEFT JOIN Calendar c ON c.jobAdProcessId = jap.id
        LEFT JOIN InterviewPanel ip ON ip.calendarId = c.id
        WHERE ja.orgId = :#{#request.orgId}
          AND (:#{#request.keyword} IS NULL OR LOWER(ja.title) LIKE LOWER(CONCAT('%', :#{#request.keyword}, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :#{#request.keyword}, '%')))
          AND (:#{#request.jobAdStatus?.name()} IS NULL OR ja.jobAdStatus = :#{#request.jobAdStatus?.name()})
          AND (:#{#request.isPublic} IS NULL OR ja.isPublic = :#{#request.isPublic})
          AND (:#{#request.departmentIds} IS NULL OR d.id IN :#{#request.departmentIds})
          AND (:#{#request.hrContactId} IS NULL OR ja.hrContactId = :#{#request.hrContactId})
          AND (:#{#request.createdBy} IS NULL OR LOWER(ja.createdBy) LIKE LOWER(CONCAT('%', :#{#request.createdBy}, '%')))
          AND (ja.createdAt >= COALESCE(:#{#request.createdAtStart}, ja.createdAt))
          AND (ja.createdAt <= COALESCE(:#{#request.createdAtEnd}, ja.createdAt))
          AND (ja.dueDate >= COALESCE(:#{#request.dueDateStart}, ja.dueDate))
          AND (ja.dueDate <= COALESCE(:#{#request.dueDateEnd}, ja.dueDate))
          AND (:participantId IS NULL OR ja.hrContactId = :participantId OR (ip.interviewerId IS NOT NULL AND ip.interviewerId = :participantId))
    """
    )
    Page<JobAdOrgFilterProjection> filterJobAdsForOrg(@Param("request") JobAdOrgFilterRequest request, Pageable pageable, @Param("participantId") Long participantId);

    @Query("select distinct ja from JobAd ja " +
            "join JobAdProcess jap on jap.jobAdId = ja.id " +
            "where jap.id = :jobAdProcessId")
    JobAd findByJobAdProcessId(@Param("jobAdProcessId") Long jobAdProcessId);

    @Query("""
        SELECT ja
        FROM JobAd ja
        JOIN JobAdProcess jap ON jap.jobAdId = ja.id
        LEFT JOIN Calendar c ON c.jobAdProcessId = jap.id
        LEFT JOIN InterviewPanel ip ON ip.calendarId = c.id
        WHERE ja.orgId = :orgId and ja.id = :jobAdId
        AND (:participantId IS NULL OR ja.hrContactId = :participantId OR (ip.interviewerId IS NOT NULL AND ip.interviewerId = :participantId))
    """)
    JobAd getJobAdOrgDetailById(@Param("jobAdId") Long jobAdId, @Param("orgId") Long orgId, @Param("participantId") Long participantId);

    @Query("""
        SELECT distinct ja
        FROM JobAd ja
        JOIN JobAdProcess jap ON jap.jobAdId = ja.id
        LEFT JOIN Calendar c ON c.jobAdProcessId = jap.id
        LEFT JOIN InterviewPanel ip ON ip.calendarId = c.id
        WHERE ja.orgId = :orgId
          AND (:participantId IS NULL OR ja.hrContactId = :participantId OR (ip.interviewerId IS NOT NULL AND ip.interviewerId = :participantId))
    """)
    Page<JobAd> getJobAdsByParticipantId(@Param("orgId") Long orgId, @Param("participantId") Long participantId, Pageable pageable);

    @Query(value = """
        select * from FUNC_FILTER_JOB_AD_OUTSIDE(
            :keyword,
            :isShowExpired,
            :careerIds,
            :levelIds,
            :jobAdLocation,
            :isRemote,
            :salaryFrom,
            :salaryTo,
            :negotiable,
            :jobType,
            :searchOrg,
            :orgId,
            :limit,
            :offset,
            :sortBy,
            :sortDirection
        )
    """, nativeQuery = true)
    List<JobAdProjection> filterJobAdsForOutsideFunction(
            @Param("keyword") String keyword,
            @Param("isShowExpired") Boolean isShowExpired,
            @Param("careerIds") Long[] careerIds,
            @Param("levelIds") Long[] levelIds,
            @Param("jobAdLocation") String jobAdLocation,
            @Param("isRemote") Boolean isRemote,
            @Param("salaryFrom") Integer salaryFrom,
            @Param("salaryTo") Integer salaryTo,
            @Param("negotiable") Boolean negotiable,
            @Param("jobType") String jobType,
            @Param("searchOrg") Boolean searchOrg,
            @Param("orgId") Long orgId,
            @Param("limit") Integer limit,
            @Param("offset") Integer offset,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection
    );

    @Query(value = """
        select * from FUNC_WORKING_LOCATION_OUTSIDE(
            :keyword,
            :isShowExpired,
            :careerIds,
            :levelIds,
            :jobAdLocation,
            :isRemote,
            :salaryFrom,
            :salaryTo,
            :negotiable,
            :jobType,
            :searchOrg,
            :orgId
        )
    """, nativeQuery = true)
    List<Object[]> getWorkingLocationByFilterFunction(
            @Param("keyword") String keyword,
            @Param("isShowExpired") Boolean isShowExpired,
            @Param("careerIds") Long[] careerIds,
            @Param("levelIds") Long[] levelIds,
            @Param("jobAdLocation") String jobAdLocation,
            @Param("isRemote") Boolean isRemote,
            @Param("salaryFrom") Integer salaryFrom,
            @Param("salaryTo") Integer salaryTo,
            @Param("negotiable") Boolean negotiable,
            @Param("jobType") String jobType,
            @Param("searchOrg") Boolean searchOrg,
            @Param("orgId") Long orgId
    );

    @Query(value = """
            select ja.* from (
                select ja.id,
                       ts_rank(
                               to_tsvector(ja.title || ' ' || replace(ja.keyword, ';', ' ')),
                               plainto_tsquery(:keyword)
                       ) as rank_score,
                       similarity(ja.title || ' ' || replace(ja.keyword, ';', ' '), :keyword) as sim_score
                from job_ad ja
                where ja.is_public = true
                and ja.job_ad_status = 'OPEN'
                and ja.due_date >= CURRENT_DATE
                and ja.id <> :jobAdId
            ) as ranked_jobs
            join job_ad ja on ja.id = ranked_jobs.id
            where rank_score > 0.05 or sim_score > 0.3
            order by 0.3 * rank_score + 0.05  * sim_score desc
            limit 10
        """, nativeQuery = true)
    List<JobAd> findRelatedJobAds(@Param("keyword") String keyword, @Param("jobAdId") Long jobAdId);

    @Query(value = """
        WITH all_jobs AS (
            SELECT ja.id,
                   COALESCE(jas.view_count, 0) AS view_count
            FROM job_ad ja
            LEFT JOIN job_ad_statistic jas ON jas.job_ad_id = ja.id
            WHERE ja.is_public = true
            AND ja.job_ad_status = 'OPEN'
            AND ja.due_date >= CURRENT_DATE
        ),
        featured_jobs AS (
            SELECT id
            FROM all_jobs
            ORDER BY view_count DESC, id DESC
            LIMIT :pageSize OFFSET :offset
        )
        SELECT
            ja.id as id,
            ja.code as code,
            ja.title as title,
            ja.org_id as orgId,
            ja.position_id as positionId,
            ja.job_type as jobType,
            ja.due_date as dueDate,
            ja.quantity as quantity,
            ja.salary_type as salaryType,
            ja.salary_from as salaryFrom,
            ja.salary_to as salaryTo,
            ja.currency_type as currencyType,
            ja.keyword as keyword,
            ja.description as description,
            ja.requirement as requirement,
            ja.benefit as benefit,
            ja.hr_contact_id as hrContactId,
            ja.job_ad_status as jobAdStatus,
            ja.is_public as isPublic,
            ja.is_auto_send_email as isAutoSendEmail,
            ja.email_template_id as emailTemplateId,
            ja.is_remote as isRemote,
            ja.is_all_level as isAllLevel,
            ja.is_active as isActive,
            ja.is_deleted as isDeleted,
            ja.created_by as createdBy,
            ja.created_at as createdAt,
            ja.updated_by as updatedBy,
            ja.updated_at as updatedAt,
            ja.key_code_internal as keyCodeInternal,
            (SELECT COUNT(*) FROM all_jobs) AS totalElement
        FROM featured_jobs pj
        JOIN job_ad ja ON ja.id = pj.id;
    """, nativeQuery = true)
    List<JobAdProjection> getFeaturedJobAds(@Param("pageSize") Integer pageSize, @Param("offset") Long offset);


    @Query(value = """
        with rank_calculation as (
            select ja.id,
                 ts_rank(
                        to_tsvector(ja.title || ' ' || replace(ja.keyword, ';', ' ')),
                        plainto_tsquery(:keyword)
                ) as rank_score,
                 similarity(ja.title || ' ' || replace(ja.keyword, ';', ' '), :keyword) as sim_score
            from job_ad ja
            where ja.is_public = true
            and ja.job_ad_status = 'OPEN'
            and ja.due_date >= CURRENT_DATE
        ),
        all_jobs as (
            select rc.*
            from rank_calculation rc
            where rc.rank_score > 0.05 or rc.sim_score > 0.3
        ),
        suitable_jobs as (
            select aj.id
            from all_jobs aj
            order by 0.3 * aj.rank_score + 0.05 * aj.sim_score desc
            LIMIT :pageSize OFFSET :offset
        )
        SELECT
            ja.id as id,
            ja.code as code,
            ja.title as title,
            ja.org_id as orgId,
            ja.position_id as positionId,
            ja.job_type as jobType,
            ja.due_date as dueDate,
            ja.quantity as quantity,
            ja.salary_type as salaryType,
            ja.salary_from as salaryFrom,
            ja.salary_to as salaryTo,
            ja.currency_type as currencyType,
            ja.keyword as keyword,
            ja.description as description,
            ja.requirement as requirement,
            ja.benefit as benefit,
            ja.hr_contact_id as hrContactId,
            ja.job_ad_status as jobAdStatus,
            ja.is_public as isPublic,
            ja.is_auto_send_email as isAutoSendEmail,
            ja.email_template_id as emailTemplateId,
            ja.is_remote as isRemote,
            ja.is_all_level as isAllLevel,
            ja.is_active as isActive,
            ja.is_deleted as isDeleted,
            ja.created_by as createdBy,
            ja.created_at as createdAt,
            ja.updated_by as updatedBy,
            ja.updated_at as updatedAt,
            ja.key_code_internal as keyCodeInternal,
            (SELECT COUNT(*) FROM all_jobs) AS totalElement
        FROM suitable_jobs sj
        JOIN job_ad ja ON ja.id = sj.id
    """, nativeQuery = true)
    List<JobAdProjection> getSuitableJobAds(@Param("keyword") String keyword, @Param("pageSize") Integer pageSize, @Param("offset") Long offset);

    @Modifying
    @Query("UPDATE JobAd ja SET ja.jobAdStatus = :jobAdStatus WHERE ja.orgId IN :orgIds")
    void updateJobAdStatusByOrgIds(@Param("orgIds") List<Long> orgIds, @Param("jobAdStatus") String jobAdStatus);

}
