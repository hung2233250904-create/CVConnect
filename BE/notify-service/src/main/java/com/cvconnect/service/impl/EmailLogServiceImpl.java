package com.cvconnect.service.impl;

import com.cvconnect.common.RestTemplateClient;
import com.cvconnect.constant.Constants;
import com.cvconnect.dto.EmailLogDto;
import com.cvconnect.entity.EmailLog;
import com.cvconnect.enums.SendEmailStatus;
import com.cvconnect.repository.EmailLogRepository;
import com.cvconnect.service.EmailLogService;
import lombok.extern.slf4j.Slf4j;
import nmquan.commonlib.constant.CommonConstants;
import nmquan.commonlib.utils.ObjectMapperUtils;
import nmquan.commonlib.utils.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class EmailLogServiceImpl implements EmailLogService {
    @Autowired
    private EmailLogRepository emailLogRepository;
    @Autowired
    private RestTemplateClient restTemplateClient;

    @Override
    public Long save(EmailLogDto emailLogDto) {
        EmailLog emailLog = new EmailLog();
        emailLog.setId(emailLogDto.getId());
        emailLog.setSender(emailLogDto.getSender());
        emailLog.setEmailGroup(emailLogDto.getEmailGroup());
        emailLog.setRecipients(emailLogDto.getRecipients());
        emailLog.setCcList(emailLogDto.getCcList());
        emailLog.setSubject(emailLogDto.getSubject());
        emailLog.setBody(emailLogDto.getBody());
        emailLog.setCandidateInfoId(emailLogDto.getCandidateInfoId());
        emailLog.setJobAdId(emailLogDto.getJobAdId());
        emailLog.setOrgId(emailLogDto.getOrgId());
        emailLog.setEmailTemplateId(emailLogDto.getEmailTemplateId());
        emailLog.setTemplate(emailLogDto.getTemplate());
        emailLog.setTemplateVariables(emailLogDto.getTemplateVariables());
        emailLog.setErrorMessage(emailLogDto.getErrorMessage());
        emailLog.setStatus(emailLogDto.getStatus());
        emailLog.setSentAt(emailLogDto.getSentAt());
        emailLog.setCreatedBy(emailLogDto.getCreatedBy());
        emailLog.setUpdatedBy(emailLogDto.getUpdatedBy());
        emailLogRepository.save(emailLog);
        return emailLog.getId();
    }

    @Override
    public EmailLogDto findById(Long id) {
        EmailLog emailLog = emailLogRepository.findById(id).orElse(null);
        if (emailLog == null) {
            return null;
        }
        return this.buildEmailLogDto(emailLog);
    }

    @Override
    public List<EmailLogDto> getWaitResendEmail(Long limit) {
        if(limit == null){
            limit = 50L;
        }
        List<EmailLog> emailLogs = emailLogRepository.findByStatus(SendEmailStatus.FAILURE_WAIT_RESEND, limit);
        return emailLogs.stream()
                .map(this::buildEmailLogDto).toList();
    }

    @Override
    public List<EmailLogDto> getByCandidateInfoId(Long candidateInfoId, Long jobAdId) {
        try {
            Long orgId = restTemplateClient.validOrgMember();
            List<String> roles = WebUtils.getCurrentRole();
            if(roles == null){
                roles = Collections.emptyList();
            }
            if(!roles.contains(Constants.RoleCode.HR) && !roles.contains(Constants.RoleCode.ORG_ADMIN)){
                return List.of();
            }
            List<EmailLog> emailLogs = emailLogRepository.findByCandidateInfoIdAndJobAdIdAndOrgId(candidateInfoId, jobAdId, orgId);
            if(ObjectUtils.isEmpty(emailLogs)){
                return List.of();
            }
            return ObjectMapperUtils.convertToList(emailLogs, EmailLogDto.class);
        } catch (Exception e) {
            log.warn("Failed to load email logs for candidateInfoId={} jobAdId={}", candidateInfoId, jobAdId, e);
            return List.of();
        }
    }

    private EmailLogDto buildEmailLogDto(EmailLog emailLog) {
        EmailLogDto emailLogDto = new EmailLogDto();
        emailLogDto.setId(emailLog.getId());
        emailLogDto.setEmailGroup(emailLog.getEmailGroup());
        emailLogDto.setSender(emailLog.getSender());
        emailLogDto.setRecipients(emailLog.getRecipients());
        emailLogDto.setCcList(emailLog.getCcList());
        emailLogDto.setSubject(emailLog.getSubject());
        emailLogDto.setBody(emailLog.getBody());
        emailLogDto.setCandidateInfoId(emailLog.getCandidateInfoId());
        emailLogDto.setJobAdId(emailLog.getJobAdId());
        emailLogDto.setOrgId(emailLog.getOrgId());
        emailLogDto.setEmailTemplateId(emailLog.getEmailTemplateId());
        emailLogDto.setTemplate(emailLog.getTemplate());
        emailLogDto.setTemplateVariables(emailLog.getTemplateVariables());
        emailLogDto.setErrorMessage(emailLog.getErrorMessage());
        emailLogDto.setStatus(emailLog.getStatus());
        emailLogDto.setSentAt(emailLog.getSentAt());
        emailLogDto.setIsActive(emailLog.getIsActive());
        emailLogDto.setIsDeleted(emailLog.getIsDeleted());
        emailLogDto.setCreatedBy(emailLog.getCreatedBy());
        emailLogDto.setUpdatedBy(emailLog.getUpdatedBy());
        emailLogDto.setCreatedAt(emailLog.getCreatedAt());
        emailLogDto.setUpdatedAt(emailLog.getUpdatedAt());
        return emailLogDto;
    }
}
