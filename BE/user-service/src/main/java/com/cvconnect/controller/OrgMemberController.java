package com.cvconnect.controller;

import com.cvconnect.constant.Messages;
import com.cvconnect.dto.common.AssignRoleRequest;
import com.cvconnect.dto.common.InviteUserRequest;
import com.cvconnect.dto.common.ReplyInviteUserRequest;
import com.cvconnect.dto.orgMember.FailedRollbackUpdateAccountStatus;
import com.cvconnect.dto.orgMember.OrgMemberDto;
import com.cvconnect.dto.orgMember.OrgMemberFilter;
import com.cvconnect.service.OrgMemberService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import nmquan.commonlib.annotation.InternalRequest;
import nmquan.commonlib.constant.MessageConstants;
import nmquan.commonlib.dto.request.ChangeStatusActiveRequest;
import nmquan.commonlib.dto.response.FilterResponse;
import nmquan.commonlib.dto.response.Response;
import nmquan.commonlib.utils.LocalizationUtils;
import nmquan.commonlib.utils.ResponseUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/org-member")
public class OrgMemberController {
    @Autowired
    private OrgMemberService orgMemberService;
    @Autowired
    private LocalizationUtils localizationUtils;

    @PostMapping("/invite-join-org")
    @Operation(summary = "Invite user to join organization")
    @PreAuthorize("hasAnyAuthority('ORG_MEMBER:ADD')")
    public ResponseEntity<Response<Void>> inviteUserToJoinOrg(@Valid @RequestBody InviteUserRequest request) {
        orgMemberService.inviteUserToJoinOrg(request);
        return ResponseUtils.success(null, localizationUtils.getLocalizedMessage(Messages.SEND_INVITE_SUCCESS));
    }

    @PostMapping("/reply-invite-join-org")
    @Operation(summary = "Reply invite join organization")
    public ResponseEntity<Response<Void>> replyInviteJoinOrg(@Valid @RequestBody ReplyInviteUserRequest request) {
        orgMemberService.replyInviteJoinOrg(request);
        return ResponseUtils.success(null);
    }

    @GetMapping("/filter")
    @Operation(summary = "Filter organization members")
    @PreAuthorize("hasAnyAuthority('ORG_MEMBER:VIEW', 'HR')")
    public ResponseEntity<Response<FilterResponse<OrgMemberDto>>> filterOrgMembers(@Valid @ModelAttribute OrgMemberFilter request) {
        return ResponseUtils.success(orgMemberService.filter(request));
    }

    @InternalRequest
    @GetMapping("/internal/valid-org-member")
    @Operation(summary = "Check if the current user is a valid organization member")
    public ResponseEntity<Response<Long>> isValidOrgMember() {
        return ResponseUtils.success(orgMemberService.validOrgMember());
    }

    @PutMapping("/assign-role")
    @Operation(summary = "Assign role to organization member")
    @PreAuthorize("hasAnyAuthority('ORG_MEMBER:ADD', 'ORG_MEMBER:UPDATE')")
    public ResponseEntity<Response<Void>> assignRoleOrgMember(@Valid @RequestBody AssignRoleRequest request) {
        orgMemberService.assignRoleOrgMember(request);
        return ResponseUtils.success(null, localizationUtils.getLocalizedMessage(MessageConstants.UPDATE_SUCCESSFULLY));
    }

    @PutMapping("change-status-active")
    @Operation(summary = "Change Status Active Org Member")
    @PreAuthorize("hasAnyAuthority('ORG_MEMBER:ADD', 'ORG_MEMBER:UPDATE')")
    public ResponseEntity<Response<Void>> changeStatusActive(@Valid @RequestBody ChangeStatusActiveRequest request) {
        orgMemberService.changeStatusActive(request);
        return ResponseUtils.success(null, localizationUtils.getLocalizedMessage(MessageConstants.UPDATE_SUCCESSFULLY));
    }

    @GetMapping("/org-member-info/{userId}")
    @Operation(summary = "Get organization member info by user ID")
    @PreAuthorize("hasAnyAuthority('ORG_MEMBER:VIEW')")
    public ResponseEntity<Response<OrgMemberDto>> getOrgMemberInfo(@PathVariable("userId") Long userId) {
        return ResponseUtils.success(orgMemberService.orgMemberInfo(userId));
    }

    @PostMapping("/internal/check-org-member")
    @Operation(summary = "Internal check organization member by user ID")
    @InternalRequest
    public ResponseEntity<Response<Boolean>> checkOrgMember(@RequestBody List<Long> userIds) {
        return ResponseUtils.success(orgMemberService.checkOrgMember(userIds));
    }

    @PostMapping("/internal/update-account-status-by-org-ids")
    @Operation(summary = "Internal update account status by organization IDs")
    @InternalRequest
    public ResponseEntity<Response<Void>> updateAccountStatusByOrgIds(@Valid @RequestBody ChangeStatusActiveRequest request) {
        orgMemberService.updateAccountStatusByOrgIds(request);
        return ResponseUtils.success(null);
    }

    @PostMapping("/internal/rollback-update-account-status-by-org-ids")
    @Operation(summary = "Internal update account status by organization IDs")
    @InternalRequest
    public ResponseEntity<Response<Void>> rollbackUpdateAccountStatusByOrgIds(@Valid @RequestBody FailedRollbackUpdateAccountStatus request) {
        orgMemberService.rollbackUpdateAccountStatusByOrgIds(request);
        return ResponseUtils.success(null);
    }

    @GetMapping("/org-member-by-org")
    @Operation(summary = "Get organization members by organization ID")
    @PreAuthorize("hasAnyAuthority('ORG:VIEW', 'SYSTEM_ADMIN')")
    public ResponseEntity<Response<FilterResponse<OrgMemberDto>>> getOrgMembersByOrgId(@Valid @ModelAttribute OrgMemberFilter request) {
        return ResponseUtils.success(orgMemberService.filterBySystemAdmin(request));
    }
}
