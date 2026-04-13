package com.cvconnect.job;

import com.cvconnect.constant.Constants;
import com.cvconnect.dto.EmailLogDto;
import com.cvconnect.service.EmailLogService;
import com.cvconnect.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import nmquan.commonlib.dto.SendEmailDto;
import nmquan.commonlib.job.RunningJob;
import nmquan.commonlib.utils.ObjectMapperUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EmailResendJob implements RunningJob {
    @Autowired
    private EmailLogService emailLogService;
    @Autowired
    private EmailService emailService;

    private static final Long RESEND_LIMIT = 50L;

    @Override
    public String getJobName() {
        return Constants.JobName.EMAIL_RESEND;
    }

    @Override
    public String getScheduleType() {
        return "";
    }

    @Override
    public String getExpression() {
        return "";
    }

    @Override
    @SchedulerLock(name = Constants.JobName.EMAIL_RESEND, lockAtMostFor = "5m", lockAtLeastFor = "2m")
    public void runJob() {
        log.info("[START] Running job: {}", getJobName());
        List<EmailLogDto> waitResendEmails = emailLogService.getWaitResendEmail(RESEND_LIMIT);
        for(EmailLogDto emailLog : waitResendEmails) {
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
            emailService.resendEmail(sendEmailDto, emailLog.getId());
        }
        log.info("[DONE] Finished job (may be running async): {}", getJobName());
    }
}
