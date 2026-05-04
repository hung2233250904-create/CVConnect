package com.cvconnect.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import nmquan.commonlib.model.BaseEntity;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "calendar")
public class Calendar extends BaseEntity {
    @Column(name = "job_ad_process_id")
    private Long jobAdProcessId;

    @Size(max = 100)
    @NotNull
    @Column(name = "calendar_type", nullable = false, length = 100)
    private String calendarType;

    @ColumnDefault("false")
    @Column(name = "join_same_time")
    private Boolean joinSameTime;

    @NotNull
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @NotNull
    @Column(name = "time_from", nullable = false)
    private LocalTime timeFrom;

    @NotNull
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "org_address_id")
    private Long orgAddressId;

    @Size(max = 500)
    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Column(name = "note", length = Integer.MAX_VALUE)
    private String note;


    @NotNull
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

}