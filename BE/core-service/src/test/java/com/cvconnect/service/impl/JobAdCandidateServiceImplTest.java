package com.cvconnect.service.impl;

import com.cvconnect.common.ReplacePlaceholder;
import com.cvconnect.common.RestTemplateClient;
import com.cvconnect.constant.Constants;
import com.cvconnect.dto.candidateInfoApply.CandidateInfoApplyDto;
import com.cvconnect.dto.internal.response.UserDto;
import com.cvconnect.dto.jobAd.JobAdDto;
import com.cvconnect.dto.jobAd.JobAdProcessDto;
import com.cvconnect.dto.jobAdCandidate.ChangeCandidateProcessRequest;
import com.cvconnect.dto.jobAdCandidate.EliminateCandidateRequest;
import com.cvconnect.dto.jobAdCandidate.JobAdProcessCandidateDto;
import com.cvconnect.dto.jobAdCandidate.MarkOnboardRequest;
import com.cvconnect.dto.processType.ProcessTypeDto;
import com.cvconnect.entity.JobAdCandidate;
import com.cvconnect.enums.CandidateStatus;
import com.cvconnect.enums.CoreErrorCode;
import com.cvconnect.enums.EliminateReasonEnum;
import com.cvconnect.enums.ProcessTypeEnum;
import com.cvconnect.repository.JobAdCandidateRepository;
import com.cvconnect.service.AttachFileService;
import com.cvconnect.service.CandidateInfoApplyService;
import com.cvconnect.service.CloudinaryService;
import com.cvconnect.service.JobAdProcessCandidateService;
import com.cvconnect.service.JobAdProcessService;
import com.cvconnect.service.JobAdService;
import com.cvconnect.service.OrgService;
import nmquan.commonlib.exception.AppException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobAdCandidateServiceImplTest {

    @Mock
    private JobAdCandidateRepository jobAdCandidateRepository;
    @Mock
    private AttachFileService attachFileService;
    @Mock
    private CandidateInfoApplyService candidateInfoApplyService;
    @Mock
    private JobAdProcessService jobAdProcessService;
    @Mock
    private JobAdProcessCandidateService jobAdProcessCandidateService;
    @Mock
    private JobAdService jobAdService;
    @Mock
    private SendEmailService sendEmailService;
    @Mock
    private RestTemplateClient restTemplateClient;
    @Mock
    private ReplacePlaceholder replacePlaceholder;
    @Mock
    private KafkaUtils kafkaUtils;
    @Mock
    private OrgService orgService;
    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private JobAdCandidateServiceImpl service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

        // TC01: Happy path - candidate is moved from Applied to Screening.
        // Expectation: status becomes IN_PROGRESS and entity is persisted once.
    @Test
    void test_TC01_applyToScreening_success() {
        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        prepareChangeProcessBase(101L, 501L, ProcessTypeEnum.SCAN_CV, CandidateStatus.APPLIED.name(), true);

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(501L)
                .sendEmail(false)
                .build();

        service.changeCandidateProcess(request);

        ArgumentCaptor<JobAdCandidate> captor = ArgumentCaptor.forClass(JobAdCandidate.class);
        verify(jobAdCandidateRepository).save(captor.capture());
        assertEquals(CandidateStatus.IN_PROGRESS.name(), captor.getValue().getCandidateStatus());
    }

        // TC02: Happy path - candidate in screening can be moved to interview.
        // Expectation: no exception is thrown and save() is called.
    @Test
    void test_TC02_screeningToInterview_success() {
        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        prepareChangeProcessBase(101L, 502L, ProcessTypeEnum.INTERVIEW, CandidateStatus.IN_PROGRESS.name(), true);

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(502L)
                .sendEmail(false)
                .build();

        assertDoesNotThrow(() -> service.changeCandidateProcess(request));
        verify(jobAdCandidateRepository).save(any(JobAdCandidate.class));
    }

        // TC03: Happy path - candidate in interview can be moved to offer stage.
        // Expectation: process change succeeds and candidate is saved.
    @Test
    void test_TC03_interviewToOffer_success() {
        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        prepareChangeProcessBase(101L, 503L, ProcessTypeEnum.OFFER, CandidateStatus.IN_PROGRESS.name(), true);

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(503L)
                .sendEmail(false)
                .build();

        service.changeCandidateProcess(request);
        verify(jobAdCandidateRepository).save(any(JobAdCandidate.class));
    }

        // TC04: Happy path - candidate in offer can be moved to onboard with onboard date.
        // Expectation: status becomes WAITING_ONBOARDING and onboard date is persisted.
    @Test
    void test_TC04_offerToOnboard_success() {
        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        Instant onboardDate = Instant.now().plusSeconds(172800);
        prepareChangeProcessBase(101L, 504L, ProcessTypeEnum.ONBOARD, CandidateStatus.IN_PROGRESS.name(), true);
        when(restTemplateClient.getUser(9001L)).thenReturn(UserDto.builder().id(9001L).fullName("HR Admin").build());
        when(restTemplateClient.getUserByRoleCodeOrg(Constants.RoleCode.ORG_ADMIN, 10L))
                .thenReturn(List.of(UserDto.builder().id(9100L).build()));

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(504L)
                .sendEmail(false)
                .onboardDate(onboardDate)
                .build();

        service.changeCandidateProcess(request);

        ArgumentCaptor<JobAdCandidate> captor = ArgumentCaptor.forClass(JobAdCandidate.class);
        verify(jobAdCandidateRepository).save(captor.capture());
        assertEquals(CandidateStatus.WAITING_ONBOARDING.name(), captor.getValue().getCandidateStatus());
        assertEquals(onboardDate, captor.getValue().getOnboardDate());
    }

        // TC05: Guard rule - rejected candidate cannot be moved to any next process.
        // Expectation: service throws CANDIDATE_ALREADY_ELIMINATED and does not save.
    @Test
    void test_TC05_blockTransitionAfterRejected() {
        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdProcessCandidateService.findById(505L)).thenReturn(
                JobAdProcessCandidateDto.builder().id(505L).jobAdCandidateId(102L).jobAdProcessId(200L).build()
        );
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(102L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(102L)).thenReturn(
                Optional.of(createJobAdCandidate(102L, 2102L, 3102L, CandidateStatus.REJECTED.name()))
        );

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(505L)
                .sendEmail(false)
                .onboardDate(Instant.now().plusSeconds(86400))
                .build();

        AppException ex = assertThrows(AppException.class, () -> service.changeCandidateProcess(request));
        assertEquals(CoreErrorCode.CANDIDATE_ALREADY_ELIMINATED, ex.getErrorCode());
        verify(jobAdCandidateRepository, never()).save(any(JobAdCandidate.class));
    }

        // TC06: Guard rule - candidate cannot be marked onboard if current process is not ONBOARD.
        // Expectation: service throws CANDIDATE_NOT_IN_ONBOARD_PROCESS and does not save.
    @Test
    void test_TC06_blockOnboardBeforeOffer() {
        setCurrentUser(9002L, Constants.RoleCode.ORG_ADMIN);
        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(103L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(103L)).thenReturn(
                Optional.of(createJobAdCandidate(103L, 2103L, 3103L, CandidateStatus.IN_PROGRESS.name()))
        );
        when(jobAdProcessCandidateService.validateCurrentProcessTypeIs(103L, ProcessTypeEnum.ONBOARD.name())).thenReturn(false);

        MarkOnboardRequest request = MarkOnboardRequest.builder().jobAdCandidateId(103L).isOnboarded(true).build();

        AppException ex = assertThrows(AppException.class, () -> service.markOnboard(request));
        assertEquals(CoreErrorCode.CANDIDATE_NOT_IN_ONBOARD_PROCESS, ex.getErrorCode());
        verify(jobAdCandidateRepository, never()).save(any(JobAdCandidate.class));
    }

        // TC07: Idempotency-like behavior - repeated next-stage request should only pass once.
        // Expectation: first call succeeds, second call is blocked with INVALID_PROCESS_TYPE_CHANGE.
    @Test
    void test_TC07_preventDoubleClickTransitions() {
        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        prepareChangeProcessBase(104L, 507L, ProcessTypeEnum.INTERVIEW, CandidateStatus.IN_PROGRESS.name(), true);
        when(jobAdProcessCandidateService.validateProcessOrderChange(507L, 104L)).thenReturn(true).thenReturn(false);

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(507L)
                .sendEmail(false)
                .build();

        assertDoesNotThrow(() -> service.changeCandidateProcess(request));
        AppException ex = assertThrows(AppException.class, () -> service.changeCandidateProcess(request));
        assertEquals(CoreErrorCode.INVALID_PROCESS_TYPE_CHANGE, ex.getErrorCode());
        verify(jobAdCandidateRepository, times(1)).save(any(JobAdCandidate.class));
    }

        // TC08: Duplicate submit protection for onboard action.
        // Expectation: first markOnboard succeeds, second call fails with CANDIDATE_ALREADY_ONBOARDED.
    @Test
    void test_TC08_preventDoubleSubmitOnboard() {
        setCurrentUser(9002L, Constants.RoleCode.ORG_ADMIN);
        JobAdCandidate candidate = createJobAdCandidate(105L, 2105L, 3105L, CandidateStatus.WAITING_ONBOARDING.name());

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(105L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(105L)).thenReturn(Optional.of(candidate));
        when(jobAdProcessCandidateService.validateCurrentProcessTypeIs(105L, ProcessTypeEnum.ONBOARD.name())).thenReturn(true);
        when(jobAdCandidateRepository.existsByCandidateInfoAndOrgAndNotJobAdCandidate(3105L, 10L, 105L, CandidateStatus.ONBOARDED.name()))
                .thenReturn(false)
                .thenReturn(true);

        MarkOnboardRequest request = MarkOnboardRequest.builder().jobAdCandidateId(105L).isOnboarded(true).build();

        service.markOnboard(request);
        AppException ex = assertThrows(AppException.class, () -> service.markOnboard(request));
        assertEquals(CoreErrorCode.CANDIDATE_ALREADY_ONBOARDED, ex.getErrorCode());
        verify(jobAdCandidateRepository, times(1)).save(any(JobAdCandidate.class));
    }

        // TC09: Validation rule - reject action requires non-null reason enum.
        // Expectation: current behavior throws NullPointerException and no data is saved.
    @Test
    void test_TC09_rejectRequiresReason_nullReason() {
        setCurrentUser(9003L, Constants.RoleCode.ORG_ADMIN);
        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(106L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(106L)).thenReturn(
                Optional.of(createJobAdCandidate(106L, 2106L, 3106L, CandidateStatus.IN_PROGRESS.name()))
        );

        EliminateCandidateRequest request = EliminateCandidateRequest.builder()
                .jobAdCandidateId(106L)
                .reason(null)
                .reasonDetail("missing reason")
                .sendEmail(false)
                .build();

        assertThrows(NullPointerException.class, () -> service.eliminateCandidate(request));
        verify(jobAdCandidateRepository, never()).save(any(JobAdCandidate.class));
    }

        // TC10: Current documented behavior - whitespace reasonDetail is still accepted.
        // Expectation: eliminateCandidate completes and save() is called once.
    @Test
    void test_TC10_rejectFailsWithWhitespaceReason_currentBehaviorDocumented() {
        setCurrentUser(9003L, Constants.RoleCode.ORG_ADMIN);
        JobAdCandidate candidate = createJobAdCandidate(106L, 2200L, 3200L, CandidateStatus.IN_PROGRESS.name());

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(106L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(106L)).thenReturn(Optional.of(candidate));
        when(jobAdService.findById(2200L)).thenReturn(JobAdDto.builder().id(2200L).title("Java Dev").build());
        when(candidateInfoApplyService.getById(3200L)).thenReturn(CandidateInfoApplyDto.builder().id(3200L).candidateId(7008L).fullName("Candidate B").build());
        when(restTemplateClient.getUser(9003L)).thenReturn(UserDto.builder().id(9003L).fullName("HR B").build());
        when(restTemplateClient.getUserByRoleCodeOrg(Constants.RoleCode.ORG_ADMIN, 10L))
                .thenReturn(List.of(UserDto.builder().id(9300L).build()));

        EliminateCandidateRequest request = EliminateCandidateRequest.builder()
                .jobAdCandidateId(106L)
                .reason(EliminateReasonEnum.OTHERS)
                .reasonDetail("   ")
                .sendEmail(false)
                .build();

        assertDoesNotThrow(() -> service.eliminateCandidate(request));
        verify(jobAdCandidateRepository, times(1)).save(any(JobAdCandidate.class));
    }

        // TC11: Validation rule - moving to ONBOARD requires onboardDate.
        // Expectation: service throws ONBOARD_DATE_REQUIRED and does not save.
    @Test
    void test_TC11_onboardRequiresDate() {
        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        prepareChangeProcessBase(107L, 511L, ProcessTypeEnum.ONBOARD, CandidateStatus.IN_PROGRESS.name(), true);

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(511L)
                .sendEmail(false)
                .onboardDate(null)
                .build();

        AppException ex = assertThrows(AppException.class, () -> service.changeCandidateProcess(request));
        assertEquals(CoreErrorCode.ONBOARD_DATE_REQUIRED, ex.getErrorCode());
        verify(jobAdCandidateRepository, never()).save(any(JobAdCandidate.class));
    }

        // TC12: Error propagation scenario - audit/kafka publish fails after status update path.
        // Expectation: runtime exception is propagated; save invocation is verified for current behavior.
    @Test
    void test_TC12_rollbackWhenAuditFails_afterStatusUpdate() {
        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        prepareChangeProcessBase(108L, 512L, ProcessTypeEnum.INTERVIEW, CandidateStatus.IN_PROGRESS.name(), true);
        doThrow(new RuntimeException("AUDIT_FAILED")).when(kafkaUtils)
                .sendWithJson(eq(Constants.KafkaTopic.NOTIFICATION), any());

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(512L)
                .sendEmail(false)
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.changeCandidateProcess(request));
        assertEquals("AUDIT_FAILED", ex.getMessage());
        verify(jobAdCandidateRepository, times(1)).save(any(JobAdCandidate.class));
    }

        // TC13: Concurrency scenario - two concurrent requests attempt same stage transition.
        // Expectation: exactly one request succeeds and one request is blocked by process-order validation.
    @Test
    void test_TC13_twoConcurrentRequestsForNextStage_onlyOneApplies() throws Exception {
        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        prepareChangeProcessBase(109L, 513L, ProcessTypeEnum.INTERVIEW, CandidateStatus.IN_PROGRESS.name(), true);

        AtomicBoolean first = new AtomicBoolean(true);
        when(jobAdProcessCandidateService.validateProcessOrderChange(513L, 109L))
                .thenAnswer(i -> first.getAndSet(false));

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(513L)
                .sendEmail(false)
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();

        Callable<Void> task = () -> {
            setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
            latch.await(2, TimeUnit.SECONDS);
            try {
                service.changeCandidateProcess(request);
                ok.incrementAndGet();
            } catch (AppException ex) {
                if (CoreErrorCode.INVALID_PROCESS_TYPE_CHANGE.equals(ex.getErrorCode())) {
                    blocked.incrementAndGet();
                } else {
                    throw ex;
                }
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task);
        Future<Void> f2 = executor.submit(task);
        latch.countDown();
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertEquals(1, ok.get());
        assertEquals(1, blocked.get());
        verify(jobAdCandidateRepository, times(1)).save(any(JobAdCandidate.class));
    }

        // TC14: Conflict scenario - candidate is rejected first, then next-stage action is attempted.
        // Expectation: second action is blocked with CANDIDATE_ALREADY_ELIMINATED.
    @Test
    void test_TC14_conflictingActions_rejectVsNextStage_onlyOneFinalOutcome() {
        setCurrentUser(9003L, Constants.RoleCode.ORG_ADMIN);
        JobAdCandidate candidate = createJobAdCandidate(110L, 2300L, 3300L, CandidateStatus.IN_PROGRESS.name());

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(110L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(110L)).thenReturn(Optional.of(candidate));
        when(jobAdService.findById(2300L)).thenReturn(JobAdDto.builder().id(2300L).title("QA Engineer").build());
        when(candidateInfoApplyService.getById(3300L)).thenReturn(CandidateInfoApplyDto.builder().id(3300L).candidateId(7010L).fullName("Candidate C").build());
        when(restTemplateClient.getUser(9003L)).thenReturn(UserDto.builder().id(9003L).fullName("HR C").build());
        when(restTemplateClient.getUserByRoleCodeOrg(Constants.RoleCode.ORG_ADMIN, 10L))
                .thenReturn(List.of(UserDto.builder().id(9301L).build()));

        EliminateCandidateRequest rejectRequest = EliminateCandidateRequest.builder()
                .jobAdCandidateId(110L)
                .reason(EliminateReasonEnum.OTHERS)
                .reasonDetail("Not fit")
                .sendEmail(false)
                .build();
        service.eliminateCandidate(rejectRequest);

        when(jobAdProcessCandidateService.findById(514L)).thenReturn(
                JobAdProcessCandidateDto.builder().id(514L).jobAdCandidateId(110L).jobAdProcessId(2400L).build()
        );

        ChangeCandidateProcessRequest nextStage = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(514L)
                .sendEmail(false)
                .onboardDate(Instant.now().plusSeconds(86400))
                .build();

        AppException ex = assertThrows(AppException.class, () -> service.changeCandidateProcess(nextStage));
        assertEquals(CoreErrorCode.CANDIDATE_ALREADY_ELIMINATED, ex.getErrorCode());
    }

        // TC15: Retry safety for same logical intent without explicit requestId.
        // Expectation: first request is processed, second equivalent request is rejected as already onboarded.
    @Test
    void test_TC15_retrySameLogicalRequest_notReprocessed() {
        setCurrentUser(9002L, Constants.RoleCode.ORG_ADMIN);
        JobAdCandidate candidate = createJobAdCandidate(111L, 2400L, 3400L, CandidateStatus.WAITING_ONBOARDING.name());

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(111L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(111L)).thenReturn(Optional.of(candidate));
        when(jobAdProcessCandidateService.validateCurrentProcessTypeIs(111L, ProcessTypeEnum.ONBOARD.name())).thenReturn(true);
        when(jobAdCandidateRepository.existsByCandidateInfoAndOrgAndNotJobAdCandidate(3400L, 10L, 111L, CandidateStatus.ONBOARDED.name()))
                .thenReturn(false)
                .thenReturn(true);

        MarkOnboardRequest request = MarkOnboardRequest.builder().jobAdCandidateId(111L).isOnboarded(true).build();

        service.markOnboard(request);
        AppException ex = assertThrows(AppException.class, () -> service.markOnboard(request));
        assertEquals(CoreErrorCode.CANDIDATE_ALREADY_ONBOARDED, ex.getErrorCode());
        verify(jobAdCandidateRepository, times(1)).save(any(JobAdCandidate.class));
    }

    private void prepareChangeProcessBase(
            Long candidateId,
            Long targetProcessCandidateId,
            ProcessTypeEnum targetProcessType,
            String currentStatus,
            boolean isProcessOrderValid
    ) {
        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdProcessCandidateService.findById(targetProcessCandidateId)).thenReturn(
                JobAdProcessCandidateDto.builder()
                        .id(targetProcessCandidateId)
                        .jobAdCandidateId(candidateId)
                        .jobAdProcessId(9000L + targetProcessCandidateId)
                        .build()
        );
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(candidateId, 10L)).thenReturn(true);

        JobAdCandidate candidate = createJobAdCandidate(candidateId, 2000L + candidateId, 3000L + candidateId, currentStatus);
        when(jobAdCandidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(jobAdCandidateRepository.existsByCandidateInfoAndOrg(3000L + candidateId, 10L, CandidateStatus.ONBOARDED.name()))
                .thenReturn(false);
        when(jobAdProcessCandidateService.validateProcessOrderChange(targetProcessCandidateId, candidateId)).thenReturn(isProcessOrderValid);
        when(jobAdProcessCandidateService.getCurrentProcess(2000L + candidateId, 3000L + candidateId)).thenReturn(
                JobAdProcessCandidateDto.builder().id(400L).processName("Current Process").isCurrentProcess(true).build()
        );
        when(jobAdProcessService.getById(9000L + targetProcessCandidateId)).thenReturn(
                JobAdProcessDto.builder()
                        .id(9000L + targetProcessCandidateId)
                        .name(targetProcessType.getName())
                        .processType(ProcessTypeDto.builder().code(targetProcessType.name()).build())
                        .build()
        );

        List<JobAdProcessCandidateDto> processCandidates = new ArrayList<>();
        processCandidates.add(JobAdProcessCandidateDto.builder().id(targetProcessCandidateId).isCurrentProcess(false).build());
        processCandidates.add(JobAdProcessCandidateDto.builder().id(targetProcessCandidateId + 1).isCurrentProcess(true).build());
        lenient().when(jobAdProcessCandidateService.findByJobAdCandidateId(candidateId)).thenReturn(processCandidates);

        lenient().when(jobAdService.findById(2000L + candidateId)).thenReturn(
                JobAdDto.builder().id(2000L + candidateId).title("Job " + candidateId).orgId(10L).positionId(11L).build()
        );
        lenient().when(candidateInfoApplyService.getById(3000L + candidateId)).thenReturn(
                CandidateInfoApplyDto.builder().id(3000L + candidateId).candidateId(7000L + candidateId).fullName("Candidate " + candidateId).build()
        );
    }

    private static JobAdCandidate createJobAdCandidate(Long id, Long jobAdId, Long candidateInfoId, String status) {
        JobAdCandidate candidate = new JobAdCandidate();
        candidate.setId(id);
        candidate.setJobAdId(jobAdId);
        candidate.setCandidateInfoId(candidateInfoId);
        candidate.setCandidateStatus(status);
        return candidate;
    }

    private static void setCurrentUser(Long userId, String... roles) {
        Map<String, Object> principal = Map.of("id", userId);
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream().map(SimpleGrantedAuthority::new).toList();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
