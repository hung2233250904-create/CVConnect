package com.cvconnect.controller;

import com.cvconnect.dto.EmailLogDto;
import com.cvconnect.service.EmailLogService;
import io.swagger.v3.oas.annotations.Operation;
import nmquan.commonlib.dto.response.Response;
import nmquan.commonlib.utils.ResponseUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/email-log")
public class EmailLogController {
    @Autowired
    private EmailLogService emailLogService;

    @GetMapping("/log-by-candidate-info/{candidateInfoId}/{jobAdId}")
    @Operation(summary = "Get email logs by candidate info ID")
    @PreAuthorize("hasAnyAuthority('ORG_CANDIDATE:VIEW', 'ORG_ADMIN', 'HR')")
    public ResponseEntity<Response<List<EmailLogDto>>> getByCandidateInfoId(@PathVariable("candidateInfoId") Long candidateInfoId,
                                                                             @PathVariable("jobAdId") Long jobAdId) {
        return ResponseUtils.success(emailLogService.getByCandidateInfoId(candidateInfoId, jobAdId));
    }
}
