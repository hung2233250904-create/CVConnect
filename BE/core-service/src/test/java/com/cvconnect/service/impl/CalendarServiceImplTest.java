package com.cvconnect.service.impl;

import com.cvconnect.common.RestTemplateClient;
import com.cvconnect.constant.Constants;
import com.cvconnect.dto.calendar.CalendarCandidateInfoDto;
import com.cvconnect.dto.calendar.CalendarRequest;
import com.cvconnect.dto.candidateInfoApply.CandidateInfoApplyDto;
import com.cvconnect.dto.common.NotificationDto;
import com.cvconnect.dto.jobAd.JobAdDto;
import com.cvconnect.entity.Calendar;
import com.cvconnect.entity.CalendarCandidateInfo;
import com.cvconnect.entity.InterviewPanel;
import com.cvconnect.enums.CalendarType;
import com.cvconnect.enums.CoreErrorCode;
import com.cvconnect.repository.CalendarCandidateInfoRepository;
import com.cvconnect.repository.CalendarRepository;
import com.cvconnect.repository.InterviewPanelRepository;
import com.cvconnect.service.CandidateInfoApplyService;
import com.cvconnect.service.JobAdProcessService;
import com.cvconnect.service.JobAdService;
import com.cvconnect.utils.CoreServiceUtils;
import nmquan.commonlib.constant.CommonConstants;
import nmquan.commonlib.dto.response.IDResponse;
import nmquan.commonlib.exception.AppException;
import nmquan.commonlib.exception.CommonErrorCode;
import nmquan.commonlib.utils.KafkaUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CalendarServiceImplTest {

    // Real service with real DB repositories underneath.
    @Autowired
    private CalendarServiceImpl service;

    // DB access for CheckDB assertions.
    @Autowired
    private CalendarRepository calendarRepository;
    @Autowired
    private InterviewPanelRepository interviewPanelRepository;
    @Autowired
    private CalendarCandidateInfoRepository calendarCandidateInfoRepository;

    // External/non-deterministic dependencies are mocked.
    @MockBean
    private RestTemplateClient restTemplateClient;
    @MockBean
    private JobAdProcessService jobAdProcessService;
    @MockBean
    private CandidateInfoApplyService candidateInfoApplyService;
    @MockBean
    private JobAdService jobAdService;
    @MockBean
    private KafkaUtils kafkaUtils;

    @AfterEach
    void clearSecurityContext() {
        // Rollback is handled by @Transactional; this only avoids auth context leak between tests.
        SecurityContextHolder.clearContext();
    }

    // TC01
    // Given: request date is today (business rule requires scheduling strictly after today).
    // When: createCalendar is invoked.
    // Then: DATE_BEFORE_TODAY is thrown and CheckDB confirms no new calendar row is inserted.
    @Test
    void test_TC01_rejectScheduleCreation_whenDateIsToday() {
        long before = calendarRepository.count();

        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now(), 60, true, List.of(101L), List.of(10L));
        stubValidationDependencies(request, true, true);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.DATE_BEFORE_TODAY, ex.getErrorCode());
        assertEquals(before, calendarRepository.count());
    }

    // TC02
    // Given: request date is in the past.
    // When: createCalendar is invoked.
    // Then: DATE_BEFORE_TODAY is thrown and CheckDB confirms database state is unchanged.
    @Test
    void test_TC02_rejectScheduleCreation_whenDateIsInThePast() {
        long before = calendarRepository.count();

        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().minusDays(1), 60, true, List.of(101L), List.of(10L));
        stubValidationDependencies(request, true, true);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.DATE_BEFORE_TODAY, ex.getErrorCode());
        assertEquals(before, calendarRepository.count());
    }

    // TC03
    // Given: valid input with tomorrow date, online meeting, and valid participants/candidates.
    // When: createCalendar is invoked.
    // Then: one calendar is created, panels are inserted, and candidate mapping rows are persisted (CheckDB).
    @Test
    void test_TC03_allowScheduleCreation_whenDateIsTomorrow() {
        long beforeCalendar = calendarRepository.count();

        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(1), 60, true, List.of(101L), List.of(10L, 11L));
        stubValidationDependencies(request, true, true);
        stubAfterCreateDependencies(request);

        IDResponse<Long> response = service.createCalendar(request);

        assertNotNull(response.getId());
        assertEquals(beforeCalendar + 1, calendarRepository.count());

        List<InterviewPanel> panels = interviewPanelRepository.findByCalendarId(response.getId());
        assertEquals(2, panels.size());

        CalendarCandidateInfo c1 = calendarCandidateInfoRepository.findByCalendarIdAndCandidateInfoId(response.getId(), 101L);
        assertNotNull(c1);
    }

    // TC04
    // Given: durationMinutes is zero (invalid boundary value).
    // When: createCalendar is invoked.
    // Then: DURATION_MINUTES_INVALID is thrown and no DB write occurs.
    @Test
    void test_TC04_rejectScheduleCreation_whenDurationIsZero() {
        long before = calendarRepository.count();

        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(1), 0, true, List.of(101L), List.of(10L));
        stubValidationDependencies(request, true, true);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.DURATION_MINUTES_INVALID, ex.getErrorCode());
        assertEquals(before, calendarRepository.count());
    }

    // TC05
    // Given: durationMinutes is negative.
    // When: createCalendar is invoked.
    // Then: DURATION_MINUTES_INVALID is thrown and CheckDB confirms no inserted record.
    @Test
    void test_TC05_rejectScheduleCreation_whenDurationIsNegative() {
        long before = calendarRepository.count();

        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(1), -30, true, List.of(101L), List.of(10L));
        stubValidationDependencies(request, true, true);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.DURATION_MINUTES_INVALID, ex.getErrorCode());
        assertEquals(before, calendarRepository.count());
    }

    // TC06
    // Given: interview type is offline but orgAddressId is null.
    // When: createCalendar is invoked.
    // Then: MEETING_ADDRESS_NOT_NULL is thrown and DB remains unchanged.
    @Test
    void test_TC06_rejectOfflineInterview_whenNoAddressIsProvided() {
        long before = calendarRepository.count();

        CalendarRequest request = buildBaseOfflineRequest(LocalDate.now().plusDays(1), 60, List.of(101L), List.of(10L));
        request.setOrgAddressId(null);
        stubValidationDependencies(request, true, true);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.MEETING_ADDRESS_NOT_NULL, ex.getErrorCode());
        assertEquals(before, calendarRepository.count());
    }

    // TC07
    // Given: interview type is online but meetingLink is missing.
    // When: createCalendar is invoked.
    // Then: MEETING_LINK_NOT_NULL is thrown and no calendar row is created.
    @Test
    void test_TC07_rejectOnlineInterview_whenNoMeetingLinkIsProvided() {
        long before = calendarRepository.count();

        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(1), 60, true, List.of(101L), List.of(10L));
        request.setMeetingLink(null);
        stubValidationDependencies(request, true, true);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.MEETING_LINK_NOT_NULL, ex.getErrorCode());
        assertEquals(before, calendarRepository.count());
    }

    // TC08
    // Given: at least one interviewer is not a member of the organization.
    // When: createCalendar is invoked.
    // Then: ACCESS_DENIED is thrown and CheckDB confirms transaction does not insert data.
    @Test
    void test_TC08_rejectSchedule_whenInterviewerDoesNotBelongToOrg() {
        long before = calendarRepository.count();

        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(1), 60, true, List.of(101L), List.of(99L));
        stubValidationDependencies(request, false, true);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CommonErrorCode.ACCESS_DENIED, ex.getErrorCode());
        assertEquals(before, calendarRepository.count());
    }

    // TC09
    // Given: candidate list is not in the expected process stage.
    // When: createCalendar is invoked.
    // Then: CANDIDATE_INFO_EXISTS_NOT_IN_PROCESS is thrown and DB is unchanged.
    @Test
    void test_TC09_rejectSchedule_whenCandidateNotInTargetProcess() {
        long before = calendarRepository.count();

        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(1), 60, true, List.of(101L), List.of(10L));
        stubValidationDependencies(request, true, false);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.CANDIDATE_INFO_EXISTS_NOT_IN_PROCESS, ex.getErrorCode());
        assertEquals(before, calendarRepository.count());
    }

    // TC10
    // Given: joinSameTime=true with multiple candidates and 60-minute duration.
    // When: calendar is created.
    // Then: all candidates receive the same slot boundaries (same start and end time).
    @Test
    void test_TC10_allCandidatesShareSameSlot_whenJoinSameTimeIsTrue() {
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(2), 60, true, List.of(1L, 2L, 3L), List.of(10L));
        request.setTimeFrom(LocalTime.of(9, 0));
        stubValidationDependencies(request, true, true);
        stubAfterCreateDependencies(request);

        IDResponse<Long> response = service.createCalendar(request);

        CalendarCandidateInfo c1 = calendarCandidateInfoRepository.findByCalendarIdAndCandidateInfoId(response.getId(), 1L);
        CalendarCandidateInfo c2 = calendarCandidateInfoRepository.findByCalendarIdAndCandidateInfoId(response.getId(), 2L);
        CalendarCandidateInfo c3 = calendarCandidateInfoRepository.findByCalendarIdAndCandidateInfoId(response.getId(), 3L);

        assertEquals(LocalTime.of(9, 0), c1.getTimeFrom());
        assertEquals(LocalTime.of(10, 0), c1.getTimeTo());
        assertEquals(LocalTime.of(9, 0), c2.getTimeFrom());
        assertEquals(LocalTime.of(10, 0), c2.getTimeTo());
        assertEquals(LocalTime.of(9, 0), c3.getTimeFrom());
        assertEquals(LocalTime.of(10, 0), c3.getTimeTo());
    }

    // TC11
    // Given: joinSameTime=false with multiple candidates and 30-minute duration.
    // When: calendar is created.
    // Then: candidate slots are generated sequentially without overlap (09:00-09:30, 09:30-10:00, 10:00-10:30).
    @Test
    void test_TC11_eachCandidateGetsSequentialSlot_whenJoinSameTimeIsFalse() {
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(2), 30, false, List.of(1L, 2L, 3L), List.of(10L));
        request.setTimeFrom(LocalTime.of(9, 0));
        stubValidationDependencies(request, true, true);
        stubAfterCreateDependencies(request);

        IDResponse<Long> response = service.createCalendar(request);

        CalendarCandidateInfo c1 = calendarCandidateInfoRepository.findByCalendarIdAndCandidateInfoId(response.getId(), 1L);
        CalendarCandidateInfo c2 = calendarCandidateInfoRepository.findByCalendarIdAndCandidateInfoId(response.getId(), 2L);
        CalendarCandidateInfo c3 = calendarCandidateInfoRepository.findByCalendarIdAndCandidateInfoId(response.getId(), 3L);

        assertEquals(LocalTime.of(9, 0), c1.getTimeFrom());
        assertEquals(LocalTime.of(9, 30), c1.getTimeTo());
        assertEquals(LocalTime.of(9, 30), c2.getTimeFrom());
        assertEquals(LocalTime.of(10, 0), c2.getTimeTo());
        assertEquals(LocalTime.of(10, 0), c3.getTimeFrom());
        assertEquals(LocalTime.of(10, 30), c3.getTimeTo());
    }

    // TC12
    // Given: joinSameTime=true and at least one candidate slot is created.
    // When: createCalendar completes.
    // Then: notification is emitted exactly once for the shared interview slot.
    @Test
    void test_TC12_exactlyOneNotification_whenJoinSameTimeIsTrue() {
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(2), 60, true, List.of(1L), List.of(10L, 11L));
        stubValidationDependencies(request, true, true);
        stubAfterCreateDependencies(request);

        service.createCalendar(request);

        verify(kafkaUtils, times(1)).sendWithJson(eq(Constants.KafkaTopic.NOTIFICATION), any(NotificationDto.class));
    }

    // TC13
    // Given: joinSameTime=false for three candidates (three distinct slots).
    // When: createCalendar completes.
    // Then: notification is emitted once per candidate slot (total equals slot count).
    @Test
    void test_TC13_oneNotificationPerCandidateSlot_whenJoinSameTimeIsFalse() {
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(2), 30, false, List.of(1L, 2L, 3L), List.of(10L));
        stubValidationDependencies(request, true, true);
        stubAfterCreateDependencies(request);

        service.createCalendar(request);

        verify(kafkaUtils, times(3)).sendWithJson(eq(Constants.KafkaTopic.NOTIFICATION), any(NotificationDto.class));
    }

    // TC14
    // Given: local datetime in HCM timezone.
    // When: utility converts from HCM to UTC.
    // Then: converted instant equals expected UTC timestamp (10:00 HCM -> 03:00 UTC).
    @Test
    void test_TC14_timezoneConversion_HCM10h_to_UTC03h() {
        LocalDateTime localDateTime = LocalDateTime.of(2025, 9, 15, 10, 0, 0);

        Instant result = CoreServiceUtils.convertLocalDateTimeToInstant(
                localDateTime,
                CommonConstants.ZONE.HCM,
                CommonConstants.ZONE.UTC
        );

        assertEquals(Instant.parse("2025-09-15T03:00:00Z"), result);
    }

    // TC15
    // Given: a valid calendar request with specific date/time and 90-minute duration.
    // When: createCalendar publishes notification.
    // Then: notification message contains formatted Vietnamese time-range string "ngay dd/MM tu HH:mm den HH:mm".
    @Test
    void test_TC15_timeRangeStringFormattedAs_ddMM_HHmm_viaNotificationMessage() {
        LocalDate date = LocalDate.now().plusDays(4);
        CalendarRequest request = buildBaseOnlineRequest(date, 90, true, List.of(1L), List.of(10L));
        request.setTimeFrom(LocalTime.of(9, 0));
        stubValidationDependencies(request, true, true);
        stubAfterCreateDependencies(request);

        service.createCalendar(request);

        String expectedRange = String.format("ngày %s từ 09:00 đến 10:30", date.format(DateTimeFormatter.ofPattern("dd/MM")));
        verify(kafkaUtils).sendWithJson(
            eq(Constants.KafkaTopic.NOTIFICATION),
            argThat(payload -> payload instanceof NotificationDto dto
                && dto.getMessage() != null
                && dto.getMessage().contains(expectedRange))
        );
    }

    private CalendarRequest buildBaseOnlineRequest(
            LocalDate date,
            int durationMinutes,
            boolean joinSameTime,
            List<Long> candidateInfoIds,
            List<Long> participantIds
    ) {
        return CalendarRequest.builder()
                .jobAdProcessId(5L)
                .calendarType(CalendarType.INTERVIEW_ONLINE)
                .joinSameTime(joinSameTime)
                .date(date)
                .timeFrom(LocalTime.of(9, 0))
                .durationMinutes(durationMinutes)
                .meetingLink("https://meet.google.com/abc")
                .participantIds(participantIds)
                .candidateInfoIds(candidateInfoIds)
                .isSendEmail(false)
                .build();
    }

    private CalendarRequest buildBaseOfflineRequest(
            LocalDate date,
            int durationMinutes,
            List<Long> candidateInfoIds,
            List<Long> participantIds
    ) {
        return CalendarRequest.builder()
                .jobAdProcessId(5L)
                .calendarType(CalendarType.INTERVIEW_OFFLINE)
                .joinSameTime(true)
                .date(date)
                .timeFrom(LocalTime.of(9, 0))
                .durationMinutes(durationMinutes)
                .orgAddressId(99L)
                .participantIds(participantIds)
                .candidateInfoIds(candidateInfoIds)
                .isSendEmail(false)
                .build();
    }

    private void stubValidationDependencies(CalendarRequest request, boolean validParticipants, boolean validCandidateInProcess) {
        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdProcessService.existByJobAdProcessIdAndOrgId(request.getJobAdProcessId(), 10L)).thenReturn(true);
        when(restTemplateClient.checkOrgMember(request.getParticipantIds())).thenReturn(validParticipants);
        when(candidateInfoApplyService.validateCandidateInfoInProcess(request.getCandidateInfoIds(), request.getJobAdProcessId()))
                .thenReturn(validCandidateInProcess);
    }

    private void stubAfterCreateDependencies(CalendarRequest request) {
        Map<Long, CandidateInfoApplyDto> byIds = request.getCandidateInfoIds().stream().collect(Collectors.toMap(
                id -> id,
                id -> CandidateInfoApplyDto.builder()
                        .id(id)
                        .candidateId(7000L + id)
                        .fullName("Candidate " + id)
                        .email("candidate" + id + "@mail.com")
                        .build()
        ));
        when(candidateInfoApplyService.getByIds(request.getCandidateInfoIds())).thenReturn(byIds);
        when(jobAdService.findByJobAdProcessId(request.getJobAdProcessId()))
                .thenReturn(JobAdDto.builder().id(1000L).title("Java Backend Developer").orgId(10L).positionId(11L).build());
    }

    private static void setCurrentUser(Long userId, String... roles) {
        Map<String, Object> principal = Map.of("id", userId);
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream().map(SimpleGrantedAuthority::new).toList();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
