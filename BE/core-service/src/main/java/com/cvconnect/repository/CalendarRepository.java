package com.cvconnect.repository;

import com.cvconnect.dto.calendar.CalendarDetailInViewCandidateProjection;
import com.cvconnect.dto.calendar.CalendarFilterViewCandidateProjection;
import com.cvconnect.dto.calendar.CalendarFilterRequest;
import com.cvconnect.entity.Calendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalendarRepository extends JpaRepository<Calendar, Long> {

    @Query("""
        select distinct cci.date as date, c.id as calendarId, cci.id as calendarCandidateInfoId,
            jap.id as jobAdProcessId, jap.name as jobAdProcessName, c.creatorId as creatorId, c.calendarType as calendarType,
            cci.timeFrom as timeFrom, cci.timeTo as timeTo from Calendar c
        join CalendarCandidateInfo cci on cci.calendarId = c.id
        join CandidateInfoApply cia on cia.id = cci.candidateInfoId
        join JobAdProcess jap on jap.id = c.jobAdProcessId
        join JobAd ja on ja.id = jap.jobAdId
        join JobAdCandidate jac on jac.candidateInfoId = cia.id and jac.jobAdId = ja.id
        join InterviewPanel ip on ip.calendarId = c.id
        where jac.id = :#{#request.jobAdCandidateId}
        and (:creatorId is null or c.creatorId = :creatorId)
        and (:participantId is null or ip.interviewerId = :participantId)
        and (:participantIdAuth is null or ip.interviewerId = :participantIdAuth)
        order by date asc, timeFrom asc, timeTo asc
    """)
    List<CalendarFilterViewCandidateProjection> filterViewCandidateCalendars(CalendarFilterRequest request, Long creatorId, Long participantId, Long participantIdAuth);

    @Query("""
        select distinct ja.id as jobAdId, ja.title as jobAdTitle, jap.id as jobAdProcessId, jap.name as jobAdProcessName,
            c.creatorId as creatorId, c.calendarType as calendarType, cci.date as date, cci.timeFrom as timeFrom, cci.timeTo as timeTo,
            c.orgAddressId as locationId, c.meetingLink as meetingLink, c.id as calendarId, c.joinSameTime as joinSameTime, cci.candidateInfoId as candidateInfoId
        from CalendarCandidateInfo cci
        join Calendar c on c.id = cci.calendarId
        join JobAdProcess jap on jap.id = c.jobAdProcessId
        join JobAd ja on ja.id = jap.jobAdId
        join InterviewPanel ip on ip.calendarId = c.id
        where cci.id = :calendarCandidateInfoId
        and ja.orgId = :orgId
        and (:userId is null or c.creatorId = :userId or ja.hrContactId = :userId or ip.interviewerId = :userId)
    """)
    CalendarDetailInViewCandidateProjection detailInViewCandidate(Long calendarCandidateInfoId, Long orgId, Long userId);

    @Query("""
        select distinct cci.date as date, c.id as calendarId, c.joinSameTime as joinSameTime,
            cci.candidateInfoId as candidateInfoId, cia.fullName as fullName,
            cci.timeFrom as timeFrom, cci.timeTo as timeTo,
            ja.id as jobAdId, ja.title as jobAdTitle, ja.hrContactId as hrContactId,
            c.calendarType as calendarType
        from Calendar c
        join CalendarCandidateInfo cci on cci.calendarId = c.id
        join CandidateInfoApply cia on cia.id = cci.candidateInfoId
        join JobAdProcess jap on jap.id = c.jobAdProcessId
        join JobAd ja on ja.id = jap.jobAdId
        join InterviewPanel ip on ip.calendarId = c.id
        where
        (:#{#request.jobAdId} is null or ja.id = :#{#request.jobAdId})
        and (cci.date >= coalesce(:#{#request.dateFrom}, cci.date))
        and (cci.date <= coalesce(:#{#request.dateTo}, cci.date))
        and ja.orgId = :orgId
        and (:creatorId is null or c.creatorId = :creatorId)
        and (:participantId is null or ip.interviewerId = :participantId)
        and (:participantIdAuth is null or ja.hrContactId = :currentUserId or ip.interviewerId = :participantIdAuth)
        order by date asc, timeFrom asc, timeTo asc
    """)
    List<CalendarDetailInViewCandidateProjection> filterViewGeneral(CalendarFilterRequest request, Long orgId, Long creatorId, Long participantId, Long participantIdAuth, Long currentUserId);

    @Query("""
        select case when count(*) > 0 then true else false end
        from JobAd ja
        join JobAdProcess jap on jap.jobAdId = ja.id
        join Calendar c on c.jobAdProcessId = jap.id
        where ja.orgId = :orgId and c.id = :calendarId
    """)
    boolean existsByIdAndOrgId(Long calendarId, Long orgId);

    @Query("""
        select case when count(*) > 0 then true else false end
        from JobAd ja
        join JobAdProcess jap on jap.jobAdId = ja.id
        join Calendar c on c.jobAdProcessId = jap.id
        where ja.hrContactId = :hrContactId and c.id = :calendarId
    """)
    boolean existsByCalendarIdAndHrContactId(Long calendarId, Long hrContactId);

    @Query(value = """
        select c.id from calendar c
        join interview_panel ip on ip.calendar_id = c.id
        where c.date = :date
        and ip.interviewer_id = :interviewerId
        and (:startTime >= c.time_from and :startTime < (c.time_from + (c.duration_minutes * interval '1 minute')))
    """, nativeQuery = true)
    List<Long> findOverlappingSchedules(java.time.LocalDate date, java.time.LocalTime startTime, Long interviewerId);
}

