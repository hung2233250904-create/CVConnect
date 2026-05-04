package com.cvconnect.service;

import com.cvconnect.dto.calendar.*;
import nmquan.commonlib.dto.response.IDResponse;

import java.util.List;

public interface CalendarService {
    IDResponse<Long> createCalendar(CalendarRequest request);
    IDResponse<Long> createSimpleCalendar(SimpleCalendarRequest request);
    List<CalendarFitterViewCandidateResponse> filterViewCandidateCalendars(CalendarFilterRequest request);
    CalendarDetail detailInViewCandidate(Long calendarCandidateInfoId);
    List<CalendarFilterResponse> filterViewGeneral(CalendarFilterRequest request);
    CalendarDetail detailInViewGeneral(CalendarDetailInViewGeneralRequest request);
}
