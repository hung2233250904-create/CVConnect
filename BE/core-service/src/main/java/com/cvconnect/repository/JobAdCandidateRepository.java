package com.cvconnect.repository;

import com.cvconnect.dto.candidateInfoApply.CandidateInfoApplyProjection;
import com.cvconnect.dto.jobAdCandidate.*;
import com.cvconnect.entity.JobAdCandidate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobAdCandidateRepository extends JpaRepository<JobAdCandidate, Long> {

    @Query("SELECT CASE WHEN COUNT(jc) > 0 THEN true ELSE false END " +
            "FROM JobAdCandidate jc " +
            "JOIN CandidateInfoApply cia ON cia.id = jc.candidateInfoId " +
            "WHERE jc.jobAdId = :jobAdId AND cia.candidateId = :candidateId")
    boolean existsByJobAdIdAndCandidateId(Long jobAdId, Long candidateId);

    @Query("SELECT CASE WHEN COUNT(jc) > 0 THEN true ELSE false END " +
            "FROM JobAdCandidate jc " +
            "WHERE jc.jobAdId = :jobAdId AND jc.candidateInfoId = :candidateInfoId")
    boolean existsByJobAdIdAndCandidateInfoId(Long jobAdId, Long candidateInfoId);

    @Query(value = """
        select cia.id as id, cia.fullName as fullName, cia.email as email, cia.phone as phone, l.id as levelId, l.name as levelName,
               count(distinct jac.jobAdId) as numOfApply, max(jac.applyDate) as applyDate
        from CandidateInfoApply as cia
        join JobAdCandidate as jac on jac.candidateInfoId = cia.id
        join JobAd as ja on ja.id = jac.jobAdId
        join JobAdProcessCandidate as japc on japc.jobAdCandidateId = jac.id and japc.isCurrentProcess = true
        join JobAdProcess as jap on jap.id = japc.jobAdProcessId
        left join CandidateSummaryOrg as cso on cso.candidateInfoId = cia.id and cso.orgId = :orgId
        left join Level l on l.id = cso.levelId
        left join CalendarCandidateInfo cci on cci.candidateInfoId = cia.id
        left join Calendar c on c.id = cci.calendarId
        left join InterviewPanel ip on ip.calendarId = c.id
        where
        (:#{#request.fullName} is null or lower(cia.fullName) like lower(concat('%', :#{#request.fullName}, '%')))
        and (:#{#request.email} is null or lower(cia.email) like lower(concat('%', :#{#request.email}, '%')))
        and (:#{#request.phone} is null or lower(cia.phone) like lower(concat('%', :#{#request.phone}, '%')))
        and (:#{#request.levelIds == null || #request.levelIds.isEmpty()} = true or (cso.levelId is not null and cso.levelId in :#{#request.levelIds}))
        and (:#{#request.jobAdTitle} is null or lower(ja.title) like lower(concat('%', :#{#request.jobAdTitle}, '%')))
        and (:#{#request.candidateStatuses == null || #request.candidateStatuses.isEmpty()} = true or jac.candidateStatus in :#{#request.candidateStatuses})
        and (:#{#request.processTypes ==  null || #request.processTypes.isEmpty()} = true or jap.processTypeId in :#{#request.processTypes})
        and (COALESCE(:#{#request.applyDateStart}, NULL) IS NULL OR jac.applyDate >= :#{#request.applyDateStart})
        and (COALESCE(:#{#request.applyDateEnd}, NULL) IS NULL OR jac.applyDate <= :#{#request.applyDateEnd})
        and (:#{#request.hrContactId} is null or ja.hrContactId = :#{#request.hrContactId})
        and (:participantId is null or ja.hrContactId = :participantId or ip.interviewerId = :participantId)
        and (:orgId is null or ja.orgId = :orgId)
        group by cia.id, cia.fullName, cia.email, cia.phone, l.id, l.name
        having (:#{#request.numOfApplyStart} is null or count(distinct jac.jobAdId) >= :#{#request.numOfApplyStart})
            and (:#{#request.numOfApplyEnd} is null or count(distinct jac.jobAdId) <= :#{#request.numOfApplyEnd})
    """ )
    Page<CandidateInfoApplyProjection> filter(CandidateFilterRequest request, Long orgId, Long participantId, Pageable pageable);

    @Query(value = """
        select distinct jac.candidateInfoId as candidateInfoId,
                        ja.id as jobAdId,
                        ja.title as jobAdTitle,
                        jac.candidateStatus as candidateStatus,
                        pt.id as processTypeId,
                        pt.name as processTypeName,
                        pt.code as processTypeCode,
                        jac.applyDate as applyDate,
                        ja.hrContactId as hrContactId
        from JobAdCandidate as jac
        join JobAd as ja on ja.id = jac.jobAdId
        join JobAdProcessCandidate as japc on japc.jobAdCandidateId = jac.id and japc.isCurrentProcess = true
        join JobAdProcess as jap on jap.id = japc.jobAdProcessId
        join ProcessType as pt on pt.id = jap.processTypeId
        join CandidateInfoApply as cia on cia.id = jac.candidateInfoId
        left join CandidateSummaryOrg as cso on cso.candidateInfoId = cia.id and cso.orgId = :orgId
        left join CalendarCandidateInfo cci on cci.candidateInfoId = cia.id
        left join Calendar c on c.id = cci.calendarId
        left join InterviewPanel ip on ip.calendarId = c.id
        where
        (:#{#request.fullName} is null or lower(cia.fullName) like lower(concat('%', :#{#request.fullName}, '%')))
        and (:#{#request.email} is null or lower(cia.email) like lower(concat('%', :#{#request.email}, '%')))
        and (:#{#request.phone} is null or lower(cia.phone) like lower(concat('%', :#{#request.phone}, '%')))
        and (:#{#request.levelIds == null || #request.levelIds.isEmpty()} = true or (cso.levelId is not null and cso.levelId in :#{#request.levelIds}))
        and (:#{#request.jobAdTitle} is null or lower(ja.title) like lower(concat('%', :#{#request.jobAdTitle}, '%')))
        and (:#{#request.candidateStatuses == null || #request.candidateStatuses.isEmpty()} = true or jac.candidateStatus in :#{#request.candidateStatuses})
        and (:#{#request.processTypes ==  null || #request.processTypes.isEmpty()} = true or jap.processTypeId in :#{#request.processTypes})
        and (COALESCE(:#{#request.applyDateStart}, NULL) IS NULL OR jac.applyDate >= :#{#request.applyDateStart})
        and (COALESCE(:#{#request.applyDateEnd}, NULL) IS NULL OR jac.applyDate <= :#{#request.applyDateEnd})
        and (:#{#request.hrContactId} is null or ja.hrContactId = :#{#request.hrContactId})
        and (:#{#candidateInfoIds == null || #candidateInfoIds.isEmpty()} = true or jac.candidateInfoId in :candidateInfoIds)
        and (:participantId is null or ja.hrContactId = :participantId or ip.interviewerId = :participantId)
        and (:orgId is null or ja.orgId = :orgId)
        order by applyDate desc
    """)
    List<CandidateFilterProjection> findAllByCandidateInfoIds(CandidateFilterRequest request, List<Long> candidateInfoIds, Long orgId, Long participantId);

    @Query(value = """
        select distinct cia.id as id, cia.fullName as fullName, cia.email as email, cia.phone as phone, cia.coverLetter as coverLetter, cia.candidateId as candidateId,
               af.id as cvFileId, af.secureUrl as cvFileUrl,
               l.id as levelId, l.name as levelName,
               cso.skill as skill
        from CandidateInfoApply as cia
        join JobAdCandidate as jac on jac.candidateInfoId = cia.id
        join JobAd as ja on ja.id = jac.jobAdId
        left join AttachFile as af on af.id = cia.cvFileId
        left join CandidateSummaryOrg as cso on cso.candidateInfoId = cia.id and cso.orgId = :orgId
        left join Level as l on l.id = cso.levelId
        left join CalendarCandidateInfo cci on cci.candidateInfoId = cia.id
        left join Calendar c on c.id = cci.calendarId
        left join InterviewPanel ip on ip.calendarId = c.id
        where cia.id = :candidateInfoId
        and (:orgId is null or ja.orgId = :orgId)
        and (:participantId is null or ja.hrContactId = :participantId or ip.interviewerId = :participantId)
    """)
    CandidateInfoApplyProjection getCandidateInfoDetailProjection(Long candidateInfoId, Long orgId, Long participantId);

    @Query(value = """
        select distinct ja.id as jobAdId, ja.title as jobAdTitle, ja.hrContactId as hrContactId, ja.keyCodeInternal as keyCodeInternal,
               p.id as positionId, p.name as positionName,
               d.id as departmentId, d.name as departmentName, d.code as departmentCode,
               jac.id as jobAdCandidateId,
               jac.candidateStatus as candidateStatus,
               jac.applyDate as applyDate,
               jac.onboardDate as onboardDate,
               jac.eliminateReasonType as eliminateReasonType,
               jac.eliminateReasonDetail as eliminateReasonDetail,
               jac.eliminateDate as eliminateDate,
               japc.id as jobAdProcessCandidateId,
               japc.actionDate as actionDate,
               japc.isCurrentProcess as isCurrentProcess,
               japc.jobAdProcessId as jobAdProcessId,
               jap.name as processName
        from JobAdCandidate as jac
        join CandidateInfoApply cia on cia.id = jac.candidateInfoId
        join JobAd as ja on ja.id = jac.jobAdId
        join Position as p on p.id = ja.positionId
        join Department as d on d.id = p.departmentId
        join JobAdProcessCandidate as japc on japc.jobAdCandidateId = jac.id
        join JobAdProcess as jap on jap.id = japc.jobAdProcessId
        left join CalendarCandidateInfo cci on cci.candidateInfoId = cia.id
        left join Calendar c on c.id = cci.calendarId
        left join InterviewPanel ip on ip.calendarId = c.id
        where jac.candidateInfoId = :candidateInfoId
        and (:orgId is null or ja.orgId = :orgId)
        and (:participantId is null or ja.hrContactId = :participantId or ip.interviewerId = :participantId)
        order by applyDate desc
    """)
    List<JobAdCandidateProjection> getJobAdCandidatesByCandidateInfoId(Long candidateInfoId, Long orgId, Long participantId);

    @Query("""
            select case when count(*) > 0 then true else false end
            from JobAdCandidate as jac
            join JobAd as ja on ja.id = jac.jobAdId
            where jac.candidateInfoId = :candidateInfoId
            and ja.orgId = :orgId
            and (:hrContactId is null or ja.hrContactId = :hrContactId)
        """)
    boolean existsByCandidateInfoIdAndOrgIdAndHrContactId(Long candidateInfoId, Long orgId, Long hrContactId);

    @Query("""
        select case when count(*) > 0 then true else false end
            from JobAdCandidate as jac
            join JobAd as ja on ja.id = jac.jobAdId
            where ja.hrContactId = :hrContactId and jac.id = :jobAdCandidateId
    """)
    Boolean existsByJobAdCandidateIdAndHrContactId(Long jobAdCandidateId, Long hrContactId);

    @Modifying
    @Query("UPDATE JobAdCandidate jac SET jac.candidateStatus = :candidateStatus WHERE jac.id = :jobAdCandidateId")
    void updateCandidateStatus(Long jobAdCandidateId, String candidateStatus);

    @Query("SELECT CASE WHEN COUNT(jc) > 0 THEN true ELSE false END " +
            "FROM JobAdCandidate jc " +
            "JOIN JobAd ja ON ja.id = jc.jobAdId " +
            "WHERE jc.candidateInfoId = :candidateInfoId AND jc.candidateStatus = :candidateStatus " +
            "AND ja.orgId = :orgId")
    Boolean existsByCandidateInfoAndOrg(Long candidateInfoId, Long orgId, String candidateStatus);

    @Query("SELECT CASE WHEN COUNT(jc) > 0 THEN true ELSE false END " +
            "FROM JobAdCandidate jc " +
            "JOIN JobAd ja ON ja.id = jc.jobAdId " +
            "WHERE jc.candidateInfoId = :candidateInfoId AND jc.candidateStatus = :candidateStatus " +
            "AND ja.orgId = :orgId AND jc.id <> :jobAdCandidateId")
    Boolean existsByCandidateInfoAndOrgAndNotJobAdCandidate(Long candidateInfoId, Long orgId, Long jobAdCandidateId, String candidateStatus);

    @Query("""
        select case when count(*) > 0 then true else false end
            from JobAdCandidate as jac
            join JobAd as ja on ja.id = jac.jobAdId
            where ja.orgId = :orgId and jac.id = :jobAdCandidateId
    """)
    Boolean existsByJobAdCandidateIdAndOrgId(Long jobAdCandidateId, Long orgId);

    @Query(value = """
        select distinct ja.id as jobAdId, ja.title as jobAdTitle, ja.hrContactId as hrContactId,
               jac.id as jobAdCandidateId, jac.candidateStatus as candidateStatus, jac.applyDate as applyDate,
               jac.onboardDate as onboardDate, jac.eliminateReasonType as eliminateReasonType, jac.eliminateDate as eliminateDate,
               jap.id as jobAdProcessId, jap.name as processName, japc.actionDate as transferDate,
               o.id as orgId, o.name as orgName, o.logoId as logoId,
               cia.fullName as fullName, cia.phone as phone, cia.email as email, cia.coverLetter as coverLetter, cia.cvFileId as cvFileId, cia.candidateId as candidateId
        from JobAdCandidate as jac
        join JobAd as ja on ja.id = jac.jobAdId
        join Organization as o on o.id = ja.orgId
        join CandidateInfoApply as cia on cia.id = jac.candidateInfoId
        join JobAdProcessCandidate as japc on japc.jobAdCandidateId = jac.id and japc.isCurrentProcess = true
        join JobAdProcess as jap on jap.id = japc.jobAdProcessId
        where cia.candidateId = :#{#request.userId}
        and (:#{#request.candidateStatus?.name()} is null or jac.candidateStatus = :#{#request.candidateStatus?.name()})
        and (
             :#{#request.keyword} is null
             or lower(ja.title) like lower(concat('%', :#{#request.keyword}, '%'))
             or lower(o.name) like lower(concat('%', :#{#request.keyword}, '%'))
        )
    """,
     countQuery = """
        select count(jac.id)
        from JobAdCandidate jac
        join JobAd ja on ja.id = jac.jobAdId
        join Organization o on o.id = ja.orgId
        join CandidateInfoApply cia on cia.id = jac.candidateInfoId
        join JobAdProcessCandidate japc on japc.jobAdCandidateId = jac.id and japc.isCurrentProcess = true
        join JobAdProcess jap on jap.id = japc.jobAdProcessId
        where cia.candidateId = :#{#request.userId}
        and (:#{#request.candidateStatus?.name()} is null or jac.candidateStatus = :#{#request.candidateStatus?.name()})
        and (
             :#{#request.keyword} is null
             or lower(ja.title) like lower(concat('%', :#{#request.keyword}, '%'))
             or lower(o.name) like lower(concat('%', :#{#request.keyword}, '%'))
        )
     """)
    Page<JobAdAppliedProjection> getJobAdsAppliedByCandidate(JobAdAppliedFilterRequest request, Pageable pageable);

    @Query("""
        select jac from JobAdCandidate as jac
        join CandidateInfoApply cia on cia.id = jac.candidateInfoId
        where jac.jobAdId = :jobAdId and cia.candidateId = :candidateId
    """)
    JobAdCandidate findByJobAdIdAndCandidateId(Long jobAdId, Long candidateId);

    @Query("""
        select jac.id as id, ja.id as jobAdId, ja.title as jobAdTitle, cia.id as candidateInfoId, cia.fullName as fullName
        from JobAdCandidate jac
        join JobAd ja on ja.id =jac.jobAdId
        join CandidateInfoApply cia on cia.id = jac.candidateInfoId
        where ja.id = :jobAdId and cia.candidateId = :candidateId
    """)
    JobAdCandidateProjection getJobAdCandidateByJobAdIdAndCandidateId(Long jobAdId, Long candidateId);

    // ko sort HR, CandidateStatus
    @Query(value = """
        select distinct jac.id as id, jac.applyDate as applyDate, jac.onboardDate as onboardDate, jac.candidateStatus as candidateStatus,
               ja.id as jobAdId, ja.title as title, ja.hrContactId as hrContactId,
               cia.id as candidateInfoId, cia.fullName as fullName, cia.email as email, cia.phone as phone, cia.candidateId as candidateId,
               l.id as levelId, l.name as levelName
        from JobAdCandidate as jac
        join JobAd as ja on ja.id = jac.jobAdId
        join CandidateInfoApply as cia on cia.id = jac.candidateInfoId
        join CandidateSummaryOrg cso on cso.candidateInfoId = cia.id and cso.orgId = :#{#request.orgId}
        join Level l on l.id = cso.levelId
        left join CalendarCandidateInfo cci on cci.candidateInfoId = cia.id
        left join Calendar c on c.id = cci.calendarId
        left join InterviewPanel ip on ip.calendarId = c.id
        where jac.candidateStatus in ('ONBOARDED', 'WAITING_ONBOARDING')
        and (COALESCE(:#{#request.onboardDateStart}, NULL) IS NULL OR jac.onboardDate >= :#{#request.onboardDateStart})
        and (COALESCE(:#{#request.onboardDateEnd}, NULL) IS NULL OR jac.onboardDate <= :#{#request.onboardDateEnd})
        and (COALESCE(:#{#request.applyDateStart}, NULL) IS NULL OR jac.applyDate >= :#{#request.applyDateStart})
        and (COALESCE(:#{#request.applyDateEnd}, NULL) IS NULL OR jac.applyDate <= :#{#request.applyDateEnd})
        and (:#{#request.fullName} is null or lower(cia.fullName) like lower(concat('%', :#{#request.fullName}, '%')))
        and (:#{#request.email} is null or lower(cia.email) like lower(concat('%', :#{#request.email}, '%')))
        and (:#{#request.phone} is null or lower(cia.phone) like lower(concat('%', :#{#request.phone}, '%')))
        and (:#{#request.jobAdTitle} is null or lower(ja.title) like lower(concat('%', :#{#request.jobAdTitle}, '%')))
        and (:#{#request.hrContactId} is null or ja.hrContactId = :#{#request.hrContactId})
        and (:#{#request.levelId} is null or cso.levelId = :#{#request.levelId})
        and (:#{#request.status?.name()} is null or jac.candidateStatus = :#{#request.status?.name()})
        and (:participantId is null or ja.hrContactId = :participantId or ip.interviewerId = :participantId)
    """,
    countQuery = """
        select distinct jac.id
        from JobAdCandidate as jac
        join JobAd as ja on ja.id = jac.jobAdId
        join CandidateInfoApply as cia on cia.id = jac.candidateInfoId
        join CandidateSummaryOrg cso on cso.candidateInfoId = cia.id and cso.orgId = :#{#request.orgId}
        join Level l on l.id = cso.levelId
        left join CalendarCandidateInfo cci on cci.candidateInfoId = cia.id
        left join Calendar c on c.id = cci.calendarId
        left join InterviewPanel ip on ip.calendarId = c.id
        where jac.candidateStatus in ('ONBOARDED', 'WAITING_ONBOARDING')
        and (COALESCE(:#{#request.onboardDateStart}, NULL) IS NULL OR jac.onboardDate >= :#{#request.onboardDateStart})
        and (COALESCE(:#{#request.onboardDateEnd}, NULL) IS NULL OR jac.onboardDate <= :#{#request.onboardDateEnd})
        and (COALESCE(:#{#request.applyDateStart}, NULL) IS NULL OR jac.applyDate >= :#{#request.applyDateStart})
        and (COALESCE(:#{#request.applyDateEnd}, NULL) IS NULL OR jac.applyDate <= :#{#request.applyDateEnd})
        and (:#{#request.fullName} is null or lower(cia.fullName) like lower(concat('%', :#{#request.fullName}, '%')))
        and (:#{#request.email} is null or lower(cia.email) like lower(concat('%', :#{#request.email}, '%')))
        and (:#{#request.phone} is null or lower(cia.phone) like lower(concat('%', :#{#request.phone}, '%')))
        and (:#{#request.jobAdTitle} is null or lower(ja.title) like lower(concat('%', :#{#request.jobAdTitle}, '%')))
        and (:#{#request.hrContactId} is null or ja.hrContactId = :#{#request.hrContactId})
        and (:#{#request.levelId} is null or cso.levelId = :#{#request.levelId})
        and (:#{#request.status?.name()} is null or jac.candidateStatus = :#{#request.status?.name()})
        and (:participantId is null or ja.hrContactId = :participantId or ip.interviewerId = :participantId)
    """)
    Page<JobAdCandidateProjection> getListOfOnboardedCandidates(CandidateOnboardFilterRequest request, Long participantId, Pageable pageable);
}
