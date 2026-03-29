package com.cvconnect.controller;

import com.cvconnect.dto.candidateSummaryOrg.CandidateSummaryOrgRequest;
import com.cvconnect.service.CandidateSummaryOrgService;
import jakarta.validation.Valid;
import nmquan.commonlib.constant.MessageConstants;
import nmquan.commonlib.dto.response.IDResponse;
import nmquan.commonlib.dto.response.Response;
import nmquan.commonlib.utils.LocalizationUtils;
import nmquan.commonlib.utils.ResponseUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/candidate-summary-org")
public class CandidateSummaryOrgController {
    @Autowired
    private CandidateSummaryOrgService candidateSummaryOrgService;
    @Autowired
    private LocalizationUtils localizationUtils;

    @PostMapping("/save-summary")
    @PreAuthorize("hasAnyAuthority('ORG_CANDIDATE:UPDATE', 'ORG_ADMIN', 'HR')")
    public ResponseEntity<Response<IDResponse<Long>>> saveSummary(@Valid @RequestBody CandidateSummaryOrgRequest request) {
        return ResponseUtils.success(candidateSummaryOrgService.saveSummary(request),
                localizationUtils.getLocalizedMessage(MessageConstants.UPDATE_SUCCESSFULLY));
    }
}
