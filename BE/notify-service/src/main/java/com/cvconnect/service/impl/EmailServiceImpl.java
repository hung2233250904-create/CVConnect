package com.cvconnect.service.impl;

import com.cvconnect.common.RestTemplateClient;
import com.cvconnect.dto.EmailConfigDto;
import com.cvconnect.dto.EmailLogDto;
import com.cvconnect.enums.NotifyErrorCode;
import com.cvconnect.enums.SendEmailStatus;
import com.cvconnect.service.EmailConfigService;
import com.cvconnect.service.EmailLogService;
import com.cvconnect.service.EmailService;
import jakarta.mail.*;
import nmquan.commonlib.dto.SendEmailDto;
import nmquan.commonlib.exception.AppException;
import nmquan.commonlib.exception.CommonErrorCode;
import nmquan.commonlib.utils.ObjectMapperUtils;
import nmquan.commonlib.utils.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EmailServiceImpl implements EmailService {
    @Autowired
    private EmailConfigService emailConfigService;
    @Autowired
    private EmailLogService emailLogService;
    @Autowired
    private EmailAsyncServiceImpl emailAsyncServiceImpl;
    @Autowired
    private RestTemplateClient restTemplateClient;

    @Value("${mail.fallback.host:smtp.gmail.com}")
    private String fallbackHost;
    @Value("${mail.fallback.port:587}")
    private Integer fallbackPort;
    @Value("${mail.fallback.email:${server.email-admin:}}")
    private String fallbackEmail;
    @Value("${mail.fallback.password:}")
    private String fallbackPassword;
    @Value("${mail.fallback.is-ssl:false}")
    private Boolean fallbackIsSsl;
    @Value("${mail.fallback.protocol:smtp}")
    private String fallbackProtocol;

    private static final int BATCH_SIZE = 30;

    @Override
    public void sendEmail(SendEmailDto sendEmailDto) {
        Session session = null;
        String sessionError = null;
        try {
            session = this.getSession(sendEmailDto.getOrgId());
        } catch (Exception ex) {
            sessionError = ex.getMessage();
        }

        List<String> recipients = sendEmailDto.getRecipients();
        String emailGroup = UUID.randomUUID().toString().substring(0,18);
        for (int i = 0; i < recipients.size(); i += BATCH_SIZE) {
            List<String> batch = recipients.subList(i, Math.min(i + BATCH_SIZE, recipients.size()));
            SendEmailDto batchDto = SendEmailDto.builder()
                    .sender(sendEmailDto.getSender())
                    .recipients(batch)
                    .ccList(sendEmailDto.getCcList())
                    .subject(sendEmailDto.getSubject())
                    .body(sendEmailDto.getBody())
                    .candidateInfoId(sendEmailDto.getCandidateInfoId())
                    .jobAdId(sendEmailDto.getJobAdId())
                    .orgId(sendEmailDto.getOrgId())
                    .emailTemplateId(sendEmailDto.getEmailTemplateId())
                    .template(sendEmailDto.getTemplate())
                    .templateVariables(sendEmailDto.getTemplateVariables())
                    .build();
            EmailLogDto emailLogDto = this.buildEmailLogDto(batchDto, emailGroup);

            if (session == null) {
                emailLogDto.setStatus(SendEmailStatus.FAILURE);
                emailLogDto.setErrorMessage(sessionError);
                emailLogService.save(emailLogDto);
                continue;
            }

            Long emailLogId = emailLogService.save(emailLogDto);
            // Send email async
            emailAsyncServiceImpl.send(batchDto, session, emailLogId);
        }
    }

    @Override
    public void resendEmail(SendEmailDto sendEmailDto, Long emailLogId) {
        Session session = this.getSession(sendEmailDto.getOrgId());
        // Send email async
        emailAsyncServiceImpl.resend(sendEmailDto, session, emailLogId);
    }

    @Override
    @Transactional
    public void resendEmailClient(Long emailLogId) {
        EmailLogDto emailLog = emailLogService.findById(emailLogId);
        if(emailLog == null){
            throw new AppException(NotifyErrorCode.EMAIL_LOG_NOT_FOUND);
        }
        String currentEmail = WebUtils.getCurrentEmail();
        if(!emailLog.getSender().equals(currentEmail)){
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }
        emailLog.setStatus(SendEmailStatus.SENDING);
        emailLogService.save(emailLog);
        SendEmailDto sendEmailDto = SendEmailDto.builder()
                .sender(emailLog.getSender())
                .recipients(List.of(emailLog.getRecipients().split(",")))
                .ccList(emailLog.getCcList() == null ? null : List.of(emailLog.getCcList().split(",")))
                .orgId(emailLog.getOrgId())
                .subject(emailLog.getSubject())
                .body(emailLog.getBody())
                .template(emailLog.getTemplate())
                .build();
        if(emailLog.getTemplateVariables() != null){
            Map<String, String> templateVariables = ObjectMapperUtils.convertToMap(emailLog.getTemplateVariables())
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
            sendEmailDto.setTemplateVariables(templateVariables);
        }
        this.resendEmail(sendEmailDto, emailLogId);
    }

    private Session getSession(Long orgId) {
        EmailConfigDto resolvedEmailConfig = emailConfigService.getByOrgId(orgId);
        if (resolvedEmailConfig == null) {
            resolvedEmailConfig = this.buildFallbackEmailConfig();
        }
        if (resolvedEmailConfig == null) {
            throw new AppException(NotifyErrorCode.EMAIL_CONFIG_NOT_FOUND);
        }
        final EmailConfigDto emailConfig = resolvedEmailConfig;

        Properties props = new Properties();
        props.put("mail.transport.protocol", emailConfig.getProtocol());
        props.put("mail." + emailConfig.getProtocol() + ".host", emailConfig.getHost());
        props.put("mail." + emailConfig.getProtocol() + ".port", String.valueOf(emailConfig.getPort()));
        props.put("mail." + emailConfig.getProtocol() + ".auth", "true");
        if (emailConfig.getIsSsl()) {
            props.put("mail." + emailConfig.getProtocol() + ".ssl.enable", "true");
        } else {
            props.put("mail." + emailConfig.getProtocol() + ".starttls.enable", "true");
        }
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(emailConfig.getEmail(), emailConfig.getPassword());
            }
        });
    }

    private EmailConfigDto buildFallbackEmailConfig() {
        if (ObjectUtils.isEmpty(fallbackHost)
                || ObjectUtils.isEmpty(fallbackPort)
                || ObjectUtils.isEmpty(fallbackEmail)
                || ObjectUtils.isEmpty(fallbackPassword)
                || ObjectUtils.isEmpty(fallbackProtocol)) {
            return null;
        }
        return EmailConfigDto.builder()
                .host(fallbackHost)
                .port(fallbackPort)
                .email(fallbackEmail)
                .password(fallbackPassword)
                .isSsl(Boolean.TRUE.equals(fallbackIsSsl))
                .protocol(fallbackProtocol)
                .build();
    }

    private EmailLogDto buildEmailLogDto(SendEmailDto sendEmailDto, String emailGroup) {
        return EmailLogDto.builder()
                .emailGroup(emailGroup)
                .sender(sendEmailDto.getSender())
                .recipients(String.join(",", sendEmailDto.getRecipients()))
                .ccList(sendEmailDto.getCcList() == null ? null : String.join(",", sendEmailDto.getCcList()))
                .subject(sendEmailDto.getSubject())
                .body(sendEmailDto.getBody())
                .candidateInfoId(sendEmailDto.getCandidateInfoId())
                .jobAdId(sendEmailDto.getJobAdId())
                .orgId(sendEmailDto.getOrgId())
                .emailTemplateId(sendEmailDto.getEmailTemplateId())
                .template(sendEmailDto.getTemplate() == null ? null : sendEmailDto.getTemplate())
                .templateVariables(sendEmailDto.getTemplateVariables() == null ? null : ObjectMapperUtils.convertToJson(sendEmailDto.getTemplateVariables()))
                .status(SendEmailStatus.SENDING)
                .build();
    }
}
