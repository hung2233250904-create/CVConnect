package com.cvconnect.service.impl;

import com.cvconnect.common.ReplacePlaceholder;
import com.cvconnect.common.RestTemplateClient;
import com.cvconnect.constant.Constants;
import com.cvconnect.dto.calendar.*;
import com.cvconnect.dto.candidateInfoApply.CandidateInfoApplyDto;
import com.cvconnect.dto.common.DataReplacePlaceholder;
import com.cvconnect.dto.common.NotificationDto;
import com.cvconnect.dto.enums.CalendarTypeDto;
import com.cvconnect.dto.internal.response.EmailConfigDto;
import com.cvconnect.dto.internal.response.EmailTemplateDto;
import com.cvconnect.dto.internal.response.UserDto;
import com.cvconnect.dto.interviewPanel.InterviewPanelDto;
import com.cvconnect.dto.jobAd.JobAdDto;
import com.cvconnect.dto.jobAd.JobAdProcessDto;
import com.cvconnect.dto.org.OrgAddressDto;
import com.cvconnect.entity.Calendar;
import com.cvconnect.enums.*;
import com.cvconnect.repository.CalendarRepository;
import com.cvconnect.service.*;
import com.cvconnect.utils.CoreServiceUtils;
import nmquan.commonlib.constant.CommonConstants;
import nmquan.commonlib.dto.SendEmailDto;
import nmquan.commonlib.dto.response.IDResponse;
import nmquan.commonlib.exception.AppException;
import nmquan.commonlib.exception.CommonErrorCode;
import nmquan.commonlib.service.SendEmailService;
import nmquan.commonlib.utils.DateUtils;
import nmquan.commonlib.utils.KafkaUtils;
import nmquan.commonlib.utils.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CalendarServiceImpl implements CalendarService {
    @Autowired
    private CalendarRepository calendarRepository;
    @Autowired
    private RestTemplateClient restTemplateClient;
    @Autowired
    private InterviewPanelService interviewPanelService;
    @Autowired
    private CalendarCandidateInfoService calendarCandidateInfoService;
    @Autowired
    private JobAdProcessService jobAdProcessService;
    @Autowired
    private CandidateInfoApplyService candidateInfoApplyService;
    @Autowired
    private SendEmailService sendEmailService;
    @Autowired
    private ReplacePlaceholder replacePlaceholder;
    @Autowired
    private JobAdService jobAdService;
    @Autowired
    private JobAdCandidateService jobAdCandidateService;
    @Autowired
    private OrgAddressService orgAddressService;
    @Autowired
    private KafkaUtils kafkaUtils;

    @Override
    @Transactional
    public IDResponse<Long> createCalendar(CalendarRequest request) {
        Long orgId = restTemplateClient.validOrgMember();
        Long userId = WebUtils.getCurrentUserId();
        this.validateCreateCalendar(request, orgId);

        Calendar calendar = new Calendar();
        calendar.setJobAdProcessId(request.getJobAdProcessId());
        calendar.setCalendarType(request.getCalendarType().name());
        calendar.setJoinSameTime(request.isJoinSameTime());
        calendar.setDate(request.getDate());
        calendar.setTimeFrom(request.getTimeFrom());
        calendar.setDurationMinutes(request.getDurationMinutes());
        calendar.setOrgAddressId(request.getOrgAddressId());
        calendar.setMeetingLink(request.getMeetingLink());
        calendar.setNote(request.getNote());
        calendar.setCreatorId(userId);
        calendarRepository.save(calendar);

        // save interview panels
        List<InterviewPanelDto> interviewPanels = request.getParticipantIds().stream()
                .map(id -> {
                    InterviewPanelDto panel = new InterviewPanelDto();
                    panel.setCalendarId(calendar.getId());
                    panel.setInterviewerId(id);
                    return panel;
                }).toList();
        interviewPanelService.create(interviewPanels);

        // save calendar candidate info
        LocalDate date = request.getDate();
        LocalTime timeFrom = request.getTimeFrom();
        int durationMinutes = request.getDurationMinutes();
        boolean joinSameTime = request.isJoinSameTime();
        List<Long> candidateInfoIds = request.getCandidateInfoIds();

        List<CalendarCandidateInfoDto> calendarCandidates = new ArrayList<>();
        for (int i = 0; i < candidateInfoIds.size(); i++) {
            CalendarCandidateInfoDto calendarCandidate = new CalendarCandidateInfoDto();

            LocalDateTime startDateTime = LocalDateTime.of(date, timeFrom)
                    .plusMinutes(joinSameTime ? 0L : (long) i * durationMinutes);
            LocalDateTime endDateTime = startDateTime.plusMinutes(durationMinutes);

            calendarCandidate.setCalendarId(calendar.getId());
            calendarCandidate.setCandidateInfoId(candidateInfoIds.get(i));
            calendarCandidate.setDate(startDateTime.toLocalDate());
            calendarCandidate.setTimeFrom(startDateTime.toLocalTime());
            calendarCandidate.setTimeTo(endDateTime.toLocalTime());

            calendarCandidates.add(calendarCandidate);
        }
        calendarCandidateInfoService.create(calendarCandidates);

        // send email (candidate)
        Map<Long, CandidateInfoApplyDto> candidateInfos = candidateInfoApplyService.getByIds(candidateInfoIds);
        if (request.isSendEmail()) {
            String subject;
            String template;
            List<String> placeholders;

            // get email template
            Long emailTemplateId = request.getEmailTemplateId();
            if(emailTemplateId != null){
                EmailTemplateDto emailTemplateDto = restTemplateClient.getEmailTemplateById(emailTemplateId);
                if(ObjectUtils.isEmpty(emailTemplateDto)){
                    throw new AppException(CoreErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
                }
                subject = emailTemplateDto.getSubject();
                template = emailTemplateDto.getBody();
                placeholders = emailTemplateDto.getPlaceholderCodes();
            } else {
                CoreServiceUtils.validateManualEmail(request.getSubject(), request.getTemplate());
                subject = request.getSubject();
                template = request.getTemplate();
                placeholders = request.getPlaceholders();
            }

            JobAdDto jobAd = jobAdService.findByJobAdProcessId(request.getJobAdProcessId());
            UserDto userDto = restTemplateClient.getUser(userId);
            for(CalendarCandidateInfoDto calendarCandidate : calendarCandidates) {
                CandidateInfoApplyDto candidateInfo = candidateInfos.get(calendarCandidate.getCandidateInfoId());
                if(candidateInfo == null){
                    continue;
                }
                DataReplacePlaceholder dataReplacePlaceholder = DataReplacePlaceholder.builder()
                        .positionId(jobAd.getPositionId())
                        .jobAdName(jobAd.getTitle())
                        .jobAdProcessId(request.getJobAdProcessId())
                        .interviewLink(request.getMeetingLink())
                        .orgId(orgId)
                        .candidateName(candidateInfo.getFullName())
                        .hrName(userDto.getFullName())
                        .hrEmail(userDto.getEmail())
                        .hrPhone(userDto.getPhoneNumber())
                        .examStartTime(
                                CoreServiceUtils.convertLocalDateTimeToInstant(
                                    LocalDateTime.of(calendarCandidate.getDate(), calendarCandidate.getTimeFrom()),
                                    CommonConstants.ZONE.HCM,
                                    CommonConstants.ZONE.UTC
                                )
                        )
                        .examEndTime(
                                CoreServiceUtils.convertLocalDateTimeToInstant(
                                    LocalDateTime.of(calendarCandidate.getDate(), calendarCandidate.getTimeTo()),
                                    CommonConstants.ZONE.HCM,
                                    CommonConstants.ZONE.UTC
                                )
                        )
                        .examDuration(request.getDurationMinutes())
                        .locationId(request.getOrgAddressId())
                        .build();
                String body = replacePlaceholder.replacePlaceholder(template, placeholders, dataReplacePlaceholder);
                SendEmailDto sendEmailDto = SendEmailDto.builder()
                        .sender(userDto.getEmail())
                        .recipients(List.of(candidateInfo.getEmail()))
                        .subject(subject)
                        .body(body)
                        .candidateInfoId(candidateInfo.getId())
                        .jobAdId(jobAd.getId())
                        .orgId(orgId)
                        .emailTemplateId(emailTemplateId)
                        .build();
                sendEmailService.sendEmailWithBody(sendEmailDto);
            }
        }

        // send notify (to interviewer)
        JobAdDto jobAdDto = jobAdService.findByJobAdProcessId(request.getJobAdProcessId());
        if(joinSameTime){
            String timeRange = this.formatTimeRange(calendar.getDate(), calendar.getTimeFrom(), calendar.getDurationMinutes());
            NotifyTemplate template = NotifyTemplate.CREATED_SCHEDULED;
            NotificationDto notifyDto = NotificationDto.builder()
                    .title(String.format(template.getTitle(), jobAdDto.getTitle()))
                    .message(String.format(template.getMessage(), request.getCalendarType().getDisplayName(), timeRange))
                    .type(Constants.NotificationType.USER)
                    .redirectUrl(Constants.Path.ORG_CALENDAR)
                    .senderId(userId)
                    .receiverIds(request.getParticipantIds())
                    .receiverType(MemberType.ORGANIZATION.getName())
                    .build();
            kafkaUtils.sendWithJson(Constants.KafkaTopic.NOTIFICATION, notifyDto);
        } else {
            for(CalendarCandidateInfoDto candidateInfo : calendarCandidates) {
                String timeRange = this.formatTimeRange(candidateInfo.getDate(), candidateInfo.getTimeFrom(), candidateInfo.getTimeTo());
                NotifyTemplate template = NotifyTemplate.CREATED_SCHEDULED;
                NotificationDto notifyDto = NotificationDto.builder()
                        .title(String.format(template.getTitle(), jobAdDto.getTitle()))
                        .message(String.format(template.getMessage(), request.getCalendarType().getDisplayName(), timeRange))
                        .type(Constants.NotificationType.USER)
                        .redirectUrl(Constants.Path.ORG_CALENDAR)
                        .senderId(userId)
                        .receiverIds(request.getParticipantIds())
                        .receiverType(MemberType.ORGANIZATION.getName())
                        .build();
                kafkaUtils.sendWithJson(Constants.KafkaTopic.NOTIFICATION, notifyDto);
            }
        }

        return IDResponse.<Long>builder()
                .id(calendar.getId())
                .build();
    }

    @Override
    public List<CalendarFitterViewCandidateResponse> filterViewCandidateCalendars(CalendarFilterRequest request) {
        if(request.getJobAdCandidateId() == null){
            throw new AppException(CoreErrorCode.JOB_AD_NOT_FOUND);
        }
        Long orgId = restTemplateClient.validOrgMember();
        Long currentUserId = WebUtils.getCurrentUserId();
        Boolean existsByJobAdCandidateIdAndOrgId = jobAdCandidateService.existsByJobAdCandidateIdAndOrgId(request.getJobAdCandidateId(), orgId);
        if(!existsByJobAdCandidateIdAndOrgId){
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }
        List<String> roles = WebUtils.getCurrentRole();
        Long creatorId = null;
        Long participantId = null;
        Long participantIdAuth = null;

        if (!roles.contains(Constants.RoleCode.ORG_ADMIN)) {
            boolean isContactPerson = jobAdCandidateService
                    .existsByJobAdCandidateIdAndHrContactId(request.getJobAdCandidateId(), currentUserId);
            if (!isContactPerson) {
                participantIdAuth = currentUserId;
            }
        }
        if(!ObjectUtils.isEmpty(request.getParticipationType())){
            switch (request.getParticipationType()) {
                case CREATED_BY_ME -> creatorId = currentUserId;
                case JOINED_BY_ME -> participantId = currentUserId;
            }
        }

        List<CalendarFilterViewCandidateProjection> projections = calendarRepository
                .filterViewCandidateCalendars(request, creatorId, participantId, participantIdAuth);

        Set<Long> creatorIds = projections.stream()
                .map(CalendarFilterViewCandidateProjection::getCreatorId)
                .collect(Collectors.toSet());
        Map<Long, UserDto> creators = restTemplateClient.getUsersByIds(new ArrayList<>(creatorIds));

        Map<LocalDate, List<CalendarFilterViewCandidateProjection>> groupedByDate = projections.stream()
                .collect(Collectors.groupingBy(CalendarFilterViewCandidateProjection::getDate));

        List<CalendarFitterViewCandidateResponse> responses = groupedByDate.entrySet().stream()
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    List<CalendarFilterViewCandidateProjection> items = entry.getValue();

                    List<CalendarViewCandidateDetail> details = items.stream()
                            .map(p -> {
                                CalendarViewCandidateDetail detail = new CalendarViewCandidateDetail();
                                detail.setCalendarId(p.getCalendarId());
                                detail.setCalendarCandidateInfoId(p.getCalendarCandidateInfoId());
                                detail.setTimeFrom(p.getTimeFrom());
                                detail.setTimeTo(p.getTimeTo());

                                JobAdProcessDto jobAdProcess = new JobAdProcessDto();
                                jobAdProcess.setId(p.getJobAdProcessId());
                                jobAdProcess.setName(p.getJobAdProcessName());
                                detail.setJobAdProcess(jobAdProcess);

                                detail.setCreator(creators.get(p.getCreatorId()));

                                CalendarTypeDto calendarType = CalendarType.getCalendarTypeDto(p.getCalendarType());
                                detail.setCalendarType(calendarType);

                                return detail;
                            })
                            .toList();

                    CalendarFitterViewCandidateResponse response = new CalendarFitterViewCandidateResponse();
                    response.setDate(date);
                    response.setLabelDate(date.format(DateTimeFormatter.ofPattern(Constants.CALENDAR_DATE_VIEW_FORMAT)) + " (" + details.size() + ")");
                    response.setCalendars(details);

                    return response;
                })
                .sorted(Comparator.comparing(CalendarFitterViewCandidateResponse::getDate))
                .toList();

        return responses;
    }

    @Override
    public CalendarDetail detailInViewCandidate(Long calendarCandidateInfoId) {
        Long orgId = restTemplateClient.validOrgMember();
        Long userId = null;
        List<String> roles = WebUtils.getCurrentRole();
        if(!roles.contains(Constants.RoleCode.ORG_ADMIN)){
            userId = WebUtils.getCurrentUserId();
        }

        CalendarDetailInViewCandidateProjection projection = calendarRepository.detailInViewCandidate(calendarCandidateInfoId, orgId, userId);
        if(projection == null){
            throw new AppException(CoreErrorCode.CALENDAR_NOT_FOUND);
        }

        CalendarDetail detail = new CalendarDetail();

        // job ad
        JobAdDto jobAd = new JobAdDto();
        jobAd.setId(projection.getJobAdId());
        jobAd.setTitle(projection.getJobAdTitle());
        detail.setJobAd(jobAd);

        // job ad process
        JobAdProcessDto jobAdProcess = new JobAdProcessDto();
        jobAdProcess.setId(projection.getJobAdProcessId());
        jobAdProcess.setName(projection.getJobAdProcessName());
        detail.setJobAdProcess(jobAdProcess);

        // creator
        UserDto creator = restTemplateClient.getUser(projection.getCreatorId());
        detail.setCreator(creator);

        // calendar type
        CalendarTypeDto calendarType = CalendarType.getCalendarTypeDto(projection.getCalendarType());
        detail.setCalendarType(calendarType);

        // date, timeFrom, timeTo
        detail.setDate(projection.getDate());
        detail.setTimeFrom(projection.getTimeFrom());
        detail.setTimeTo(projection.getTimeTo());

        // location
        if(projection.getLocationId() != null){
            OrgAddressDto location = orgAddressService.getById(projection.getLocationId());
            detail.setLocation(location);
        }

        // meeting link
        detail.setMeetingLink(projection.getMeetingLink());

        // candidates
        List<CandidateInfoApplyDto> candidates = candidateInfoApplyService.getByCalendarId(projection.getCalendarId());
        if(projection.getJoinSameTime()){
            detail.setCandidates(candidates);
        } else {
            CandidateInfoApplyDto candidate = candidates.stream()
                    .filter(c -> Objects.equals(c.getId(), projection.getCandidateInfoId()))
                    .findFirst()
                    .orElseThrow(() -> new AppException(CoreErrorCode.CANDIDATE_INFO_APPLY_NOT_FOUND));
            detail.setCandidates(List.of(candidate));
        }

        // participants
        List<UserDto> participants = interviewPanelService.getByCalendarId(projection.getCalendarId());
        detail.setParticipants(participants);

        return detail;
    }

    @Override
    public List<CalendarFilterResponse> filterViewGeneral(CalendarFilterRequest request) {
        Long orgId = restTemplateClient.validOrgMember();
        Long creatorId = null;
        Long participantId = null;
        Long participantIdAuth = null;

        Long currentUserId = WebUtils.getCurrentUserId();
        List<String> roles = WebUtils.getCurrentRole();
        if(!roles.contains(Constants.RoleCode.ORG_ADMIN)){
            participantIdAuth = currentUserId;
        }
        if(!ObjectUtils.isEmpty(request.getParticipationType())){
            switch (request.getParticipationType()) {
                case CREATED_BY_ME -> creatorId = currentUserId;
                case JOINED_BY_ME -> participantId = currentUserId;
            }
        }

        if(request.getDateFrom() != null){
            request.setDateFrom(request.getDateFrom().plus(7, ChronoUnit.HOURS));
        }
        if(request.getDateTo() != null){
            request.setDateTo(request.getDateTo().plus(7, ChronoUnit.HOURS));
        }
        List<CalendarDetailInViewCandidateProjection> projections = calendarRepository
                .filterViewGeneral(request, orgId, creatorId, participantId, participantIdAuth, currentUserId);

        Set<Long> hrContactIds = projections.stream()
                .map(CalendarDetailInViewCandidateProjection::getHrContactId)
                .collect(Collectors.toSet());
        Map<Long, UserDto> hrContacts = restTemplateClient.getUsersByIds(new ArrayList<>(hrContactIds));

        List<CalendarFilterResponse> responses = projections.stream()
                .collect(Collectors.groupingBy(CalendarDetailInViewCandidateProjection::getDate,
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    List<CalendarDetailInViewCandidateProjection> listByDate = entry.getValue();

                    Map<String, List<CalendarDetailInViewCandidateProjection>> groupedByCalendar =
                            listByDate.stream().collect(Collectors.groupingBy(
                                    p -> p.getCalendarId().toString(),
                                    LinkedHashMap::new,
                                    Collectors.toList()
                            ));

                    List<CalendarFilterDetail> details = groupedByCalendar.values().stream()
                            .flatMap(group -> {
                                CalendarDetailInViewCandidateProjection first = group.get(0);
                                boolean joinSameTime = Boolean.TRUE.equals(first.getJoinSameTime());

                                if (joinSameTime) {
                                    CalendarFilterDetail detail = new CalendarFilterDetail();
                                    detail.setCalendarId(first.getCalendarId());

                                    UserDto hrContact = hrContacts.get(first.getHrContactId());
                                    JobAdDto jobAd = JobAdDto.builder()
                                            .id(first.getJobAdId())
                                            .title(first.getJobAdTitle())
                                            .hrContactId(hrContact.getId())
                                            .hrContactName(hrContact.getFullName())
                                            .build();
                                    detail.setJobAd(jobAd);

                                    CalendarTypeDto calendarType = CalendarType.getCalendarTypeDto(first.getCalendarType());
                                    detail.setCalendarType(calendarType);

                                    detail.setTimeFrom(first.getTimeFrom());
                                    detail.setTimeTo(first.getTimeTo());

                                    List<CandidateInfoApplyDto> candidates = group.stream()
                                            .map(p -> CandidateInfoApplyDto.builder()
                                                    .id(p.getCandidateInfoId())
                                                    .fullName(p.getFullName())
                                                    .build())
                                            .collect(Collectors.toList());
                                    detail.setCandidateInfos(candidates);

                                    return Stream.of(detail);

                                } else {
                                    return group.stream().map(p -> {
                                        CalendarFilterDetail d = new CalendarFilterDetail();
                                        d.setCalendarId(p.getCalendarId());

                                        UserDto hrContact = hrContacts.get(p.getHrContactId());
                                        JobAdDto jobAd = JobAdDto.builder()
                                                .id(p.getJobAdId())
                                                .title(p.getJobAdTitle())
                                                .hrContactId(hrContact.getId())
                                                .hrContactName(hrContact.getFullName())
                                                .build();
                                        d.setJobAd(jobAd);

                                        CalendarTypeDto calendarType = CalendarType.getCalendarTypeDto(p.getCalendarType());
                                        d.setCalendarType(calendarType);

                                        d.setTimeFrom(p.getTimeFrom());
                                        d.setTimeTo(p.getTimeTo());

                                        d.setCandidateInfos(List.of(
                                                CandidateInfoApplyDto.builder()
                                                .id(p.getCandidateInfoId())
                                                .fullName(p.getFullName())
                                                .build()
                                        ));
                                        return d;
                                    });
                                }
                            })
                            .toList();

                    CalendarFilterResponse res = new CalendarFilterResponse();
                    res.setDate(date);
                    res.setLabelDate(date.format(DateTimeFormatter.ofPattern(Constants.CALENDAR_DATE_VIEW_FORMAT)) + " (" + details.size() + ")");
                    res.setDetails(details);
                    return res;
                })
                .toList();

        return responses;
    }

    @Override
    public CalendarDetail detailInViewGeneral(CalendarDetailInViewGeneralRequest request) {
        Long orgId = restTemplateClient.validOrgMember();
        Long currentUserId = WebUtils.getCurrentUserId();

        boolean checkOrgCalendar = calendarRepository.existsByIdAndOrgId(request.getCalendarId(), orgId);
        if(!checkOrgCalendar){
            throw new AppException(CoreErrorCode.CALENDAR_NOT_FOUND);
        }

        List<String> roles = WebUtils.getCurrentRole();
        boolean isOrgAdmin = roles.contains(Constants.RoleCode.ORG_ADMIN);
        boolean isHrContact = calendarRepository.existsByCalendarIdAndHrContactId(request.getCalendarId(), currentUserId);

        List<UserDto> participants = interviewPanelService.getByCalendarId(request.getCalendarId());
        if(!isOrgAdmin && !isHrContact){
            boolean isParticipant = participants.stream()
                    .anyMatch(p -> Objects.equals(p.getId(), currentUserId));
            if(!isParticipant){
                throw new AppException(CommonErrorCode.ACCESS_DENIED);
            }
        }

        Calendar calendar = calendarRepository.findById(request.getCalendarId()).orElseThrow(
                () -> new AppException(CoreErrorCode.CALENDAR_NOT_FOUND)
        );
        if(!calendar.getJoinSameTime()){
            if(request.getCandidateInfoId() == null){
                throw new AppException(CoreErrorCode.CANDIDATE_INFO_APPLY_NOT_FOUND);
            }
        }

        JobAdProcessDto jobAdProcess = jobAdProcessService.getById(calendar.getJobAdProcessId());
        JobAdDto jobAd = jobAdService.findById(jobAdProcess.getJobAdId());
        UserDto creator = restTemplateClient.getUser(calendar.getCreatorId());
        CalendarTypeDto calendarType = CalendarType.getCalendarTypeDto(calendar.getCalendarType());

        CalendarDetail detail = new CalendarDetail();
        detail.setJobAd(jobAd);
        detail.setJobAdProcess(jobAdProcess);
        detail.setCreator(creator);
        detail.setCalendarType(calendarType);
        if (Objects.equals(calendarType.getType(), Constants.OFFLINE)){
            OrgAddressDto location = orgAddressService.getById(calendar.getOrgAddressId());
            detail.setLocation(location);
        } else if(Objects.equals(calendarType.getType(), Constants.ONLINE)){
            detail.setMeetingLink(calendar.getMeetingLink());
        }
        detail.setParticipants(participants);

        if(calendar.getJoinSameTime()){
            List<CandidateInfoApplyDto> candidates = candidateInfoApplyService.getByCalendarId(calendar.getId());
            detail.setCandidates(candidates);
            detail.setDate(calendar.getDate());
            detail.setTimeFrom(calendar.getTimeFrom());

            LocalDateTime startDateTime = LocalDateTime.of(calendar.getDate(), calendar.getTimeFrom());
            LocalDateTime endDateTime = startDateTime.plusMinutes(calendar.getDurationMinutes());
            detail.setTimeTo(endDateTime.toLocalTime());
        } else {
            CandidateInfoApplyDto candidate = candidateInfoApplyService.getById(request.getCandidateInfoId());
            detail.setCandidates(List.of(candidate));

            CalendarCandidateInfoDto calendarCandidate = calendarCandidateInfoService
                    .getByCalendarIdAndCandidateInfoId(calendar.getId(), request.getCandidateInfoId());
            if(calendarCandidate == null){
                throw new AppException(CoreErrorCode.CALENDAR_NOT_FOUND);
            }
            detail.setDate(calendarCandidate.getDate());
            detail.setTimeFrom(calendarCandidate.getTimeFrom());
            detail.setTimeTo(calendarCandidate.getTimeTo());
        }

        return detail;
    }

    private void validateCreateCalendar(CalendarRequest request, Long orgId) {
        // validate jobAdProcessId
        Boolean exists = jobAdProcessService.existByJobAdProcessIdAndOrgId(request.getJobAdProcessId(), orgId);
        if (!exists) {
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }

        // validate calendarType
        if(Objects.equals(request.getCalendarType().getType(), Constants.OFFLINE) && request.getOrgAddressId() == null){
            throw new AppException(CoreErrorCode.MEETING_ADDRESS_NOT_NULL);
        } else if (Objects.equals(request.getCalendarType().getType(), Constants.ONLINE) && ObjectUtils.isEmpty(request.getMeetingLink())) {
            throw new AppException(CoreErrorCode.MEETING_LINK_NOT_NULL);
        }

        // validate date
        if(request.getDate().isBefore(LocalDate.now()) || Objects.equals(request.getDate(), LocalDate.now())){
            throw new AppException(CoreErrorCode.DATE_BEFORE_TODAY);
        }
        if(request.getDurationMinutes() <= 0){
            throw new AppException(CoreErrorCode.DURATION_MINUTES_INVALID);
        }

        // validate participantIds
        Boolean isValidParticipants = restTemplateClient.checkOrgMember(request.getParticipantIds());
        if(!isValidParticipants) {
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        }

        // validate candidateInfoIds
        Boolean isValidCandidateInfo = candidateInfoApplyService.validateCandidateInfoInProcess(request.getCandidateInfoIds(), request.getJobAdProcessId());
        if(!isValidCandidateInfo) {
            throw new AppException(CoreErrorCode.CANDIDATE_INFO_EXISTS_NOT_IN_PROCESS);
        }
    }

    private String formatTimeRange(LocalDate date, LocalTime startTime, int duration) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime endTime = startTime.plusMinutes(duration);
        return String.format(
                "ngày %s từ %s đến %s",
                date.format(dateFormatter),
                startTime.format(timeFormatter),
                endTime.format(timeFormatter)
        );
    }

    private String formatTimeRange(LocalDate date, LocalTime startTime, LocalTime endTime) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        return String.format(
                "ngày %s từ %s đến %s",
                date.format(dateFormatter),
                startTime.format(timeFormatter),
                endTime.format(timeFormatter)
        );
    }

}
