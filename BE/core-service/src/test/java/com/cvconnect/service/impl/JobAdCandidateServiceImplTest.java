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
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    void test_TC_HUNG_INV_001_eliminateCandidate_blocks_whenCandidateAlreadyRejected() {
        // Test Case ID: HUNG-INV-001
        // Objective: reject action must be blocked for already-rejected candidates.
        // CheckDB: verify read access occurs and no write (save) is performed.
        // Rollback: no write executed, so DB state remains unchanged.

        setCurrentUser(9003L, Constants.RoleCode.ORG_ADMIN);

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(100L, 10L)).thenReturn(true);
        when(jobAdCandidateRepository.findById(100L)).thenReturn(
                Optional.of(createJobAdCandidate(100L, 2002L, 3002L, CandidateStatus.REJECTED.name()))
        );

        EliminateCandidateRequest request = EliminateCandidateRequest.builder()
                .jobAdCandidateId(100L)
                .reason(EliminateReasonEnum.SKILL_MISMATCH)
                .reasonDetail("Does not match role expectation")
                .sendEmail(false)
                .build();

        AppException exception = assertThrows(AppException.class, () -> service.eliminateCandidate(request));
        assertEquals(CoreErrorCode.CANDIDATE_ALREADY_ELIMINATED, exception.getErrorCode());

        verify(jobAdCandidateRepository, never()).save(any(JobAdCandidate.class));
        verifyNoInteractions(kafkaUtils);
    }

    @Test
    void test_TC_HUNG_RBK_001_eliminateCandidate_propagatesException_whenNotificationPublishFails() {
        // Test Case ID: HUNG-RBK-001
        // Objective: simulate mid-flow failure after DB save to validate transactional error behavior.
        // CheckDB: verify entity is updated to REJECTED and save() is called once.
        // Rollback: in real DB, @Transactional should rollback on RuntimeException. This unit test verifies exception propagation;
        //           actual DB rollback must be confirmed by integration test with a real transactional datasource.

        setCurrentUser(9003L, Constants.RoleCode.ORG_ADMIN);

        when(restTemplateClient.validOrgMember()).thenReturn(10L);
        when(jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(100L, 10L)).thenReturn(true);

        JobAdCandidate candidate = createJobAdCandidate(100L, 2002L, 3002L, CandidateStatus.IN_PROGRESS.name());
        when(jobAdCandidateRepository.findById(100L)).thenReturn(Optional.of(candidate));

        when(jobAdService.findById(2002L)).thenReturn(JobAdDto.builder().id(2002L).title("Backend Developer").build());
        when(candidateInfoApplyService.getById(3002L)).thenReturn(CandidateInfoApplyDto.builder().candidateId(7007L).fullName("Candidate A").build());

        doThrow(new RuntimeException("Kafka is unavailable"))
                .when(kafkaUtils)
                .sendWithJson(eq(Constants.KafkaTopic.NOTIFICATION), any());

        EliminateCandidateRequest request = EliminateCandidateRequest.builder()
                .jobAdCandidateId(100L)
                .reason(EliminateReasonEnum.SKILL_MISMATCH)
                .reasonDetail("Failed coding interview")
                .sendEmail(false)
                .build();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.eliminateCandidate(request));
        assertEquals("Kafka is unavailable", exception.getMessage());

        ArgumentCaptor<JobAdCandidate> saveCaptor = ArgumentCaptor.forClass(JobAdCandidate.class);
        verify(jobAdCandidateRepository).save(saveCaptor.capture());
        assertEquals(CandidateStatus.REJECTED.name(), saveCaptor.getValue().getCandidateStatus());
        assertEquals(EliminateReasonEnum.SKILL_MISMATCH.name(), saveCaptor.getValue().getEliminateReasonType());
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
