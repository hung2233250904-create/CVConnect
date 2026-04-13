package com.cvconnect.job.failedRollback;

import com.cvconnect.constant.Constants;
import com.cvconnect.dto.failedRollback.FailedRollbackDto;
import com.cvconnect.enums.FailedRollbackType;
import com.cvconnect.service.FailedRollbackService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import nmquan.commonlib.job.RunningJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class FailedRollbackRetryJob implements RunningJob {
    @Autowired
    private FailedRollbackService failedRollbackService;
    @Autowired
    private FailedRollbackHandlerRegistry handlerRegistry;
    @Override
    public String getJobName() {
        return Constants.JobName.FAILED_ROLLBACK_RETRY;
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
    @SchedulerLock(name = Constants.JobName.FAILED_ROLLBACK_RETRY, lockAtMostFor = "5m", lockAtLeastFor = "2m")
    public void runJob() {
        log.info("[START] Running job: {}", getJobName());
        List<FailedRollbackDto> failedRollbacks = failedRollbackService.getPendingFailedRollbacks();
        for(FailedRollbackDto dto : failedRollbacks) {
            try{
                FailedRollbackType type = FailedRollbackType.valueOf(dto.getType());
                FailedRollbackHandler handler = handlerRegistry.getHandler(type);
                if (handler == null) {
                    continue;
                }
                try {
                    handler.rollback(dto);
                    dto.setStatus(true);
                    log.info("[SUCCESS] Successfully rolled back FailedRollback id: {}. Type: {}", dto.getId(), dto.getType());
                } catch (Exception ex) {
                    dto.setRetryCount(dto.getRetryCount() + 1);
                    log.error("[ERROR] Failed rollback id: {}. Type {}. Error: {}", dto.getId(), dto.getType(), ex.getMessage());
                } finally {
                    failedRollbackService.save(dto);
                }
            } catch (Exception ex){
                log.error("[ERROR] Unknown FailedRollbackType for id: {}. Type: {}", dto.getId(), dto.getType());
            }
        }
        log.info("[DONE] Finished job: {}", getJobName());
    }
}
