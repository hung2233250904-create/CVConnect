package com.cvconnect.service.impl;

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
import com.cvconnect.service.*;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    private com.cvconnect.common.RestTemplateClient restTemplateClient;
    @Mock
    private com.cvconnect.common.ReplacePlaceholder replacePlaceholder;
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
        // Rollback note: clear authentication context after each test to avoid cross-test pollution.
        SecurityContextHolder.clearContext();
    }

    @Test
    void test_TC_HUNG_STS_009_changeCandidateProcess_requiresOnboardDate_whenTargetProcessIsOnboard() {
        // Test Case ID: HUNG-STS-009
        // Objective: Validate mandatory onboard date when changing to ONBOARD process.
        // CheckDB: verify DB access/save methods are invoked only up to validation boundary.
        // Rollback: this is a unit test with mocked DB; no real DB mutation occurs.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdProcessCandidateService.findById(501L)).thenReturn(
                JobAdProcessCandidateDto.builder()
                        .id(501L)
                        .jobAdCandidateId(100L)
                        .jobAdProcessId(200L)
                        .build()
        );
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(100L, 10L)).thenReturn(true);

        JobAdCandidate existingCandidate = createJobAdCandidate(100L, 2000L, 3000L, CandidateStatus.APPLIED.name());
        when(jobAdCandidateRepository.findById(100L)).thenReturn(Optional.of(existingCandidate));
        when(jobAdCandidateRepository.existsByCandidateInfoAndOrg(3000L, 10L, CandidateStatus.ONBOARDED.name())).thenReturn(false);
        when(jobAdProcessCandidateService.validateProcessOrderChange(501L, 100L)).thenReturn(true);
        when(jobAdProcessCandidateService.getCurrentProcess(2000L, 3000L)).thenReturn(
                JobAdProcessCandidateDto.builder().processName("Interview").build()
        );
        when(jobAdProcessService.getById(200L)).thenReturn(
                JobAdProcessDto.builder()
                        .id(200L)
                        .processType(ProcessTypeDto.builder().code(ProcessTypeEnum.ONBOARD.name()).build())
                        .build()
        );

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(501L)
                .sendEmail(false)
                .build();

        AppException exception = assertThrows(AppException.class, () -> service.changeCandidateProcess(request));
        assertEquals(CoreErrorCode.ONBOARD_DATE_REQUIRED, exception.getErrorCode());

        // CheckDB assertions: service read path is used, but persistent write must not happen after validation fails.
        verify(jobAdCandidateRepository, never()).save(any(JobAdCandidate.class));
        verify(jobAdProcessCandidateService, never()).create(anyList());
    }

    @Test
    void test_TC_HUNG_INV_001_changeCandidateProcess_blocksTransition_whenCandidateAlreadyRejected() {
        // Test Case ID: HUNG-INV-001
        // Objective: Reject state must block further process transitions.
        // CheckDB: verify candidate is loaded and no update is persisted when guard fails.
        // Rollback: unit test uses mock repository; state remains unchanged by design.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdProcessCandidateService.findById(501L)).thenReturn(
                JobAdProcessCandidateDto.builder().id(501L).jobAdCandidateId(100L).jobAdProcessId(200L).build()
        );
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(100L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(100L)).thenReturn(
                Optional.of(createJobAdCandidate(100L, 2000L, 3000L, CandidateStatus.REJECTED.name()))
        );

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(501L)
                .sendEmail(false)
                .onboardDate(Instant.now().plusSeconds(86_400))
                .build();

        AppException exception = assertThrows(AppException.class, () -> service.changeCandidateProcess(request));
        assertEquals(CoreErrorCode.CANDIDATE_ALREADY_ELIMINATED, exception.getErrorCode());

        verify(jobAdCandidateRepository, never()).save(any(JobAdCandidate.class));
    }

    @Test
    void test_TC_HUNG_STS_004_markOnboard_updatesCandidateStatusToOnboarded_whenAllGuardsPass() {
        // Test Case ID: HUNG-STS-004
        // Objective: mark candidate as ONBOARDED in a valid onboarding process.
        // CheckDB: verify repository.save() receives candidate status ONBOARDED.
        // Rollback: mock DB is used; no persisted data remains after test execution.

        setCurrentUser(9002L, Constants.RoleCode.ORG_ADMIN);

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(100L, 10L)).thenReturn(true);

        JobAdCandidate candidate = createJobAdCandidate(100L, 2001L, 3001L, CandidateStatus.WAITING_ONBOARDING.name());
        when(jobAdCandidateRepository.findById(100L)).thenReturn(Optional.of(candidate));
        when(jobAdProcessCandidateService.validateCurrentProcessTypeIs(100L, ProcessTypeEnum.ONBOARD.name())).thenReturn(true);
        when(jobAdCandidateRepository.existsByCandidateInfoAndOrgAndNotJobAdCandidate(3001L, 10L, 100L, CandidateStatus.ONBOARDED.name()))
                .thenReturn(false);

        MarkOnboardRequest request = MarkOnboardRequest.builder()
                .jobAdCandidateId(100L)
                .isOnboarded(true)
                .build();

        service.markOnboard(request);

        ArgumentCaptor<JobAdCandidate> captor = ArgumentCaptor.forClass(JobAdCandidate.class);
        verify(jobAdCandidateRepository).save(captor.capture());
        assertEquals(CandidateStatus.ONBOARDED.name(), captor.getValue().getCandidateStatus());
    }

    @Test
    void test_TC_HUNG_INV_002_markOnboard_blocks_whenCurrentProcessIsNotOnboard() {
        // Test Case ID: HUNG-INV-002
        // Objective: onboarding is invalid if candidate is not currently in ONBOARD process.
        // CheckDB: verify validation reads are performed and save is not executed.
        // Rollback: no DB change because save is blocked before mutation.

        setCurrentUser(9002L, Constants.RoleCode.ORG_ADMIN);

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(100L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(100L)).thenReturn(
                Optional.of(createJobAdCandidate(100L, 2001L, 3001L, CandidateStatus.IN_PROGRESS.name()))
        );
        when(jobAdProcessCandidateService.validateCurrentProcessTypeIs(100L, ProcessTypeEnum.ONBOARD.name())).thenReturn(false);

        MarkOnboardRequest request = MarkOnboardRequest.builder()
                .jobAdCandidateId(100L)
                .isOnboarded(true)
                .build();

        AppException exception = assertThrows(AppException.class, () -> service.markOnboard(request));
        assertEquals(CoreErrorCode.CANDIDATE_NOT_IN_ONBOARD_PROCESS, exception.getErrorCode());

        verify(jobAdCandidateRepository, never()).save(any(JobAdCandidate.class));
    }


    @Test
    void test_TC01_changeCandidateProcess_applyToScanCv_success_updatesStatusAndHistory() {
        // Test Case ID: TC01
        // Objective: valid transition APPLY -> SCREENING(SCAN_CV) updates status and history.
        // CheckDB: verify candidate status becomes IN_PROGRESS and process history has one current process.
        // Rollback: unit test uses mocks; no real DB transaction persisted.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        Long candidateId = 101L;
        Long targetProcessCandidateId = 501L;

        prepareChangeProcessBase(candidateId, targetProcessCandidateId, ProcessTypeEnum.SCAN_CV, CandidateStatus.APPLIED.name(), true);

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(targetProcessCandidateId)
                .sendEmail(false)
                .build();

        service.changeCandidateProcess(request);

        ArgumentCaptor<JobAdCandidate> candidateCaptor = ArgumentCaptor.forClass(JobAdCandidate.class);
        ArgumentCaptor<List<JobAdProcessCandidateDto>> processCaptor = ArgumentCaptor.forClass(List.class);
        verify(jobAdCandidateRepository).save(candidateCaptor.capture());
        verify(jobAdProcessCandidateService).create(processCaptor.capture());
        assertEquals(CandidateStatus.IN_PROGRESS.name(), candidateCaptor.getValue().getCandidateStatus());
        assertEquals(1L, processCaptor.getValue().stream().filter(dto -> Boolean.TRUE.equals(dto.getIsCurrentProcess())).count());
        assertTrue(processCaptor.getValue().stream().filter(dto -> Boolean.TRUE.equals(dto.getIsCurrentProcess()))
                .allMatch(dto -> dto.getActionDate() != null));
    }

    @Test
    void test_TC02_changeCandidateProcess_scanCvToInterview_success_noErrors() {
        // Test Case ID: TC02
        // Objective: valid transition SCREENING(SCAN_CV) -> INTERVIEW runs successfully.
        // CheckDB: verify candidate is saved and transition list is updated.
        // Rollback: mock-based test, no persistent DB data.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        Long candidateId = 101L;
        Long targetProcessCandidateId = 502L;

        prepareChangeProcessBase(candidateId, targetProcessCandidateId, ProcessTypeEnum.INTERVIEW, CandidateStatus.IN_PROGRESS.name(), true);

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(targetProcessCandidateId)
                .sendEmail(false)
                .build();

        assertDoesNotThrow(() -> service.changeCandidateProcess(request));
        verify(jobAdCandidateRepository).save(any(JobAdCandidate.class));
        verify(jobAdProcessCandidateService).create(anyList());
    }

    @Test
    void test_TC03_changeCandidateProcess_interviewToOffer_success_noErrors() {
        // Test Case ID: TC03
        // Objective: valid transition INTERVIEW -> OFFER runs successfully.
        // CheckDB: verify candidate is saved with in-progress state and process history updates.
        // Rollback: unit test with mocks only.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        Long candidateId = 101L;
        Long targetProcessCandidateId = 503L;

        prepareChangeProcessBase(candidateId, targetProcessCandidateId, ProcessTypeEnum.OFFER, CandidateStatus.IN_PROGRESS.name(), true);

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(targetProcessCandidateId)
                .sendEmail(false)
                .build();

        assertDoesNotThrow(() -> service.changeCandidateProcess(request));
        verify(jobAdCandidateRepository).save(any(JobAdCandidate.class));
    }

    @Test
    void test_TC04_changeCandidateProcess_offerToOnboard_success_setsWaitingAndDate() {
        // Test Case ID: TC04
        // Objective: valid transition OFFER -> ONBOARD stores onboard date and waiting status.
        // CheckDB: verify status WAITING_ONBOARDING and onboardDate are saved.
        // Rollback: no real DB transaction in unit test.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        Long candidateId = 101L;
        Long targetProcessCandidateId = 504L;
        Instant onboardDate = Instant.now().plusSeconds(172_800);

        prepareChangeProcessBase(candidateId, targetProcessCandidateId, ProcessTypeEnum.ONBOARD, CandidateStatus.IN_PROGRESS.name(), true);
        when(restTemplateClient.getUser(9001L)).thenReturn(UserDto.builder().id(9001L).fullName("HR Admin").build());
        when(restTemplateClient.getUserByRoleCodeOrg(Constants.RoleCode.ORG_ADMIN, 10L))
                .thenReturn(List.of(UserDto.builder().id(9100L).build()));

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(targetProcessCandidateId)
                .sendEmail(false)
                .onboardDate(onboardDate)
                .build();

        service.changeCandidateProcess(request);

        ArgumentCaptor<JobAdCandidate> candidateCaptor = ArgumentCaptor.forClass(JobAdCandidate.class);
        verify(jobAdCandidateRepository).save(candidateCaptor.capture());
        assertEquals(CandidateStatus.WAITING_ONBOARDING.name(), candidateCaptor.getValue().getCandidateStatus());
        assertEquals(onboardDate, candidateCaptor.getValue().getOnboardDate());
    }

    @Test
    void test_TC07_changeCandidateProcess_doubleClick_onlyFirstCallApplies() {
        // Test Case ID: TC07
        // Objective: prevent double-click transitions from skipping stages.
        // CheckDB: first call saves candidate; second call blocked before save.
        // Rollback: no committed DB state in unit test.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        Long candidateId = 104L;
        Long targetProcessCandidateId = 507L;

        prepareChangeProcessBase(candidateId, targetProcessCandidateId, ProcessTypeEnum.INTERVIEW, CandidateStatus.IN_PROGRESS.name(), true);
        when(jobAdProcessCandidateService.validateProcessOrderChange(targetProcessCandidateId, candidateId))
                .thenReturn(true)
                .thenReturn(false);

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(targetProcessCandidateId)
                .sendEmail(false)
                .build();

        assertDoesNotThrow(() -> service.changeCandidateProcess(request));
        AppException exception = assertThrows(AppException.class, () -> service.changeCandidateProcess(request));
        assertEquals(CoreErrorCode.INVALID_PROCESS_TYPE_CHANGE, exception.getErrorCode());

        verify(jobAdCandidateRepository, times(1)).save(any(JobAdCandidate.class));
    }

    @Test
    void test_TC08_markOnboard_doubleSubmit_onlyFirstUpdateIsApplied() {
        // Test Case ID: TC08
        // Objective: prevent duplicate onboard submit.
        // CheckDB: first call saves ONBOARDED, second call blocked by onboarded guard.
        // Rollback: mock-based verification only.

        setCurrentUser(9002L, Constants.RoleCode.ORG_ADMIN);
        JobAdCandidate candidate = createJobAdCandidate(105L, 2100L, 3100L, CandidateStatus.WAITING_ONBOARDING.name());

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(105L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(105L)).thenReturn(Optional.of(candidate));
        when(jobAdProcessCandidateService.validateCurrentProcessTypeIs(105L, ProcessTypeEnum.ONBOARD.name())).thenReturn(true);
        when(jobAdCandidateRepository.existsByCandidateInfoAndOrgAndNotJobAdCandidate(3100L, 10L, 105L, CandidateStatus.ONBOARDED.name()))
                .thenReturn(false)
                .thenReturn(true);

        MarkOnboardRequest request = MarkOnboardRequest.builder()
                .jobAdCandidateId(105L)
                .isOnboarded(true)
                .build();

        assertDoesNotThrow(() -> service.markOnboard(request));
        AppException exception = assertThrows(AppException.class, () -> service.markOnboard(request));
        assertEquals(CoreErrorCode.CANDIDATE_ALREADY_ONBOARDED, exception.getErrorCode());
        verify(jobAdCandidateRepository, times(1)).save(any(JobAdCandidate.class));
    }

    @Test
    void test_TC09_eliminateCandidate_requiresReason_whenReasonIsNull() {
        // Test Case ID: TC09
        // Objective: null reject reason is invalid (current implementation throws at request.getReason().name()).
        // CheckDB: verify no save occurs when reason is null.
        // Rollback: no write occurs because flow fails before repository.save().

        setCurrentUser(9003L, Constants.RoleCode.ORG_ADMIN);

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(106L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(106L)).thenReturn(
                Optional.of(createJobAdCandidate(106L, 2200L, 3200L, CandidateStatus.IN_PROGRESS.name()))
        );

        EliminateCandidateRequest request = EliminateCandidateRequest.builder()
                .jobAdCandidateId(106L)
                .reason(null)
                .reasonDetail("Missing reason")
                .sendEmail(false)
                .build();

        assertThrows(NullPointerException.class, () -> service.eliminateCandidate(request));
        verify(jobAdCandidateRepository, never()).save(any(JobAdCandidate.class));
    }

    @Test
    void test_TC10_eliminateCandidate_withWhitespaceReasonDetail_currentBehaviorStillSaves() {
        // Test Case ID: TC10
        // Objective: cover whitespace reason detail input path in current implementation.
        // CheckDB: verify save is called and eliminate reason detail is persisted as provided.
        // Rollback: no real DB commit in unit test.

        setCurrentUser(9003L, Constants.RoleCode.ORG_ADMIN);
        JobAdCandidate candidate = createJobAdCandidate(106L, 2200L, 3200L, CandidateStatus.IN_PROGRESS.name());

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(106L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(106L)).thenReturn(Optional.of(candidate));
        when(jobAdService.findById(2200L)).thenReturn(JobAdDto.builder().id(2200L).title("Java Developer").build());
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

        ArgumentCaptor<JobAdCandidate> saveCaptor = ArgumentCaptor.forClass(JobAdCandidate.class);
        verify(jobAdCandidateRepository).save(saveCaptor.capture());
        assertEquals("   ", saveCaptor.getValue().getEliminateReasonDetail());
    }

    @Test
    void test_TC13_changeCandidateProcess_rollbackErrorContract_propagatesDefinedMessage() {
        // Test Case ID: TC13
        // Objective: verify exact error contract when notification publish fails after state update.
        // CheckDB: save() is invoked before failure; exception message is preserved.
        // Rollback: transactional rollback must be validated in integration tests.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        Long candidateId = 108L;
        Long targetProcessCandidateId = 513L;

        prepareChangeProcessBase(candidateId, targetProcessCandidateId, ProcessTypeEnum.INTERVIEW, CandidateStatus.IN_PROGRESS.name(), true);
        doThrow(new RuntimeException("NOTIFY_PUBLISH_FAILED"))
                .when(kafkaUtils)
                .sendWithJson(eq(Constants.KafkaTopic.NOTIFICATION), any());

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(targetProcessCandidateId)
                .sendEmail(false)
                .build();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.changeCandidateProcess(request));
        assertEquals("NOTIFY_PUBLISH_FAILED", exception.getMessage());
        verify(jobAdCandidateRepository).save(any(JobAdCandidate.class));
    }

    @Test
    void test_TC14_changeCandidateProcess_twoConcurrentRequests_onlyOneTransitionApplies() throws Exception {
        // Test Case ID: TC14
        // Objective: basic race-condition simulation for two concurrent next-stage requests.
        // CheckDB: one request succeeds and one is blocked by process-order validation.
        // Rollback: no persistent state in this unit-level simulation.

        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
        Long candidateId = 109L;
        Long targetProcessCandidateId = 514L;

        prepareChangeProcessBase(candidateId, targetProcessCandidateId, ProcessTypeEnum.INTERVIEW, CandidateStatus.IN_PROGRESS.name(), true);
        AtomicBoolean firstGate = new AtomicBoolean(true);
        when(jobAdProcessCandidateService.validateProcessOrderChange(targetProcessCandidateId, candidateId))
                .thenAnswer(invocation -> firstGate.getAndSet(false));

        ChangeCandidateProcessRequest request = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(targetProcessCandidateId)
                .sendEmail(false)
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger blockedCount = new AtomicInteger();

        Callable<Void> task = () -> {
                        setCurrentUser(9001L, Constants.RoleCode.ORG_ADMIN);
            startLatch.await(2, TimeUnit.SECONDS);
            try {
                service.changeCandidateProcess(request);
                successCount.incrementAndGet();
            } catch (AppException ex) {
                if (CoreErrorCode.INVALID_PROCESS_TYPE_CHANGE.equals(ex.getErrorCode())) {
                    blockedCount.incrementAndGet();
                } else {
                    throw ex;
                }
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task);
        Future<Void> f2 = executor.submit(task);
        startLatch.countDown();
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertEquals(1, successCount.get());
        assertEquals(1, blockedCount.get());
        verify(jobAdCandidateRepository, times(1)).save(any(JobAdCandidate.class));
    }

    @Test
    void test_TC15_conflictingActions_rejectThenNextStage_onlyOneFinalValidOutcome() {
        // Test Case ID: TC15
        // Objective: conflict simulation between reject and next-stage by asserting next-stage is blocked after reject.
        // CheckDB: reject updates candidate to REJECTED; later transition attempt is blocked.
        // Rollback: unit test validates business guard without real lock/version manager.

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

        when(jobAdProcessCandidateService.findById(515L)).thenReturn(
                JobAdProcessCandidateDto.builder().id(515L).jobAdCandidateId(110L).jobAdProcessId(2400L).build()
        );

        ChangeCandidateProcessRequest nextStageRequest = ChangeCandidateProcessRequest.builder()
                .toJobAdProcessCandidateId(515L)
                .sendEmail(false)
                .onboardDate(Instant.now().plusSeconds(86_400))
                .build();

        AppException exception = assertThrows(AppException.class, () -> service.changeCandidateProcess(nextStageRequest));
        assertEquals(CoreErrorCode.CANDIDATE_ALREADY_ELIMINATED, exception.getErrorCode());
    }

    @Test
    void test_TC16_retryLogicalSameOnboardIntent_secondCallIsBlocked_noDuplicateSave() {
        // Test Case ID: TC16
        // Objective: idempotency-like behavior for repeated onboard intent.
        // CheckDB: only first call saves; second call blocked by already-onboarded guard.
        // Rollback: mock-only unit test, no real persistence.

        setCurrentUser(9002L, Constants.RoleCode.ORG_ADMIN);
        JobAdCandidate candidate = createJobAdCandidate(111L, 2400L, 3400L, CandidateStatus.WAITING_ONBOARDING.name());

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(111L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(111L)).thenReturn(Optional.of(candidate));
        when(jobAdProcessCandidateService.validateCurrentProcessTypeIs(111L, ProcessTypeEnum.ONBOARD.name())).thenReturn(true);
        when(jobAdCandidateRepository.existsByCandidateInfoAndOrgAndNotJobAdCandidate(3400L, 10L, 111L, CandidateStatus.ONBOARDED.name()))
                .thenReturn(false)
                .thenReturn(true);

        MarkOnboardRequest request = MarkOnboardRequest.builder()
                .jobAdCandidateId(111L)
                .isOnboarded(true)
                .build();

        service.markOnboard(request);
        AppException exception = assertThrows(AppException.class, () -> service.markOnboard(request));
        assertEquals(CoreErrorCode.CANDIDATE_ALREADY_ONBOARDED, exception.getErrorCode());
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
        when(jobAdProcessCandidateService.validateProcessOrderChange(targetProcessCandidateId, candidateId))
                .thenReturn(isProcessOrderValid);
        when(jobAdProcessCandidateService.getCurrentProcess(2000L + candidateId, 3000L + candidateId)).thenReturn(
                JobAdProcessCandidateDto.builder().id(499L).processName("Current Process").isCurrentProcess(true).build()
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
        processCandidates.add(JobAdProcessCandidateDto.builder().id(targetProcessCandidateId + 1000).isCurrentProcess(true).build());
        when(jobAdProcessCandidateService.findByJobAdCandidateId(candidateId)).thenReturn(processCandidates);

        when(jobAdService.findById(2000L + candidateId))
                .thenReturn(JobAdDto.builder().id(2000L + candidateId).title("Job " + candidateId).orgId(10L).positionId(100L).build());
        when(candidateInfoApplyService.getById(3000L + candidateId))
                .thenReturn(CandidateInfoApplyDto.builder().id(3000L + candidateId).candidateId(7000L + candidateId).fullName("Candidate " + candidateId).build());
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
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }
}
