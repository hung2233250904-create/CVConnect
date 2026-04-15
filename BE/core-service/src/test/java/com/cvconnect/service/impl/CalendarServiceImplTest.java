package com.cvconnect.service.impl;

import com.cvconnect.common.ReplacePlaceholder;
import com.cvconnect.common.RestTemplateClient;
import com.cvconnect.constant.Constants;
import com.cvconnect.dto.calendar.CalendarCandidateInfoDto;
import com.cvconnect.dto.calendar.CalendarRequest;
import com.cvconnect.dto.candidateInfoApply.CandidateInfoApplyDto;
import com.cvconnect.dto.common.NotificationDto;
import com.cvconnect.dto.jobAd.JobAdDto;
import com.cvconnect.entity.Calendar;
import com.cvconnect.enums.CalendarType;
import com.cvconnect.enums.CoreErrorCode;
import com.cvconnect.repository.CalendarRepository;
import com.cvconnect.service.*;
import com.cvconnect.utils.CoreServiceUtils;
import nmquan.commonlib.constant.CommonConstants;
import nmquan.commonlib.dto.response.IDResponse;
import nmquan.commonlib.exception.AppException;
import nmquan.commonlib.exception.CommonErrorCode;
import nmquan.commonlib.service.SendEmailService;
import nmquan.commonlib.utils.KafkaUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarServiceImplTest {

    @Mock
    private CalendarRepository calendarRepository;
    @Mock
    private RestTemplateClient restTemplateClient;
    @Mock
    private InterviewPanelService interviewPanelService;
    @Mock
    private CalendarCandidateInfoService calendarCandidateInfoService;
    @Mock
    private JobAdProcessService jobAdProcessService;
    @Mock
    private CandidateInfoApplyService candidateInfoApplyService;
    @Mock
    private SendEmailService sendEmailService;
    @Mock
    private ReplacePlaceholder replacePlaceholder;
    @Mock
    private JobAdService jobAdService;
    @Mock
    private JobAdCandidateService jobAdCandidateService;
    @Mock
    private OrgAddressService orgAddressService;
    @Mock
    private KafkaUtils kafkaUtils;

    @InjectMocks
    private CalendarServiceImpl service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void test_TC01_createCalendar_rejectsTodayDate() {
        // Test Case ID: TC01
        // CheckDB: validation fails before save.
        // Rollback: no DB write due to early validation error.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now(), 60, true, List.of(101L), List.of(10L));

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdProcessService.existByJobAdProcessIdAndOrgId(5L, 10L)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.DATE_BEFORE_TODAY, ex.getErrorCode());
        verify(calendarRepository, never()).save(any(Calendar.class));
    }

    @Test
    void test_TC02_createCalendar_rejectsPastDate() {
        // Test Case ID: TC02
        // CheckDB: validation fails before save.
        // Rollback: no DB write due to early validation error.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().minusDays(1), 60, true, List.of(101L), List.of(10L));

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdProcessService.existByJobAdProcessIdAndOrgId(5L, 10L)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.DATE_BEFORE_TODAY, ex.getErrorCode());
        verify(calendarRepository, never()).save(any(Calendar.class));
    }

    @Test
    void test_TC03_createCalendar_acceptsFutureDate_andReturnsId() {
        // Test Case ID: TC03
        // CheckDB: verify save + child records created.
        // Rollback: unit test uses mocks only.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(2), 60, true, List.of(101L), List.of(10L, 11L));
        stubCreateCalendarHappyPath(request, 10L, 7001L);

        IDResponse<Long> response = service.createCalendar(request);

        assertEquals(7001L, response.getId());
        verify(calendarRepository).save(any(Calendar.class));
        verify(interviewPanelService).create(anyList());
        verify(calendarCandidateInfoService).create(anyList());
    }

    @Test
    void test_TC04_createCalendar_rejectsDurationZero() {
        // Test Case ID: TC04
        // CheckDB: no save when duration invalid.
        // Rollback: no DB mutation.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(2), 0, true, List.of(101L), List.of(10L));

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdProcessService.existByJobAdProcessIdAndOrgId(5L, 10L)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.DURATION_MINUTES_INVALID, ex.getErrorCode());
        verify(calendarRepository, never()).save(any(Calendar.class));
    }

    @Test
    void test_TC05_createCalendar_rejectsDurationNegative() {
        // Test Case ID: TC05
        // CheckDB: no save when duration invalid.
        // Rollback: no DB mutation.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(2), -30, true, List.of(101L), List.of(10L));

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdProcessService.existByJobAdProcessIdAndOrgId(5L, 10L)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.DURATION_MINUTES_INVALID, ex.getErrorCode());
        verify(calendarRepository, never()).save(any(Calendar.class));
    }

    @Test
    void test_TC06_createCalendar_rejectsOfflineWithoutAddress() {
        // Test Case ID: TC06
        // CheckDB: no save when location missing for offline calendar.
        // Rollback: no DB mutation.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        CalendarRequest request = buildBaseOfflineRequest(LocalDate.now().plusDays(2), 60, List.of(101L), List.of(10L));
        request.setOrgAddressId(null);

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdProcessService.existByJobAdProcessIdAndOrgId(5L, 10L)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.MEETING_ADDRESS_NOT_NULL, ex.getErrorCode());
    }

    @Test
    void test_TC07_createCalendar_rejectsOnlineWithoutMeetingLink() {
        // Test Case ID: TC07
        // CheckDB: no save when meeting link missing for online calendar.
        // Rollback: no DB mutation.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(2), 60, true, List.of(101L), List.of(10L));
        request.setMeetingLink(null);

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdProcessService.existByJobAdProcessIdAndOrgId(5L, 10L)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.MEETING_LINK_NOT_NULL, ex.getErrorCode());
    }

    @Test
    void test_TC08_createCalendar_rejectsWhenParticipantNotInOrg() {
        // Test Case ID: TC08
        // CheckDB: validation fails before save.
        // Rollback: no DB mutation.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(2), 60, true, List.of(101L), List.of(99L));

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdProcessService.existByJobAdProcessIdAndOrgId(5L, 10L)).thenReturn(true);
        when(restTemplateClient.checkOrgMember(List.of(99L))).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CommonErrorCode.ACCESS_DENIED, ex.getErrorCode());
        verify(calendarRepository, never()).save(any(Calendar.class));
    }

    @Test
    void test_TC09_createCalendar_rejectsWhenCandidateNotInProcess() {
        // Test Case ID: TC09
        // CheckDB: validation fails before save.
        // Rollback: no DB mutation.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(2), 60, true, List.of(101L), List.of(10L));

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdProcessService.existByJobAdProcessIdAndOrgId(5L, 10L)).thenReturn(true);
        when(restTemplateClient.checkOrgMember(List.of(10L))).thenReturn(true);
        when(candidateInfoApplyService.validateCandidateInfoInProcess(List.of(101L), 5L)).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> service.createCalendar(request));
        assertEquals(CoreErrorCode.CANDIDATE_INFO_EXISTS_NOT_IN_PROCESS, ex.getErrorCode());
        verify(calendarRepository, never()).save(any(Calendar.class));
    }

    @Test
    void test_TC10_createCalendar_joinSameTimeTrue_allCandidatesShareSameSlot() {
        // Test Case ID: TC10
        // CheckDB: verify generated candidate slots are identical.
        // Rollback: mock-only unit test.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        LocalDate date = LocalDate.now().plusDays(3);
        LocalTime timeFrom = LocalTime.of(9, 0);
        CalendarRequest request = buildBaseOnlineRequest(date, 60, true, List.of(1L, 2L, 3L), List.of(10L, 11L));
        request.setTimeFrom(timeFrom);
        stubCreateCalendarHappyPath(request, 10L, 7002L);

        service.createCalendar(request);

        ArgumentCaptor<List<CalendarCandidateInfoDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(calendarCandidateInfoService).create(captor.capture());
        List<CalendarCandidateInfoDto> slots = captor.getValue();

        assertEquals(3, slots.size());
        assertEquals(LocalTime.of(9, 0), slots.get(0).getTimeFrom());
        assertEquals(LocalTime.of(10, 0), slots.get(0).getTimeTo());
        assertEquals(LocalTime.of(9, 0), slots.get(1).getTimeFrom());
        assertEquals(LocalTime.of(10, 0), slots.get(1).getTimeTo());
        assertEquals(LocalTime.of(9, 0), slots.get(2).getTimeFrom());
        assertEquals(LocalTime.of(10, 0), slots.get(2).getTimeTo());
    }

    @Test
    void test_TC11_createCalendar_joinSameTimeFalse_candidatesGetSequentialSlots() {
        // Test Case ID: TC11
        // CheckDB: verify generated candidate slots are sequential.
        // Rollback: mock-only unit test.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        LocalDate date = LocalDate.now().plusDays(3);
        LocalTime timeFrom = LocalTime.of(9, 0);
        CalendarRequest request = buildBaseOnlineRequest(date, 30, false, List.of(1L, 2L, 3L), List.of(10L));
        request.setTimeFrom(timeFrom);
        stubCreateCalendarHappyPath(request, 10L, 7003L);

        service.createCalendar(request);

        ArgumentCaptor<List<CalendarCandidateInfoDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(calendarCandidateInfoService).create(captor.capture());
        List<CalendarCandidateInfoDto> slots = captor.getValue();

        assertEquals(LocalTime.of(9, 0), slots.get(0).getTimeFrom());
        assertEquals(LocalTime.of(9, 30), slots.get(0).getTimeTo());
        assertEquals(LocalTime.of(9, 30), slots.get(1).getTimeFrom());
        assertEquals(LocalTime.of(10, 0), slots.get(1).getTimeTo());
        assertEquals(LocalTime.of(10, 0), slots.get(2).getTimeFrom());
        assertEquals(LocalTime.of(10, 30), slots.get(2).getTimeTo());
    }

    @Test
    void test_TC12_createCalendar_joinSameTimeTrue_sendsExactlyOneNotificationToInterviewers() {
        // Test Case ID: TC12
        // CheckDB: verify notification dispatch count and receivers.
        // Rollback: no persistent messaging in unit test.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(4), 60, true, List.of(1L), List.of(10L, 11L));
        stubCreateCalendarHappyPath(request, 10L, 7004L);

        service.createCalendar(request);

        ArgumentCaptor<NotificationDto> notifyCaptor = ArgumentCaptor.forClass(NotificationDto.class);
        verify(kafkaUtils, times(1)).sendWithJson(eq(Constants.KafkaTopic.NOTIFICATION), notifyCaptor.capture());
        assertEquals(List.of(10L, 11L), notifyCaptor.getValue().getReceiverIds());
    }

    @Test
    void test_TC13_createCalendar_joinSameTimeFalse_sendsOneNotificationPerCandidateSlot() {
        // Test Case ID: TC13
        // CheckDB: verify notification dispatch count follows candidate slots.
        // Rollback: no persistent messaging in unit test.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        CalendarRequest request = buildBaseOnlineRequest(LocalDate.now().plusDays(4), 30, false, List.of(1L, 2L, 3L), List.of(10L));
        stubCreateCalendarHappyPath(request, 10L, 7005L);

        service.createCalendar(request);

        verify(kafkaUtils, times(3)).sendWithJson(eq(Constants.KafkaTopic.NOTIFICATION), any(NotificationDto.class));
    }

    @Test
    void test_TC14_convertLocalDateTime_HCM10h_to_UTC03h() {
        // Test Case ID: TC14
        // CheckDB: pure utility, no DB involved.
        // Rollback: not applicable.

        LocalDateTime localDateTime = LocalDateTime.of(2025, 9, 15, 10, 0, 0);
        Instant result = CoreServiceUtils.convertLocalDateTimeToInstant(
                localDateTime,
                CommonConstants.ZONE.HCM,
                CommonConstants.ZONE.UTC
        );

        assertEquals(Instant.parse("2025-09-15T03:00:00Z"), result);
    }

    @Test
    void test_TC15_createCalendar_formatsTimeRangeMessage_ddMM_HHmm() {
        // Test Case ID: TC15
        // CheckDB: verify notification message includes expected time-range format.
        // Rollback: no persistent messaging in unit test.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        LocalDate date = LocalDate.now().plusDays(5);
        CalendarRequest request = buildBaseOnlineRequest(date, 90, true, List.of(1L), List.of(10L));
        request.setTimeFrom(LocalTime.of(9, 0));
        stubCreateCalendarHappyPath(request, 10L, 7006L);

        service.createCalendar(request);

        ArgumentCaptor<NotificationDto> notifyCaptor = ArgumentCaptor.forClass(NotificationDto.class);
        verify(kafkaUtils).sendWithJson(eq(Constants.KafkaTopic.NOTIFICATION), notifyCaptor.capture());
        String expectedRange = String.format("ngày %s từ 09:00 đến 10:30", date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")));
        assertTrue(notifyCaptor.getValue().getMessage().contains(expectedRange));
    }

    private CalendarRequest buildBaseOnlineRequest(
            LocalDate date,
            int duration,
            boolean joinSameTime,
            List<Long> candidateIds,
            List<Long> participantIds
    ) {
        return CalendarRequest.builder()
                .jobAdProcessId(5L)
                .calendarType(CalendarType.INTERVIEW_ONLINE)
                .joinSameTime(joinSameTime)
                .date(date)
                .timeFrom(LocalTime.of(9, 0))
                .durationMinutes(duration)
                .meetingLink("https://meet.google.com/abc")
                .participantIds(participantIds)
                .candidateInfoIds(candidateIds)
                .isSendEmail(false)
                .build();
    }

    private CalendarRequest buildBaseOfflineRequest(
            LocalDate date,
            int duration,
            List<Long> candidateIds,
            List<Long> participantIds
    ) {
        return CalendarRequest.builder()
                .jobAdProcessId(5L)
                .calendarType(CalendarType.INTERVIEW_OFFLINE)
                .joinSameTime(true)
                .date(date)
                .timeFrom(LocalTime.of(9, 0))
                .durationMinutes(duration)
                .orgAddressId(99L)
                .participantIds(participantIds)
                .candidateInfoIds(candidateIds)
                .isSendEmail(false)
                .build();
    }

    private void stubCreateCalendarHappyPath(CalendarRequest request, Long orgId, Long newCalendarId) {
        when(restTemplateClient.validOrgMember()).thenReturn(orgId);
        when(jobAdProcessService.existByJobAdProcessIdAndOrgId(request.getJobAdProcessId(), orgId)).thenReturn(true);
        when(restTemplateClient.checkOrgMember(request.getParticipantIds())).thenReturn(true);
        when(candidateInfoApplyService.validateCandidateInfoInProcess(request.getCandidateInfoIds(), request.getJobAdProcessId())).thenReturn(true);

        doAnswer(invocation -> {
            Calendar calendar = invocation.getArgument(0);
            calendar.setId(newCalendarId);
            return calendar;
        }).when(calendarRepository).save(any(Calendar.class));

        Map<Long, CandidateInfoApplyDto> candidates = request.getCandidateInfoIds().stream()
                .collect(java.util.stream.Collectors.toMap(
                        id -> id,
                        id -> CandidateInfoApplyDto.builder()
                                .id(id)
                                .candidateId(7000L + id)
                                .fullName("Candidate " + id)
                                .email("candidate" + id + "@mail.com")
                                .build()
                ));
        when(candidateInfoApplyService.getByIds(request.getCandidateInfoIds())).thenReturn(candidates);
        when(jobAdService.findByJobAdProcessId(request.getJobAdProcessId()))
                .thenReturn(JobAdDto.builder().id(1000L).title("Java Backend Developer").positionId(11L).orgId(orgId).build());
    }

    private static void setCurrentUser(Long userId, String... roles) {
        Map<String, Object> principal = Map.of("id", userId);
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }
}
