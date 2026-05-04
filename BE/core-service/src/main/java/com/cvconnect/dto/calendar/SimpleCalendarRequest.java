package com.cvconnect.dto.calendar;

import com.cvconnect.constant.Messages;
import com.cvconnect.enums.CalendarType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simplified calendar request DTO for HR UI - used for creating interview schedules
 * Maps to full CalendarRequest with sensible defaults
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SimpleCalendarRequest {
    @NotNull(message = "Loại lịch là bắt buộc")
    private CalendarType type;

    private String meetingLink;
    private String address;

    @NotNull(message = "Ngày & giờ phỏng vấn là bắt buộc")
    private String startTime;

    private String interviewerId;

    private Long candidateId;
}
