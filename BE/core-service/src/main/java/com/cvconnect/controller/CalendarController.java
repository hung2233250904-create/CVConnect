package com.cvconnect.controller;

import com.cvconnect.dto.calendar.*;
import com.cvconnect.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import nmquan.commonlib.constant.MessageConstants;
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
@RequestMapping("calendar")
public class CalendarController {
    @Autowired
    private CalendarService calendarService;
    @Autowired
    private LocalizationUtils localizationUtils;

    @PostMapping("/create")
    @Operation(summary = "Create Calendar", description = "Create a new calendar entry")
    @PreAuthorize("hasAnyAuthority('ORG_CALENDAR:ADD', 'ORG_ADMIN', 'HR')")
    public ResponseEntity<Response<IDResponse<Long>>> createCalendar(@Valid @RequestBody CalendarRequest request) {
        return ResponseUtils.success(calendarService.createCalendar(request), localizationUtils.getLocalizedMessage(MessageConstants.CREATE_SUCCESSFULLY));
    }

    @PostMapping("/create-simple")
    @Operation(summary = "Create Calendar (Simple)", description = "Create a new calendar entry using simplified form")
    @PreAuthorize("hasAnyAuthority('ORG_CALENDAR:ADD', 'ORG_ADMIN', 'HR')")
    public ResponseEntity<Response<IDResponse<Long>>> createSimpleCalendar(@Valid @RequestBody SimpleCalendarRequest request) {
        return ResponseUtils.success(calendarService.createSimpleCalendar(request), localizationUtils.getLocalizedMessage(MessageConstants.CREATE_SUCCESSFULLY));
    }

    @GetMapping("/filter-view-candidate")
    @Operation(summary = "Filter View Candidate Calendars", description = "Filter calendars for viewing by candidates")
    @PreAuthorize("hasAnyAuthority('ORG_CALENDAR:VIEW')")
    public ResponseEntity<Response<List<CalendarFitterViewCandidateResponse>>> filterViewCandidateCalendars(@Valid @ModelAttribute CalendarFilterRequest request) {
        return ResponseUtils.success(calendarService.filterViewCandidateCalendars(request));
    }

    @GetMapping("/detail-in-view-candidate/{calendarCandidateInfoId}")
    @Operation(summary = "Detail in View Candidate Calendars", description = "Get detailed calendars for viewing by candidates")
    @PreAuthorize("hasAnyAuthority('ORG_CALENDAR:VIEW')")
    public ResponseEntity<Response<CalendarDetail>> detailInViewCandidate(@PathVariable Long calendarCandidateInfoId) {
        return ResponseUtils.success(calendarService.detailInViewCandidate(calendarCandidateInfoId));
    }

    @GetMapping("/filter-view-general")
    @Operation(summary = "Filter View General Calendars", description = "Filter calendars for general viewing")
    @PreAuthorize("hasAnyAuthority('ORG_CALENDAR:VIEW')")
    public ResponseEntity<Response<List<CalendarFilterResponse>>> filterViewGeneral(@Valid @ModelAttribute CalendarFilterRequest request) {
        return ResponseUtils.success(calendarService.filterViewGeneral(request));
    }

    @PostMapping("/detail-in-view-general")
    @Operation(summary = "Detail in View General Calendars", description = "Get detailed calendars for general viewing")
    @PreAuthorize("hasAnyAuthority('ORG_CALENDAR:VIEW')")
    public ResponseEntity<Response<CalendarDetail>> detailInViewGeneral(@Valid @RequestBody CalendarDetailInViewGeneralRequest request) {
        return ResponseUtils.success(calendarService.detailInViewGeneral(request));
    }
}
