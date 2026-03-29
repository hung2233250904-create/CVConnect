package com.cvconnect.controller;

import com.cvconnect.dto.jobAd.*;
import com.cvconnect.service.JobAdService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import nmquan.commonlib.constant.MessageConstants;
import nmquan.commonlib.dto.request.FilterRequest;
import nmquan.commonlib.dto.response.FilterResponse;
import nmquan.commonlib.dto.response.IDResponse;
import nmquan.commonlib.dto.response.Response;
import nmquan.commonlib.utils.LocalizationUtils;
import nmquan.commonlib.utils.ResponseUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/job-ad")
public class JobAdController {
    @Autowired
    private JobAdService jobAdService;
    @Autowired
    private LocalizationUtils localizationUtils;

    @PostMapping("/create")
    @Operation(summary = "Create Job Ad")
    @PreAuthorize("hasAnyAuthority('ORG_JOB_AD:ADD')")
    public ResponseEntity<Response<IDResponse<Long>>> createJobAd(@Valid @RequestBody JobAdRequest request) {
        return ResponseUtils.success(jobAdService.create(request),
                localizationUtils.getLocalizedMessage(MessageConstants.CREATE_SUCCESSFULLY));
    }

    @GetMapping("/process/{jobAdId}")
    @Operation(summary = "Get Job Ad by Job Ad Process ID")
    @PreAuthorize("hasAnyAuthority('ORG_JOB_AD:VIEW')")
    public ResponseEntity<Response<List<JobAdProcessDto>>> getProcessByJobAdId(@PathVariable("jobAdId") Long jobAdId) {
        return ResponseUtils.success(jobAdService.getProcessByJobAdId(jobAdId));
    }

    @GetMapping("/org/filter")
    @Operation(summary = "Filter Job Ads for Organization")
    @PreAuthorize("hasAnyAuthority('ORG_JOB_AD:VIEW')")
    public ResponseEntity<Response<FilterResponse<JobAdOrgDetailResponse>>> filterJobAdsForOrg(@Valid @ModelAttribute JobAdOrgFilterRequest request) {
        return ResponseUtils.success(jobAdService.filterJobAdsForOrg(request));
    }

    @PutMapping("/update-status/{jobAdId}")
    @Operation(summary = "Update Job Ad Status")
    @PreAuthorize("hasAnyAuthority('ORG_JOB_AD:UPDATE')")
    public ResponseEntity<Response<Void>> updateJobAdStatus(@PathVariable("jobAdId") Long jobAdId, @Valid @RequestBody JobAdStatusRequest request) {
        request.setJobAdId(jobAdId);
        jobAdService.updateJobAdStatus(request);
        return ResponseUtils.success(null, localizationUtils.getLocalizedMessage(MessageConstants.UPDATE_SUCCESSFULLY));
    }

    @PutMapping("/update-public/{jobAdId}")
    @Operation(summary = "Update Job Ad Public Status")
    @PreAuthorize("hasAnyAuthority('ORG_JOB_AD:UPDATE')")
    public ResponseEntity<Response<Void>> updatePublicStatus(@PathVariable("jobAdId") Long jobAdId, @Valid @RequestBody JobAdPublicStatusRequest request) {
        request.setJobAdId(jobAdId);
        jobAdService.updatePublicStatus(request);
        return ResponseUtils.success(null, localizationUtils.getLocalizedMessage(MessageConstants.UPDATE_SUCCESSFULLY));
    }

    @GetMapping("/org/detail/{jobAdId}")
    @Operation(summary = "Get Job Ad by ID for Organization")
    @PreAuthorize("hasAnyAuthority('ORG_JOB_AD:VIEW')")
    public ResponseEntity<Response<JobAdOrgDetailResponse>> getJobAdOrgDetail(@PathVariable("jobAdId") Long jobAdId) {
        return ResponseUtils.success(jobAdService.getJobAdOrgDetail(jobAdId));
    }

    @PutMapping("/update/{jobAdId}")
    @Operation(summary = "Update Job Ad")
    @PreAuthorize("hasAnyAuthority('ORG_JOB_AD:UPDATE')")
    public ResponseEntity<Response<IDResponse<Long>>> updateJobAd(@PathVariable("jobAdId") Long jobAdId, @Valid @RequestBody JobAdUpdateRequest request) {
        request.setId(jobAdId);
        return ResponseUtils.success(jobAdService.update(request), localizationUtils.getLocalizedMessage(MessageConstants.UPDATE_SUCCESSFULLY));
    }

    @GetMapping("/by-participant")
    @Operation(summary = "Get Job Ads by Participant ID")
    @PreAuthorize("hasAnyAuthority('ORG_JOB_AD:VIEW')")
    public ResponseEntity<Response<FilterResponse<JobAdDto>>> getJobAdsByParticipantId(@Valid @ModelAttribute FilterRequest request) {
        return ResponseUtils.success(jobAdService.getJobAdsByParticipantId(request));
    }

    @GetMapping("/outside/data-filter")
    @Operation(summary = "Get data filter for Outside Users")
    public ResponseEntity<Response<JobAdOutsideDataFilter>> outsideDataFilter() {
        return ResponseUtils.success(jobAdService.outsideDataFilter());
    }

    @GetMapping("/outside/filter")
    @Operation(summary = "Filter Job Ads for Outside Users")
    public ResponseEntity<Response<JobAdOutsideFilterResponse<JobAdOutsideDetailResponse>>> filterJobAdsForOutside(@Valid @ModelAttribute JobAdOutsideFilterRequest request) {
        return ResponseUtils.success(jobAdService.filterJobAdsForOutside(request));
    }

    @GetMapping("/outside/detail/{jobAdId}")
    @Operation(summary = "Get Job Ad Detail for Outside Users")
    public ResponseEntity<Response<JobAdOutsideDetailResponse>> detailOutside(@PathVariable("jobAdId") Long jobAdId,
                                                                              @RequestParam(name = "keyCodeInternal", required = false) String keyCodeInternal) {
        return ResponseUtils.success(jobAdService.detailOutside(jobAdId, keyCodeInternal));
    }

    @GetMapping("/outside/relate/{jobAdId}")
    @Operation(summary = "Get Related Job Ads for Outside Users")
    public ResponseEntity<Response<List<JobAdOutsideDetailResponse>>> listRelateOutside(@PathVariable("jobAdId") Long jobAdId) {
        return ResponseUtils.success(jobAdService.listRelateOutside(jobAdId));
    }

    @GetMapping("/outside/filter-featured")
    @Operation(summary = "Filter Default Featured Job Ads for Outside Users")
    public ResponseEntity<Response<FilterResponse<JobAdOutsideDetailResponse>>> filterFeaturedOutside(@ModelAttribute FilterRequest request) {
        return ResponseUtils.success(jobAdService.filterFeaturedOutside(request));
    }

    @GetMapping("/outside/filter-suitable")
    @Operation(summary = "Filter Default Suitable Job Ads for Outside Users")
    public ResponseEntity<Response<FilterResponse<JobAdOutsideDetailResponse>>> filterSuitableOutside(@ModelAttribute FilterRequest request) {
        return ResponseUtils.success(jobAdService.filterSuitableOutside(request));
    }
}
