package com.cvconnect.service.impl;

import com.cvconnect.common.ReplacePlaceholder;
import com.cvconnect.common.RestTemplateClient;
import com.cvconnect.constant.Constants;
import com.cvconnect.dto.attachFile.AttachFileDto;
import com.cvconnect.dto.candidateInfoApply.CandidateInfoApplyProjection;
import com.cvconnect.dto.candidateInfoApply.CandidateInfoDetail;
import com.cvconnect.dto.candidateSummaryOrg.CandidateSummaryOrgDto;
import com.cvconnect.dto.common.DataReplacePlaceholder;
import com.cvconnect.dto.candidateInfoApply.CandidateInfoApplyDto;
import com.cvconnect.dto.common.NotificationDto;
import com.cvconnect.dto.internal.response.ConversationDto;
import com.cvconnect.dto.internal.response.EmailConfigDto;
import com.cvconnect.dto.internal.response.EmailTemplateDto;
import com.cvconnect.dto.internal.response.UserDto;
import com.cvconnect.dto.jobAd.JobAdDto;
import com.cvconnect.dto.jobAd.JobAdProcessDto;
import com.cvconnect.dto.jobAdCandidate.*;
import com.cvconnect.dto.level.LevelDto;
import com.cvconnect.dto.org.OrgDto;
import com.cvconnect.dto.processType.ProcessTypeDto;
import com.cvconnect.entity.JobAdCandidate;
import com.cvconnect.enums.*;
import com.cvconnect.repository.JobAdCandidateRepository;
import com.cvconnect.service.*;
import com.cvconnect.utils.CoreServiceUtils;
import lombok.extern.slf4j.Slf4j;
import nmquan.commonlib.constant.CommonConstants;
import nmquan.commonlib.dto.SendEmailDto;
import nmquan.commonlib.dto.response.FilterResponse;
import nmquan.commonlib.dto.response.IDResponse;
import nmquan.commonlib.exception.AppException;
import nmquan.commonlib.exception.CommonErrorCode;
import nmquan.commonlib.service.SendEmailService;
import nmquan.commonlib.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JobAdCandidateServiceImpl implements JobAdCandidateService {
    @Autowired
    private JobAdCandidateRepository jobAdCandidateRepository;
    @Autowired
    private AttachFileService attachFileService;
    @Autowired
    private CandidateInfoApplyService candidateInfoApplyService;
    @Autowired
    private JobAdProcessService jobAdProcessService;
    @Autowired
    private JobAdProcessCandidateService jobAdProcessCandidateService;
    @Autowired
    private JobAdService jobAdService;
    @Autowired
    private SendEmailService sendEmailService;
    @Autowired
    private RestTemplateClient restTemplateClient;
    @Autowired
    private ReplacePlaceholder replacePlaceholder;
    @Autowired
    private KafkaUtils kafkaUtils;
    @Autowired
    private OrgService orgService;
    @Autowired
    private CloudinaryService cloudinaryService;

    @Override
    @Transactional
    public IDResponse<Long> apply(ApplyRequest request, MultipartFile cvFile) {
        // validate
        Long userId = WebUtils.getCurrentUserId();
        request.setCandidateId(userId);
        this.validateApply(request, cvFile);
        JobAdDto jobAdDto = this.validateJobAd(request.getJobAdId());

        // save candidate info apply if not exist
        Long candidateInfoApplyId = request.getCandidateInfoApplyId();
        if(candidateInfoApplyId == null){
            CoreServiceUtils.validateDocumentFileInput(cvFile);
            MultipartFile[] files = new MultipartFile[]{cvFile};
            List<Long> cvFileIds = attachFileService.uploadFile(files);
            CandidateInfoApplyDto candidateInfoApplyDto = CandidateInfoApplyDto.builder()
                    .candidateId(request.getCandidateId())
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .phone(request.getPhone())
                    .cvFileId(cvFileIds.get(0))
                    .coverLetter(request.getCoverLetter())
                    .build();
            List<Long> candidateInfoIds = candidateInfoApplyService.create(List.of(candidateInfoApplyDto));
            candidateInfoApplyId = candidateInfoIds.get(0);
        }

        // save job ad candidate
        Instant instantNow = ZonedDateTime.now(CommonConstants.ZONE.UTC).toInstant();

        JobAdCandidate jobAdCandidate = new JobAdCandidate();
        jobAdCandidate.setJobAdId(request.getJobAdId());
        jobAdCandidate.setCandidateInfoId(candidateInfoApplyId);
        jobAdCandidate.setApplyDate(instantNow);
        jobAdCandidate.setCandidateStatus(CandidateStatus.APPLIED.name());
        jobAdCandidateRepository.save(jobAdCandidate);


        // save job ad process candidate
        List<JobAdProcessDto> jobAdProcessDtos = jobAdProcessService.getByJobAdId(request.getJobAdId());
        List<JobAdProcessCandidateDto> jobAdProcessCandidateDtos = jobAdProcessDtos.stream()
                .map(process -> {
                    JobAdProcessCandidateDto dto = new JobAdProcessCandidateDto();
                    dto.setJobAdProcessId(process.getId());
                    dto.setJobAdCandidateId(jobAdCandidate.getId());
                    dto.setIsCurrentProcess(false);
                    if(ProcessTypeEnum.APPLY.name().equals(process.getProcessType().getCode())){
                        dto.setIsCurrentProcess(true);
                        dto.setActionDate(instantNow);
                    }
                    return dto;
                })
                .toList();
        jobAdProcessCandidateService.create(jobAdProcessCandidateDtos);

        // send email to candidate
        CandidateInfoApplyDto candidateInfoApplyDto = candidateInfoApplyService.getById(candidateInfoApplyId);
        try {
            if(jobAdDto.getIsAutoSendEmail()){
                EmailTemplateDto emailTemplateDto = restTemplateClient.getEmailTemplateById(jobAdDto.getEmailTemplateId());
                if(!ObjectUtils.isEmpty(emailTemplateDto)){
                    UserDto userDto = restTemplateClient.getUser(jobAdDto.getHrContactId());
                    boolean canSend = !ObjectUtils.isEmpty(userDto)
                            && !ObjectUtils.isEmpty(candidateInfoApplyDto)
                            && !ObjectUtils.isEmpty(userDto.getEmail())
                            && !ObjectUtils.isEmpty(candidateInfoApplyDto.getEmail());
                    if(canSend){
                        String emailHr = userDto.getEmail();
                        String emailCandidate = candidateInfoApplyDto.getEmail();
                        String subject = emailTemplateDto.getSubject();

                        // replace placeholders
                        String template = emailTemplateDto.getBody();
                        List<String> placeholders = emailTemplateDto.getPlaceholderCodes();
                        DataReplacePlaceholder dataReplacePlaceholder = DataReplacePlaceholder.builder()
                                .positionId(jobAdDto.getPositionId())
                                .jobAdName(jobAdDto.getTitle())
                                .jobAdProcessName(ProcessTypeEnum.APPLY.name())
                                .orgId(jobAdDto.getOrgId())
                                .candidateName(candidateInfoApplyDto.getFullName())
                                .hrName(userDto.getFullName())
                                .hrEmail(emailHr)
                                .hrPhone(userDto.getPhoneNumber())
                                .build();
                        String body = replacePlaceholder.replacePlaceholder(template, placeholders, dataReplacePlaceholder);
                        SendEmailDto sendEmailDto = SendEmailDto.builder()
                                .sender(emailHr)
                                .recipients(List.of(emailCandidate))
                                .subject(subject)
                                .body(body)
                                .candidateInfoId(candidateInfoApplyId)
                                .jobAdId(jobAdCandidate.getJobAdId())
                                .orgId(jobAdDto.getOrgId())
                                .emailTemplateId(jobAdDto.getEmailTemplateId())
                                .build();
                        sendEmailService.sendEmailWithBody(sendEmailDto);
                    }
                }
            }
        } catch (Exception ignored) {
            // Do not block candidate apply flow when optional email delivery fails.
        }

        // send notification to hr
        try {
            if(!ObjectUtils.isEmpty(jobAdDto.getHrContactId())) {
                NotifyTemplate template = NotifyTemplate.CANDIDATE_APPLY_JOB_AD;
                NotificationDto notification = NotificationDto.builder()
                        .title(String.format(template.getTitle()))
                        .message(String.format(template.getMessage(), candidateInfoApplyDto.getFullName(), jobAdDto.getTitle()))
                        .senderId(userId)
                        .receiverIds(List.of(jobAdDto.getHrContactId()))
                        .receiverType(MemberType.ORGANIZATION.getName())
                        .type(Constants.NotificationType.USER)
                        .redirectUrl(Constants.Path.JOB_AD_CANDIDATE_DETAIL + "/" + candidateInfoApplyId)
                        .build();
                kafkaUtils.sendWithJson(Constants.KafkaTopic.NOTIFICATION, notification);
            }
        } catch (Exception ignored) {
            // Do not block candidate apply flow when notification delivery fails.
        }

        return IDResponse.<Long>builder()
                .id(jobAdCandidate.getId())
                .build();
    }

    @Override
    public FilterResponse<CandidateFilterResponse> filter(CandidateFilterRequest request) {
        Long orgId = restTemplateClient.validOrgMember();
        Long participantId = null;
        List<String> role = WebUtils.getCurrentRole();
        if(!role.contains(Constants.RoleCode.ORG_ADMIN)){
            participantId = WebUtils.getCurrentUserId();
            request.setHrContactId(null);
        }

        if(Objects.equals(request.getSortBy(), CommonConstants.DEFAULT_SORT_BY)){
            request.setSortBy("applyDate");
        }
        Page<CandidateInfoApplyProjection> page = jobAdCandidateRepository.filter(request, orgId, participantId, request.getPageable());
        List<CandidateFilterResponse> data = page.getContent().stream()
                .map(projection -> CandidateFilterResponse.builder()
                        .candidateInfo(
                                CandidateInfoApplyDto.builder()
                                        .id(projection.getId())
                                        .fullName(projection.getFullName())
                                        .email(projection.getEmail())
                                        .phone(projection.getPhone())
                                        .candidateSummaryOrg(CandidateSummaryOrgDto.builder()
                                                .level(LevelDto.builder()
                                                        .id(projection.getLevelId())
                                                        .levelName(projection.getLevelName())
                                                        .build())
                                                .build())
                                        .build()
                        )
                        .numOfApply(projection.getNumOfApply())
                        .build())
                .toList();

        Map<Long, CandidateFilterResponse> candidateInfoMap = data.stream()
                .collect(Collectors.toMap(
                        item -> item.getCandidateInfo().getId(),
                        Function.identity(),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
        List<Long> candidateInfoIds = new ArrayList<>(candidateInfoMap.keySet());
        List<CandidateFilterProjection> jobAdCandidates = jobAdCandidateRepository.findAllByCandidateInfoIds(request, candidateInfoIds, orgId, participantId);

        // get Hr contacts
        List<Long> hrIds = jobAdCandidates.stream()
                .map(CandidateFilterProjection::getHrContactId)
                .distinct()
                .toList();
        Map<Long, UserDto> hrContacts = restTemplateClient.getUsersByIds(hrIds);

        for(CandidateFilterProjection projection : jobAdCandidates){
            CandidateFilterResponse response = candidateInfoMap.get(projection.getCandidateInfoId());
            if(response != null){
                JobAdCandidateDto jobAdCandidateDto = JobAdCandidateDto.builder()
                        .candidateStatus(projection.getCandidateStatus())
                        .applyDate(projection.getApplyDate())
                        .jobAd(JobAdDto.builder()
                                .id(projection.getJobAdId())
                                .title(projection.getJobAdTitle())
                                .hrContactName(hrContacts.get(projection.getHrContactId()) != null ?
                                        hrContacts.get(projection.getHrContactId()).getFullName() : null)
                                .build())
                        .currentRound(ProcessTypeDto.builder()
                                .id(projection.getProcessTypeId())
                                .code(projection.getProcessTypeCode())
                                .name(projection.getProcessTypeName())
                                .build())
                        .build();
                if(response.getJobAdCandidates() == null){
                    response.setJobAdCandidates(new ArrayList<>());
                }
                response.getJobAdCandidates().add(jobAdCandidateDto);
            }
        }

        List<CandidateFilterResponse> result = new ArrayList<>(candidateInfoMap.values());
        return PageUtils.toFilterResponse(page, result);
    }

    @Override
    @Transactional
    public CandidateInfoDetail candidateDetail(Long candidateInfoId) {
        Long orgId = restTemplateClient.validOrgMember();
        Long participantId = null;
        List<String> role = WebUtils.getCurrentRole();
        if(!role.contains(Constants.RoleCode.ORG_ADMIN)){
            participantId = WebUtils.getCurrentUserId();
        }
        CandidateInfoApplyProjection projection = jobAdCandidateRepository.getCandidateInfoDetailProjection(candidateInfoId, orgId, participantId);
        if(ObjectUtils.isEmpty(projection)){
            throw new AppException(CoreErrorCode.CANDIDATE_INFO_APPLY_NOT_FOUND);
        }
        UserDto candidate = restTemplateClient.getUser(projection.getCandidateId());

        String secureUrl = null;
        if(candidate.getAvatarId() != null) {
            List<AttachFileDto> files = attachFileService.getAttachFiles(List.of(candidate.getAvatarId()));
            if (!ObjectUtils.isEmpty(files)) {
                secureUrl = files.get(0).getSecureUrl();
            }
        }

        String resolvedCvUrl = projection.getCvFileUrl();
        AttachFileDto resolvedCvFile = null;
        if (projection.getCvFileId() != null) {
            try {
                List<AttachFileDto> cvFiles = attachFileService.getAttachFiles(List.of(projection.getCvFileId()));
                if (!ObjectUtils.isEmpty(cvFiles) && cvFiles.get(0) != null) {
                    resolvedCvFile = cvFiles.get(0);
                    String signedUrl = cloudinaryService.generateSignedUrl(cvFiles.get(0));
                    if (!ObjectUtils.isEmpty(signedUrl)) {
                        resolvedCvUrl = signedUrl;
                    } else if (!ObjectUtils.isEmpty(cvFiles.get(0).getSecureUrl())) {
                        resolvedCvUrl = cvFiles.get(0).getSecureUrl();
                    }
                }
            } catch (Exception ignored) {
                // Keep projection URL as fallback to avoid breaking detail response.
            }
        }

        CandidateInfoApplyDto candidateInfoApply = CandidateInfoApplyDto.builder()
                                    .id(projection.getId())
                                    .fullName(projection.getFullName())
                                    .email(projection.getEmail())
                                    .phone(projection.getPhone())
                                    .candidateId(projection.getCandidateId())
                                    .cvUrl(resolvedCvUrl)
                                        .attachFile(resolvedCvFile != null
                                            ? AttachFileDto.builder()
                                            .id(resolvedCvFile.getId())
                                            .originalFilename(resolvedCvFile.getOriginalFilename())
                                            .baseFilename(resolvedCvFile.getBaseFilename())
                                            .extension(resolvedCvFile.getExtension())
                                            .filename(resolvedCvFile.getFilename())
                                            .format(resolvedCvFile.getFormat())
                                            .resourceType(resolvedCvFile.getResourceType())
                                            .secureUrl(resolvedCvUrl)
                                            .type(resolvedCvFile.getType())
                                            .url(resolvedCvFile.getUrl())
                                            .publicId(resolvedCvFile.getPublicId())
                                            .folder(resolvedCvFile.getFolder())
                                            .build()
                                            : AttachFileDto.builder()
                                            .id(projection.getCvFileId())
                                            .secureUrl(resolvedCvUrl)
                                            .build())
                                    .coverLetter(projection.getCoverLetter())
                                    .candidateSummaryOrg(CandidateSummaryOrgDto.builder()
                                            .level(LevelDto.builder()
                                                    .id(projection.getLevelId())
                                                    .levelName(projection.getLevelName())
                                                    .build())
                                            .skill(projection.getSkill())
                                            .build())
                                    .avatarUrl(secureUrl)
                                    .build();

        List<JobAdCandidateProjection> jobAdCandidates = jobAdCandidateRepository.getJobAdCandidatesByCandidateInfoId(candidateInfoId, orgId, participantId);

        // get Hr contacts
        List<Long> hrIds = jobAdCandidates.stream()
                .map(JobAdCandidateProjection::getHrContactId)
                .distinct()
                .toList();
        Map<Long, UserDto> hrContacts = restTemplateClient.getUsersByIds(hrIds);

        Map<Long, List<JobAdCandidateProjection>> groupedByCandidate = jobAdCandidates.stream()
                .collect(Collectors.groupingBy(JobAdCandidateProjection::getJobAdCandidateId, LinkedHashMap::new, Collectors.toList()));

        List<JobAdCandidateDto> jobAdCandidateDtos = groupedByCandidate.values().stream()
                .map(projections -> {
                    JobAdCandidateProjection first = projections.get(0);

                    JobAdCandidateDto dto = JobAdCandidateDto.builder()
                            .id(first.getJobAdCandidateId())
                            .candidateStatus(first.getCandidateStatus())
                            .applyDate(first.getApplyDate())
                            .onboardDate(first.getOnboardDate())
                            .eliminateReason(EliminateReasonEnum.getEliminateReasonEnumDto(first.getEliminateReasonType()))
                            .eliminateReasonDetail(first.getEliminateReasonDetail())
                            .eliminateDate(first.getEliminateDate())
                            .jobAd(JobAdDto.builder()
                                    .id(first.getJobAdId())
                                    .title(first.getJobAdTitle())
                                    .hrContactId(first.getHrContactId())
                                    .hrContactName(hrContacts.get(first.getHrContactId()) != null ?
                                            hrContacts.get(first.getHrContactId()).getFullName() : null)
                                    .positionId(first.getPositionId())
                                    .positionName(first.getPositionName())
                                    .departmentId(first.getDepartmentId())
                                    .departmentName(first.getDepartmentName())
                                    .departmentCode(first.getDepartmentCode())
                                    .keyCodeInternal(first.getKeyCodeInternal())
                                    .build())
                            .build();

                    List<JobAdProcessCandidateDto> jobAdProcessCandidateDtos = projections.stream()
                            .map(p -> JobAdProcessCandidateDto.builder()
                                    .id(p.getJobAdProcessCandidateId())
                                    .jobAdProcessId(p.getJobAdProcessId())
                                    .processName(p.getProcessName())
                                    .isCurrentProcess(p.getIsCurrentProcess())
                                    .actionDate(p.getActionDate())
                                    .build())
                            .collect(Collectors.toList());

                    dto.setJobAdProcessCandidates(jobAdProcessCandidateDtos);
                    return dto;
                })
                .toList();

        // update candidate status to VIEWED_CV if current user is hr contact and status is APPLIED
        Long currentUserId = WebUtils.getCurrentUserId();
        if(hrIds.contains(currentUserId)){
            for(JobAdCandidateDto jobAdCandidateDto : jobAdCandidateDtos){
                if(jobAdCandidateDto.getJobAd().getHrContactId().equals(currentUserId) &&
                        CandidateStatus.APPLIED.name().equals(jobAdCandidateDto.getCandidateStatus())){
                    jobAdCandidateRepository.updateCandidateStatus(jobAdCandidateDto.getId(), CandidateStatus.VIEWED_CV.name());

                    // send notify to candidate
                    NotifyTemplate template = NotifyTemplate.HR_VIEWED_CANDIDATE_PROFILE;
                    NotificationDto notificationDto = NotificationDto.builder()
                            .title(String.format(template.getTitle(), jobAdCandidateDto.getJobAd().getTitle()))
                            .message(String.format(template.getMessage()))
                            .type(Constants.NotificationType.USER)
                            .redirectUrl(Constants.Path.CANDIDATE_MESSAGE_CHAT + "?id=" + jobAdCandidateDto.getId())
                            .senderId(jobAdCandidateDto.getJobAd().getHrContactId())
                            .receiverIds(List.of(projection.getCandidateId()))
                            .receiverType(MemberType.CANDIDATE.getName())
                            .build();
                    kafkaUtils.sendWithJson(Constants.KafkaTopic.NOTIFICATION, notificationDto);
                }
            }
        }

        return CandidateInfoDetail.builder()
                .candidateInfo(candidateInfoApply)
                .jobAdCandidates(jobAdCandidateDtos)
                .build();
    }

    @Override
    public boolean checkCandidateInfoInOrg(Long candidateInfoId, Long orgId, Long hrContactId) {
        return jobAdCandidateRepository.existsByCandidateInfoIdAndOrgIdAndHrContactId(candidateInfoId, orgId, hrContactId);
    }

    @Override
    @Transactional
    public void changeCandidateProcess(ChangeCandidateProcessRequest request) {
        Long toJobAdProcessCandidateId = request.getToJobAdProcessCandidateId();
        JobAdProcessCandidateDto toProcessCandidate = jobAdProcessCandidateService.findById(toJobAdProcessCandidateId);
        if(ObjectUtils.isEmpty(toProcessCandidate)){
            throw new AppException(CoreErrorCode.PROCESS_TYPE_NOT_FOUND);
        }

        // check authorization
        Long orgId = restTemplateClient.validOrgMember();
        Long hrContactId = WebUtils.getCurrentUserId();
        this.checkAuthorizedChangeProcess(toProcessCandidate.getJobAdCandidateId(), orgId, hrContactId);

        // check candidate reject
        Long jobAdCandidateId = toProcessCandidate.getJobAdCandidateId();
        JobAdCandidate jobAdCandidate = jobAdCandidateRepository.findById(jobAdCandidateId)
                .orElseThrow(() -> new AppException(CommonErrorCode.ERROR));
        if(CandidateStatus.REJECTED.name().equals(jobAdCandidate.getCandidateStatus())){
            throw new AppException(CoreErrorCode.CANDIDATE_ALREADY_ELIMINATED);
        }

        // check candidate already onboarded
        Boolean checkOnboarded = jobAdCandidateRepository.existsByCandidateInfoAndOrg(jobAdCandidate.getCandidateInfoId(), orgId, CandidateStatus.ONBOARDED.name());
        if(checkOnboarded){
            throw new AppException(CoreErrorCode.CANDIDATE_ALREADY_ONBOARDED);
        }

        // check valid process order change
        Boolean checkProcessOrder = jobAdProcessCandidateService.validateProcessOrderChange(toJobAdProcessCandidateId, jobAdCandidateId);
        if(!checkProcessOrder){
            throw new AppException(CoreErrorCode.INVALID_PROCESS_TYPE_CHANGE);
        }

        JobAdProcessCandidateDto currentProcess = jobAdProcessCandidateService.getCurrentProcess(jobAdCandidate.getJobAdId(), jobAdCandidate.getCandidateInfoId());
        if(ObjectUtils.isEmpty(currentProcess)){
            throw new AppException(CommonErrorCode.ERROR);
        }

        // update job ad candidate status
        JobAdProcessDto jobAdProcessDto = jobAdProcessService.getById(toProcessCandidate.getJobAdProcessId());
        if(ObjectUtils.isEmpty(jobAdProcessDto)){
            throw new AppException(CoreErrorCode.PROCESS_TYPE_NOT_FOUND);
        }
        ProcessTypeDto processTypeDto = jobAdProcessDto.getProcessType();
        if (!ProcessTypeEnum.APPLY.name().equals(processTypeDto.getCode()) &&
                !ProcessTypeEnum.ONBOARD.name().equals(processTypeDto.getCode())) {
            jobAdCandidate.setCandidateStatus(CandidateStatus.IN_PROGRESS.name());
        } else if (ProcessTypeEnum.ONBOARD.name().equals(processTypeDto.getCode())) {
            if(request.getOnboardDate() == null){
                throw new AppException(CoreErrorCode.ONBOARD_DATE_REQUIRED);
            }
            if(request.getOnboardDate().isBefore(ZonedDateTime.now(CommonConstants.ZONE.UTC).toInstant())){
                throw new AppException(CoreErrorCode.ONBOARD_DATE_INVALID);
            }
            jobAdCandidate.setCandidateStatus(CandidateStatus.WAITING_ONBOARDING.name());
            jobAdCandidate.setOnboardDate(request.getOnboardDate());
        }
        jobAdCandidateRepository.save(jobAdCandidate);

        // update process candidates
        List<JobAdProcessCandidateDto> dtos = jobAdProcessCandidateService.findByJobAdCandidateId(jobAdCandidateId);
        Instant now = ZonedDateTime.now(CommonConstants.ZONE.UTC).toInstant();
        for (JobAdProcessCandidateDto dto : dtos) {
            boolean isCurrent = dto.getId().equals(toJobAdProcessCandidateId);
            dto.setIsCurrentProcess(isCurrent);
            if (isCurrent) {
                dto.setActionDate(now);
            }
        }
        jobAdProcessCandidateService.create(dtos);

        // send email to candidate
        if(request.isSendEmail()){
            String subject;
            String template;
            List<String> placeholders;

            // get email template
            Long emailTemplateId = request.getEmailTemplateId();
            if(emailTemplateId != null){
                EmailTemplateDto emailTemplateDto = restTemplateClient.getEmailTemplateById(emailTemplateId);
                if(ObjectUtils.isEmpty(emailTemplateDto)){
                    throw new AppException(CoreErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
                }
                subject = emailTemplateDto.getSubject();
                template = emailTemplateDto.getBody();
                placeholders = emailTemplateDto.getPlaceholderCodes();
            } else {
                CoreServiceUtils.validateManualEmail(request.getSubject(), request.getTemplate());
                subject = request.getSubject();
                template = request.getTemplate();
                placeholders = request.getPlaceholders();
            }

            // get data to replace placeholder
            CandidateInfoApplyDto candidateInfo = candidateInfoApplyService.getById(jobAdCandidate.getCandidateInfoId());
            UserDto hrContact = restTemplateClient.getUser(hrContactId);
            JobAdDto jobAd = jobAdService.findById(jobAdCandidate.getJobAdId());
            if(ObjectUtils.isEmpty(hrContact) || ObjectUtils.isEmpty(candidateInfo) || ObjectUtils.isEmpty(jobAd)){
                throw new AppException(CommonErrorCode.ERROR);
            }

            // replace placeholders
            DataReplacePlaceholder dataReplacePlaceholder = DataReplacePlaceholder.builder()
                    .positionId(jobAd.getPositionId())
                    .jobAdName(jobAd.getTitle())
                    .jobAdProcessName(currentProcess.getProcessName())
                    .nextJobAdProcessName(jobAdProcessDto.getName())
                    .orgId(jobAd.getOrgId())
                    .candidateName(candidateInfo.getFullName())
                    .hrName(hrContact.getFullName())
                    .hrEmail(hrContact.getEmail())
                    .hrPhone(hrContact.getPhoneNumber())
                    .build();
            if(request.getOnboardDate() != null){
                dataReplacePlaceholder.setExamStartTime(request.getOnboardDate());
            }
            String body = replacePlaceholder.replacePlaceholder(template, placeholders, dataReplacePlaceholder);

            // send email
            SendEmailDto sendEmailDto = SendEmailDto.builder()
                    .sender(hrContact.getEmail())
                    .recipients(List.of(candidateInfo.getEmail()))
                    .subject(subject)
                    .body(body)
                    .candidateInfoId(candidateInfo.getId())
                    .jobAdId(jobAd.getId())
                    .orgId(jobAd.getOrgId())
                    .emailTemplateId(emailTemplateId)
                    .build();
            sendEmailService.sendEmailWithBody(sendEmailDto);
        }

        // send notify to candidate
        JobAdDto jd = jobAdService.findById(jobAdCandidate.getJobAdId());
        CandidateInfoApplyDto candidateInfoApplyDto = candidateInfoApplyService.getById(jobAdCandidate.getCandidateInfoId());
        NotifyTemplate notifyTemplate = NotifyTemplate.CHANGE_CANDIDATE_PROCESS;
        NotificationDto notificationDto = NotificationDto.builder()
                .title(String.format(notifyTemplate.getTitle(), jd.getTitle()))
                .message(String.format(notifyTemplate.getMessage(), jobAdProcessDto.getName()))
                .type(Constants.NotificationType.USER)
                .redirectUrl(Constants.Path.CANDIDATE_MESSAGE_CHAT + "?id=" + jobAdCandidate.getId())
                .senderId(hrContactId)
                .receiverIds(List.of(candidateInfoApplyDto.getCandidateId()))
                .receiverType(MemberType.CANDIDATE.getName())
                .build();
        kafkaUtils.sendWithJson(Constants.KafkaTopic.NOTIFICATION, notificationDto);

        // if onboard process, send notify to org admin
        if(ProcessTypeEnum.ONBOARD.name().equals(processTypeDto.getCode())){
            UserDto hrContact = restTemplateClient.getUser(hrContactId);
            List<UserDto> orgAdmin =  restTemplateClient.getUserByRoleCodeOrg(Constants.RoleCode.ORG_ADMIN, orgId);
            NotifyTemplate templateOnboard = NotifyTemplate.CHANGE_PROCESS_ONBOARD;
            NotificationDto notifyDto = NotificationDto.builder()
                    .title(String.format(templateOnboard.getTitle(), jd.getTitle()))
                    .message(String.format(templateOnboard.getMessage(), hrContact.getFullName(), candidateInfoApplyDto.getFullName()))
                    .type(Constants.NotificationType.USER)
                    .redirectUrl(Constants.Path.CANDIDATE_DETAIL + candidateInfoApplyDto.getId())
                    .senderId(hrContactId)
                    .receiverIds(orgAdmin.stream().map(UserDto::getId).toList())
                    .receiverType(MemberType.ORGANIZATION.getName())
                    .build();
            kafkaUtils.sendWithJson(Constants.KafkaTopic.NOTIFICATION, notifyDto);
        }
    }

    @Override
    @Transactional
    public void eliminateCandidate(EliminateCandidateRequest request) {
        Long orgId = restTemplateClient.validOrgMember();
        Long hrContactId = WebUtils.getCurrentUserId();
        this.checkAuthorizedChangeProcess(request.getJobAdCandidateId(), orgId, hrContactId);

        JobAdCandidate jobAdCandidate = jobAdCandidateRepository.findById(request.getJobAdCandidateId()).orElseThrow(
                () -> new AppException(CommonErrorCode.DATA_NOT_FOUND)
        );
        if(CandidateStatus.REJECTED.name().equals(jobAdCandidate.getCandidateStatus())){
            throw new AppException(CoreErrorCode.CANDIDATE_ALREADY_ELIMINATED);
        }
        jobAdCandidate.setCandidateStatus(CandidateStatus.REJECTED.name());
        jobAdCandidate.setEliminateReasonType(request.getReason().name());
        jobAdCandidate.setEliminateReasonDetail(request.getReasonDetail());
        jobAdCandidate.setEliminateDate(ZonedDateTime.now(CommonConstants.ZONE.UTC).toInstant());
        jobAdCandidateRepository.save(jobAdCandidate);

        // send notify to candidate
        JobAdDto jd = jobAdService.findById(jobAdCandidate.getJobAdId());
        CandidateInfoApplyDto candidateInfoApplyDto = candidateInfoApplyService.getById(jobAdCandidate.getCandidateInfoId());
        NotifyTemplate notifyTemplate = NotifyTemplate.ELIMINATE_CANDIDATE;
        NotificationDto notificationDto = NotificationDto.builder()
                .title(String.format(notifyTemplate.getTitle(), jd.getTitle()))
                .message(String.format(notifyTemplate.getMessage()))
                .type(Constants.NotificationType.USER)
                .redirectUrl(Constants.Path.CANDIDATE_MESSAGE_CHAT + "?id=" + jobAdCandidate.getId())
                .senderId(hrContactId)
                .receiverIds(List.of(candidateInfoApplyDto.getCandidateId()))
                .receiverType(MemberType.CANDIDATE.getName())
                .build();
        kafkaUtils.sendWithJson(Constants.KafkaTopic.NOTIFICATION, notificationDto);

        // send notify to org admin
        UserDto hrContact = restTemplateClient.getUser(hrContactId);
        List<UserDto> orgAdmin = restTemplateClient.getUserByRoleCodeOrg(Constants.RoleCode.ORG_ADMIN, orgId);
        NotifyTemplate templateOnboard = NotifyTemplate.ELIMINATE_CANDIDATE_NOTIFY_ORG_ADMIN;
        NotificationDto notifyDto = NotificationDto.builder()
                .title(String.format(templateOnboard.getTitle(), jd.getTitle()))
                .message(String.format(templateOnboard.getMessage(), hrContact.getFullName(), candidateInfoApplyDto.getFullName()))
                .type(Constants.NotificationType.USER)
                .redirectUrl(Constants.Path.CANDIDATE_DETAIL + candidateInfoApplyDto.getId())
                .senderId(hrContactId)
                .receiverIds(orgAdmin.stream().map(UserDto::getId).toList())
                .receiverType(MemberType.ORGANIZATION.getName())
                .build();
        kafkaUtils.sendWithJson(Constants.KafkaTopic.NOTIFICATION, notifyDto);

        if(request.isSendEmail()){
            String subject;
            String template;
            List<String> placeholders;

            // get email template
            Long emailTemplateId = request.getEmailTemplateId();
            if(emailTemplateId != null){
                EmailTemplateDto emailTemplateDto = restTemplateClient.getEmailTemplateById(emailTemplateId);
                if(ObjectUtils.isEmpty(emailTemplateDto)){
                    throw new AppException(CoreErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
                }
                subject = emailTemplateDto.getSubject();
                template = emailTemplateDto.getBody();
                placeholders = emailTemplateDto.getPlaceholderCodes();
            } else {
                CoreServiceUtils.validateManualEmail(request.getSubject(), request.getTemplate());
                subject = request.getSubject();
                template = request.getTemplate();
                placeholders = request.getPlaceholders();
            }

            // get data to replace placeholder
            CandidateInfoApplyDto candidateInfo = candidateInfoApplyService.getById(jobAdCandidate.getCandidateInfoId());
            JobAdDto jobAd = jobAdService.findById(jobAdCandidate.getJobAdId());
            if(ObjectUtils.isEmpty(hrContact) || ObjectUtils.isEmpty(candidateInfo) || ObjectUtils.isEmpty(jobAd)){
                throw new AppException(CommonErrorCode.ERROR);
            }
            JobAdProcessCandidateDto currentProcess = jobAdProcessCandidateService.getCurrentProcess(jobAdCandidate.getJobAdId(), jobAdCandidate.getCandidateInfoId());
            if(ObjectUtils.isEmpty(currentProcess)){
                throw new AppException(CommonErrorCode.ERROR);
            }

            // replace placeholders
            DataReplacePlaceholder dataReplacePlaceholder = DataReplacePlaceholder.builder()
                    .positionId(jobAd.getPositionId())
                    .jobAdName(jobAd.getTitle())
                    .jobAdProcessName(currentProcess.getProcessName())
                    .orgId(jobAd.getOrgId())
                    .candidateName(candidateInfo.getFullName())
                    .hrName(hrContact.getFullName())
                    .hrEmail(hrContact.getEmail())
                    .hrPhone(hrContact.getPhoneNumber())
                    .build();
            String body = replacePlaceholder.replacePlaceholder(template, placeholders, dataReplacePlaceholder);

            // send email
            SendEmailDto sendEmailDto = SendEmailDto.builder()
                    .sender(hrContact.getEmail())
                    .recipients(List.of(candidateInfo.getEmail()))
                    .subject(subject)
                    .body(body)
                    .candidateInfoId(candidateInfo.getId())
                    .jobAdId(jobAd.getId())
                    .orgId(jobAd.getOrgId())
                    .emailTemplateId(emailTemplateId)
                    .build();
            sendEmailService.sendEmailWithBody(sendEmailDto);
        }
    }

    @Override
    @Transactional
    public void changeOnboardDate(ChangeOnboardDateRequest request) {
        if(request.getNewOnboardDate().isBefore(ZonedDateTime.now(CommonConstants.ZONE.UTC).toInstant())){
            throw new AppException(CoreErrorCode.ONBOARD_DATE_INVALID);
        }

        // check authorization
        Long orgId = restTemplateClient.validOrgMember();
        Long hrContactId = WebUtils.getCurrentUserId();
        this.checkAuthorizedChangeProcess(request.getJobAdCandidateId(), orgId, hrContactId);

        // check candidate reject
        JobAdCandidate jobAdCandidate = jobAdCandidateRepository.findById(request.getJobAdCandidateId())
                .orElseThrow(() -> new AppException(CommonErrorCode.ERROR));
        if(CandidateStatus.REJECTED.name().equals(jobAdCandidate.getCandidateStatus())){
            throw new AppException(CoreErrorCode.CANDIDATE_ALREADY_ELIMINATED);
        }

        // check candidate in onboard process
        Boolean isMatchCurrentProcess = jobAdProcessCandidateService.validateCurrentProcessTypeIs(jobAdCandidate.getId(), ProcessTypeEnum.ONBOARD.name());
        if(!isMatchCurrentProcess){
            throw new AppException(CoreErrorCode.CANDIDATE_NOT_IN_ONBOARD_PROCESS);
        }

        // check candidate already onboarded
        Boolean checkOnboarded = jobAdCandidateRepository.existsByCandidateInfoAndOrg(jobAdCandidate.getCandidateInfoId(), orgId, CandidateStatus.ONBOARDED.name());
        if(checkOnboarded){
            throw new AppException(CoreErrorCode.CANDIDATE_ALREADY_ONBOARDED);
        }

        jobAdCandidate.setOnboardDate(request.getNewOnboardDate());
        jobAdCandidateRepository.save(jobAdCandidate);

        // send notify to candidate
        JobAdDto jd = jobAdService.findById(jobAdCandidate.getJobAdId());
        CandidateInfoApplyDto candidateInfoApplyDto = candidateInfoApplyService.getById(jobAdCandidate.getCandidateInfoId());
        String formattedOnboardDate = DateUtils.instantToString_HCM(jobAdCandidate.getOnboardDate(), CommonConstants.DATE_TIME.DD_MM_YYYY_HYPHEN);
        NotifyTemplate notifyTemplate = NotifyTemplate.CHANGE_ONBOARD_DATE;
        NotificationDto notificationDto = NotificationDto.builder()
                .title(String.format(notifyTemplate.getTitle(), jd.getTitle()))
                .message(String.format(notifyTemplate.getMessage(), formattedOnboardDate))
                .type(Constants.NotificationType.USER)
                .redirectUrl(Constants.Path.CANDIDATE_MESSAGE_CHAT + "?id=" + jobAdCandidate.getId())
                .senderId(hrContactId)
                .receiverIds(List.of(candidateInfoApplyDto.getCandidateId()))
                .receiverType(MemberType.CANDIDATE.getName())
                .build();
        kafkaUtils.sendWithJson(Constants.KafkaTopic.NOTIFICATION, notificationDto);
    }

    @Override
    public void markOnboard(MarkOnboardRequest request) {
        Long orgId = restTemplateClient.validOrgMember();
        Long hrContactId = WebUtils.getCurrentUserId();

        // check authorization
        this.checkAuthorizedChangeProcess(request.getJobAdCandidateId(), orgId, hrContactId);

        // check candidate reject
        JobAdCandidate jobAdCandidate = jobAdCandidateRepository.findById(request.getJobAdCandidateId())
                .orElseThrow(() -> new AppException(CommonErrorCode.ERROR));
        if(CandidateStatus.REJECTED.name().equals(jobAdCandidate.getCandidateStatus())){
            throw new AppException(CoreErrorCode.CANDIDATE_ALREADY_ELIMINATED);
        }

        // check candidate in onboard process
        Boolean isMatchCurrentProcess = jobAdProcessCandidateService.validateCurrentProcessTypeIs(jobAdCandidate.getId(), ProcessTypeEnum.ONBOARD.name());
        if(!isMatchCurrentProcess){
            throw new AppException(CoreErrorCode.CANDIDATE_NOT_IN_ONBOARD_PROCESS);
        }

        // check candidate already onboarded
        Boolean checkOnboarded = jobAdCandidateRepository.existsByCandidateInfoAndOrgAndNotJobAdCandidate(jobAdCandidate.getCandidateInfoId(), orgId, jobAdCandidate.getId(), CandidateStatus.ONBOARDED.name());
        if(checkOnboarded){
            throw new AppException(CoreErrorCode.CANDIDATE_ALREADY_ONBOARDED);
        }

        jobAdCandidate.setCandidateStatus(request.getIsOnboarded() ?
                CandidateStatus.ONBOARDED.name() : CandidateStatus.NOT_ONBOARDED.name());
        jobAdCandidateRepository.save(jobAdCandidate);
    }

    @Override
    public Boolean existsByJobAdCandidateIdAndHrContactId(Long jobAdCandidateId, Long hrContactId) {
        return jobAdCandidateRepository.existsByJobAdCandidateIdAndHrContactId(jobAdCandidateId, hrContactId);
    }

    @Override
    public Boolean existsByJobAdCandidateIdAndOrgId(Long jobAdCandidateId, Long orgId) {
        return jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(jobAdCandidateId, orgId);
    }

    @Override
    public JobAdCandidateDto findById(Long jobAdCandidateId) {
        JobAdCandidate jobAdCandidate = jobAdCandidateRepository.findById(jobAdCandidateId).orElse(null);
        if(ObjectUtils.isEmpty(jobAdCandidate)){
            return null;
        }
        return ObjectMapperUtils.convertToObject(jobAdCandidate, JobAdCandidateDto.class);
    }

    @Override
    public void sendEmailToCandidate(SendEmailToCandidateRequest request) {
        // validate
        Long orgId = restTemplateClient.validOrgMember();
        JobAdDto jobAd = jobAdService.findById(request.getJobAdId());
        if(ObjectUtils.isEmpty(jobAd)){
            throw new AppException(CoreErrorCode.JOB_AD_NOT_FOUND);
        }
        if(!jobAd.getOrgId().equals(orgId)){
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }
        boolean existsCandidateInfo = jobAdCandidateRepository.existsByJobAdIdAndCandidateInfoId(request.getJobAdId(), request.getCandidateInfoId());
        if(!existsCandidateInfo){
            throw new AppException(CoreErrorCode.CANDIDATE_INFO_APPLY_NOT_FOUND);
        }

        String subject;
        String template;
        List<String> placeholders;

        // get email template
        Long emailTemplateId = request.getEmailTemplateId();
        if(emailTemplateId != null){
            EmailTemplateDto emailTemplateDto = restTemplateClient.getEmailTemplateById(emailTemplateId);
            if(ObjectUtils.isEmpty(emailTemplateDto)){
                throw new AppException(CoreErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
            }
            subject = emailTemplateDto.getSubject();
            template = emailTemplateDto.getBody();
            placeholders = emailTemplateDto.getPlaceholderCodes() != null
                    ? emailTemplateDto.getPlaceholderCodes()
                    : List.of();
        } else {
            CoreServiceUtils.validateManualEmail(request.getSubject(), request.getTemplate());
            subject = request.getSubject();
            template = request.getTemplate();
            placeholders = request.getPlaceholders() != null
                    ? request.getPlaceholders()
                    : List.of();
        }

        // get data to replace placeholder
        CandidateInfoApplyDto candidateInfo = candidateInfoApplyService.getById(request.getCandidateInfoId());
        UserDto hrContact = restTemplateClient.getUser(WebUtils.getCurrentUserId());
        JobAdProcessCandidateDto jobAdProcessCandidateDto = jobAdProcessCandidateService.getCurrentProcess(request.getJobAdId(), request.getCandidateInfoId());
        if(ObjectUtils.isEmpty(hrContact)){
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }
        if(ObjectUtils.isEmpty(candidateInfo)){
            throw new AppException(CoreErrorCode.CANDIDATE_INFO_APPLY_NOT_FOUND);
        }
        if(ObjectUtils.isEmpty(candidateInfo.getEmail())){
            throw new AppException(CoreErrorCode.EMAIL_REQUIRED);
        }
        if(ObjectUtils.isEmpty(hrContact.getEmail())){
            throw new AppException(CoreErrorCode.HR_CONTACT_NOT_FOUND);
        }

        // replace placeholders
        String processName = jobAdProcessCandidateDto != null
                ? jobAdProcessCandidateDto.getProcessName()
            : ProcessTypeEnum.APPLY.name();
        DataReplacePlaceholder dataReplacePlaceholder = DataReplacePlaceholder.builder()
                .positionId(jobAd.getPositionId())
                .jobAdName(jobAd.getTitle())
                .jobAdProcessName(processName)
                .orgId(jobAd.getOrgId())
                .candidateName(candidateInfo.getFullName())
                .hrName(hrContact.getFullName())
                .hrEmail(hrContact.getEmail())
                .hrPhone(hrContact.getPhoneNumber())
                .build();
        String body = replacePlaceholder.replacePlaceholder(template, placeholders, dataReplacePlaceholder);

        // Keep API stable if message broker/mail pipeline is temporarily unavailable.
        try {
            SendEmailDto sendEmailDto = SendEmailDto.builder()
                    .sender(hrContact.getEmail())
                    .recipients(List.of(candidateInfo.getEmail()))
                    .subject(subject)
                    .body(body)
                    .candidateInfoId(candidateInfo.getId())
                    .jobAdId(jobAd.getId())
                    .orgId(jobAd.getOrgId())
                    .emailTemplateId(emailTemplateId)
                    .build();
            sendEmailService.sendEmailWithBody(sendEmailDto);
        } catch (Exception e) {
            log.warn("Failed to publish send-email event for candidateInfoId={} jobAdId={}", request.getCandidateInfoId(), request.getJobAdId(), e);
        }
    }

    @Override
    public FilterResponse<JobAdCandidateDto> getJobAdsAppliedByCandidate(JobAdAppliedFilterRequest request) {
        Long userId = WebUtils.getCurrentUserId();
        request.setUserId(userId);
        request.setSortBy("applyDate");
        Page<JobAdAppliedProjection> page = jobAdCandidateRepository.getJobAdsAppliedByCandidate(request, request.getPageable());

        List<Long> hrIds = page.getContent().stream()
                .map(JobAdAppliedProjection::getHrContactId)
                .distinct()
                .toList();
        Map<Long, UserDto> hrContacts = restTemplateClient.getUsersByIds(hrIds);

        List<Long> orgIds = page.getContent().stream()
                .map(JobAdAppliedProjection::getOrgId)
                .distinct()
                .toList();
        Map<Long, OrgDto> orgMap = orgService.getOrgMapByIds(orgIds);

        List<ConversationDto> conversationUnread = restTemplateClient.getConversationUnread();
        Map<Long, ConversationDto> conversationMap = conversationUnread.stream()
                .collect(Collectors.toMap(
                        ConversationDto::getJobAdId,
                        Function.identity()
                ));

        List<JobAdCandidateDto> data = page.getContent().stream()
                .map(p -> {
                    UserDto hrContact = hrContacts.get(p.getHrContactId());
                    OrgDto org = orgMap.get(p.getOrgId());
                    AttachFileDto cvFile = attachFileService.getAttachFiles(List.of(p.getCvFileId())).get(0);
                    String cvUrl = cloudinaryService.generateSignedUrl(cvFile);
                    return JobAdCandidateDto.builder()
                            .id(p.getJobAdCandidateId())
                            .candidateStatusDto(CandidateStatus.getCandidateStatusDto(p.getCandidateStatus()))
                            .applyDate(p.getApplyDate())
                            .onboardDate(p.getOnboardDate())
                            .eliminateReason(EliminateReasonEnum.getEliminateReasonEnumDto(p.getEliminateReasonType()))
                            .eliminateDate(p.getEliminateDate())
                            .currentRound(ProcessTypeDto.builder()
                                    .id(p.getJobAdProcessId())
                                    .name(p.getProcessName())
                                    .transferDate(p.getTransferDate())
                                    .build())
                            .jobAd(JobAdDto.builder()
                                    .id(p.getJobAdId())
                                    .title(p.getJobAdTitle())
                                    .hrContactId(p.getHrContactId())
                                    .hrContactName(hrContact != null ? hrContact.getFullName() : null)
                                    .build())
                            .org(OrgDto.builder()
                                    .id(p.getOrgId())
                                    .name(p.getOrgName())
                                    .logoUrl(org != null ? org.getLogoUrl() : null)
                                    .build())
                            .candidateInfo(CandidateInfoApplyDto.builder()
                                    .fullName(p.getFullName())
                                    .email(p.getEmail())
                                    .phone(p.getPhone())
                                    .coverLetter(p.getCoverLetter())
                                    .cvUrl(cvUrl)
                                    .candidateId(p.getCandidateId())
                                    .build())
                            .hasMessageUnread(conversationMap.get(p.getJobAdId()) != null)
                            .build();

                }).collect(Collectors.toList());

        return PageUtils.toFilterResponse(page, data);
    }

    @Override
    public Long validateAndGetHrContactId(Long jobAdId, Long candidateId) {
        JobAdCandidate jobAdCandidate = jobAdCandidateRepository.findByJobAdIdAndCandidateId(jobAdId, candidateId);
        if(ObjectUtils.isEmpty(jobAdCandidate)) {
            throw new AppException(CoreErrorCode.CANDIDATE_INFO_APPLY_NOT_FOUND);
        }
        JobAdDto jobAd = jobAdService.findById(jobAdId);
        if(ObjectUtils.isEmpty(jobAd)){
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }
        return jobAd.getHrContactId();
    }

    @Override
    public FilterResponse<JobAdCandidateDto> jobAdCandidateConversation(JobAdAppliedFilterRequest request) {
        Long userId = WebUtils.getCurrentUserId();
        request.setSortBy("applyDate");
        request.setUserId(userId);
        request.setPageIndex(0);
        request.setPageSize(Integer.MAX_VALUE);

        Page<JobAdAppliedProjection> page = jobAdCandidateRepository.getJobAdsAppliedByCandidate(request, request.getPageable());

        List<Long> orgIds = page.getContent().stream()
                .map(JobAdAppliedProjection::getOrgId)
                .distinct()
                .toList();
        Map<Long, OrgDto> orgMap = orgService.getOrgMapByIds(orgIds);

        List<ConversationDto> conversation = restTemplateClient.getMyConversations();
        Map<Long, ConversationDto> conversationMap = conversation.stream()
                .collect(Collectors.toMap(
                        ConversationDto::getJobAdId,
                        Function.identity()
                ));

        List<Long> jobAdCandidateIds = page.getContent().stream()
                .map(JobAdAppliedProjection::getJobAdCandidateId)
                .toList();
        Map<Long, List<JobAdProcessCandidateDto>> jobAdProcessCandidateDtoMap = jobAdProcessCandidateService.getDetailByJobAdCandidateIds(jobAdCandidateIds);

        List<JobAdCandidateDto> data = page.getContent().stream()
                .map(p -> {
                    OrgDto org = orgMap.get(p.getOrgId());
                    AttachFileDto cvFile = attachFileService.getAttachFiles(List.of(p.getCvFileId())).get(0);
                    String cvUrl = cloudinaryService.generateSignedUrl(cvFile);
                    ConversationDto conversationDto = conversationMap.get(p.getJobAdId());

                    boolean hasMessageUnread = false;
                    if(!ObjectUtils.isEmpty(conversationDto)){
                        Long senderId = conversationDto.getLastMessageSenderId();
                        List<Long> seenBy = conversationDto.getLastMessageSeenBy();
                        if(!senderId.equals(userId) && !seenBy.contains(userId)){
                            hasMessageUnread = true;
                        }
                    }
                    return JobAdCandidateDto.builder()
                            .id(p.getJobAdCandidateId())
                            .candidateStatusDto(CandidateStatus.getCandidateStatusDto(p.getCandidateStatus()))
                            .applyDate(p.getApplyDate())
                            .onboardDate(p.getOnboardDate())
                            .eliminateReason(EliminateReasonEnum.getEliminateReasonEnumDto(p.getEliminateReasonType()))
                            .eliminateDate(p.getEliminateDate())
                            .currentRound(ProcessTypeDto.builder()
                                    .id(p.getJobAdProcessId())
                                    .name(p.getProcessName())
                                    .transferDate(p.getTransferDate())
                                    .build())
                            .jobAdProcessCandidates(jobAdProcessCandidateDtoMap.get(p.getJobAdCandidateId()))
                            .jobAd(JobAdDto.builder()
                                    .id(p.getJobAdId())
                                    .title(p.getJobAdTitle())
                                    .build())
                            .org(OrgDto.builder()
                                    .id(p.getOrgId())
                                    .name(p.getOrgName())
                                    .logoUrl(org != null ? org.getLogoUrl() : null)
                                    .build())
                            .candidateInfo(CandidateInfoApplyDto.builder()
                                    .fullName(p.getFullName())
                                    .email(p.getEmail())
                                    .phone(p.getPhone())
                                    .coverLetter(p.getCoverLetter())
                                    .cvUrl(cvUrl)
                                    .candidateId(p.getCandidateId())
                                    .build())
                            .hasMessageUnread(hasMessageUnread)
                            .conversation(conversationMap.get(p.getJobAdId()))
                            .build();
                })
                .sorted(
                        Comparator.comparing(
                                (JobAdCandidateDto d) -> d.getConversation() != null ? d.getConversation().getLastMessageSentAt() : null,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ).thenComparing(
                                JobAdCandidateDto::getApplyDate,
                                Comparator.reverseOrder()
                        )
                ).collect(Collectors.toList());

        return PageUtils.toFilterResponse(page, data);
    }

    @Override
    public FilterResponse<JobAdCandidateDto> jobAdCandidateConversationForOrg(MyConversationWithFilter request) {
        restTemplateClient.validOrgMember(); // validate org member
        Long userId = WebUtils.getCurrentUserId();

        FilterResponse<ConversationDto> conversationPage = restTemplateClient.getMyConversationsWithFilter(request);
        List<JobAdCandidateDto> data = new ArrayList<>();
        for(ConversationDto conversationDto : conversationPage.getData()){
            JobAdCandidateProjection projection = jobAdCandidateRepository.getJobAdCandidateByJobAdIdAndCandidateId(
                    conversationDto.getJobAdId(), conversationDto.getCandidateId()
            );
            if(ObjectUtils.isEmpty(projection)){
                continue;
            }
            boolean hasMessageUnread = false;
            Long senderId = conversationDto.getLastMessageSenderId();
            List<Long> seenBy = conversationDto.getLastMessageSeenBy();
            if(!senderId.equals(userId) && !seenBy.contains(userId)){
                hasMessageUnread = true;
            }
            JobAdCandidateDto jobAdCandidateDto = JobAdCandidateDto.builder()
                    .id(projection.getId())
                    .jobAd(JobAdDto.builder()
                            .id(projection.getJobAdId())
                            .title(projection.getJobAdTitle())
                            .build())
                    .candidateInfo(CandidateInfoApplyDto.builder()
                            .id(projection.getCandidateInfoId())
                            .fullName(projection.getFullName())
                            .build())
                    .hasMessageUnread(hasMessageUnread)
                    .conversation(conversationDto)
                    .build();
            data.add(jobAdCandidateDto);
        }

        FilterResponse<JobAdCandidateDto> response = new FilterResponse<>();
        response.setData(data);
        response.setPageInfo(conversationPage.getPageInfo());
        return response;
    }

    @Override
    public FilterResponse<JobAdCandidateDto> getListOfOnboardedCandidates(CandidateOnboardFilterRequest request) {
        Long orgId = restTemplateClient.validOrgMember();
        request.setOrgId(orgId);
        Long participantId = null;
        List<String> role = WebUtils.getCurrentRole();
        if(!role.contains(Constants.RoleCode.ORG_ADMIN)){
            participantId = WebUtils.getCurrentUserId();
        }
        if (request.getOnboardDateEnd() != null) {
            request.setOnboardDateEnd(DateUtils.endOfDay(request.getOnboardDateEnd(), CommonConstants.ZONE.UTC));
        }
        if (request.getApplyDateEnd() != null) {
            request.setApplyDateEnd(DateUtils.endOfDay(request.getApplyDateEnd(), CommonConstants.ZONE.UTC));
        }
        if(Objects.equals(request.getSortBy(), CommonConstants.DEFAULT_SORT_BY)){
            request.setSortBy("onboardDate");
        }
        Page<JobAdCandidateProjection> page = jobAdCandidateRepository.getListOfOnboardedCandidates(request, participantId, request.getPageable());

        List<Long> hrIds = page.getContent().stream()
                .map(JobAdCandidateProjection::getHrContactId)
                .distinct()
                .toList();
        Map<Long, UserDto> hrContacts = restTemplateClient.getUsersByIds(hrIds);

        List<JobAdCandidateDto> data = page.getContent().stream()
                .map(p -> JobAdCandidateDto.builder()
                        .id(p.getId())
                        .applyDate(p.getApplyDate())
                        .onboardDate(p.getOnboardDate())
                        .candidateStatusDto(CandidateStatus.getCandidateStatusDto(p.getCandidateStatus()))
                        .jobAd(JobAdDto.builder()
                                .id(p.getJobAdId())
                                .title(p.getJobAdTitle())
                                .hrContact(hrContacts.get(p.getHrContactId()))
                                .build())
                        .candidateInfo(CandidateInfoApplyDto.builder()
                                .id(p.getCandidateInfoId())
                                .fullName(p.getFullName())
                                .email(p.getEmail())
                                .phone(p.getPhone())
                                .candidateId(p.getCandidateId())
                                .build())
                        .level(LevelDto.builder()
                                .id(p.getLevelId())
                                .levelName(p.getLevelName())
                                .build())
                        .build())
                .collect(Collectors.toList());

        return PageUtils.toFilterResponse(page, data);
    }

    @Override
    public JobAdCandidateDto getJobAdCandidateData(Long jobAdId, Long candidateId) {
        JobAdCandidateProjection jobAdCandidate = jobAdCandidateRepository.getJobAdCandidateByJobAdIdAndCandidateId(jobAdId, candidateId);
        return JobAdCandidateDto.builder()
                .jobAdTitle(jobAdCandidate.getJobAdTitle())
                .fullName(jobAdCandidate.getFullName())
                .candidateInfoId(jobAdCandidate.getCandidateInfoId())
                .build();
    }

    private void validateApply(ApplyRequest request, MultipartFile cvFile) {
        Long candidateInfoApplyId = request.getCandidateInfoApplyId();
        if(ObjectUtils.isEmpty(candidateInfoApplyId)) {
            if(ObjectUtils.isEmpty(cvFile)) {
                throw new AppException(CoreErrorCode.CV_FILE_NOT_FOUND);
            }
            if(ObjectUtils.isEmpty(request.getFullName())){
                throw new AppException(CoreErrorCode.FULL_NAME_REQUIRED);
            }
            if(ObjectUtils.isEmpty(request.getEmail())){
                throw new AppException(CoreErrorCode.EMAIL_REQUIRED);
            }
            if(ObjectUtils.isEmpty(request.getPhone())){
                throw new AppException(CoreErrorCode.PHONE_REQUIRED);
            }
        } else {
            if(ObjectUtils.isEmpty(request.getCandidateId())) {
                throw new AppException(CoreErrorCode.CANDIDATE_NOT_FOUND);
            }
            CandidateInfoApplyDto candidateInfoApplyDto = candidateInfoApplyService.getById(candidateInfoApplyId);
            if(ObjectUtils.isEmpty(candidateInfoApplyDto) || !candidateInfoApplyDto.getCandidateId().equals(request.getCandidateId())) {
                throw new AppException(CoreErrorCode.CANDIDATE_INFO_APPLY_NOT_FOUND);
            }
        }

        boolean appliedJobAd = jobAdCandidateRepository.existsByJobAdIdAndCandidateId(request.getJobAdId(), request.getCandidateId());
        if(appliedJobAd){
            throw new AppException(CoreErrorCode.CANDIDATE_DUPLICATE_APPLY);
        }
    }

    private JobAdDto validateJobAd(Long jobAdId) {
        JobAdDto jobAdDto = jobAdService.findById(jobAdId);
        if(ObjectUtils.isEmpty(jobAdDto)){
            throw new AppException(CoreErrorCode.JOB_AD_NOT_FOUND);
        }
        Long userOrgId = WebUtils.getCurrentOrgId();
        if(Objects.equals(jobAdDto.getOrgId(), userOrgId)){
            throw new AppException(CoreErrorCode.CANNOT_APPLY_OWN_ORG_JOB_AD);
        }
        if(ObjectUtils.isEmpty(jobAdDto.getDueDate())
                || jobAdDto.getDueDate().isBefore(ZonedDateTime.now(CommonConstants.ZONE.UTC).toInstant())){
            throw new AppException(CoreErrorCode.JOB_AD_EXPIRED);
        }
        JobAdStatus status = ObjectUtils.isEmpty(jobAdDto.getJobAdStatus())
                ? null
                : JobAdStatus.getJobAdStatus(jobAdDto.getJobAdStatus());
        if(ObjectUtils.isEmpty(status) || !JobAdStatus.OPEN.equals(status)){
            throw new AppException(CoreErrorCode.JOB_AD_STOP_RECRUITMENT);
        }
        return jobAdDto;
    }

    private void checkAuthorizedChangeProcess(Long jobAdCandidateId, Long orgId, Long hrContactId) {
        List<String> role = WebUtils.getCurrentRole();
        boolean checkAuthorized;
        if (role.contains(Constants.RoleCode.ORG_ADMIN)) {
            checkAuthorized = jobAdCandidateRepository.existsByJobAdCandidateIdAndOrgId(jobAdCandidateId, orgId);
        } else {
            checkAuthorized = jobAdCandidateRepository.existsByJobAdCandidateIdAndHrContactId(jobAdCandidateId, hrContactId);
        }
        if (!checkAuthorized) {
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
