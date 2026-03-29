package com.cvconnect.controller;

import com.cvconnect.constant.Messages;
import com.cvconnect.dto.role.MemberTypeDto;
import com.cvconnect.dto.role.RoleDto;
import com.cvconnect.dto.role.RoleFilterRequest;
import com.cvconnect.dto.role.RoleRequest;
import com.cvconnect.enums.MemberType;
import com.cvconnect.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import nmquan.commonlib.constant.MessageConstants;
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
@RequestMapping("/role")
public class RoleController {
    @Autowired
    private RoleService roleService;
    @Autowired
    private LocalizationUtils localizationUtils;

    @GetMapping("/member-type")
    @Operation(summary = "Get all member types")
    @PreAuthorize("hasAnyAuthority('USER_GROUP:VIEW')")
    public ResponseEntity<Response<List<MemberTypeDto>>> getAllMemberTypes() {
        return ResponseUtils.success(roleService.getAllMemberTypes());
    }

    @PostMapping("/create")
    @Operation(summary = "Create roles")
    @PreAuthorize("hasAnyAuthority('USER_GROUP:ADD')")
    public ResponseEntity<Response<IDResponse<Long>>> createRoles(@Valid @RequestBody RoleRequest request) {
        return ResponseUtils.success(roleService.createRoles(request), localizationUtils.getLocalizedMessage(Messages.CREATE_ROLES_SUCCESS));
    }

    @PutMapping("/update/{id}")
    @Operation(summary = "Update roles")
    @PreAuthorize("hasAnyAuthority('USER_GROUP:UPDATE')")
    public ResponseEntity<Response<IDResponse<Long>>> updateRoles(@PathVariable("id") Long id, @Valid @RequestBody RoleRequest request) {
        request.setId(id);
        return ResponseUtils.success(roleService.updateRoles(request), localizationUtils.getLocalizedMessage(MessageConstants.UPDATE_SUCCESSFULLY));
    }

    @GetMapping("/filter")
    @Operation(summary = "Filter roles")
    @PreAuthorize("hasAnyAuthority('USER_GROUP:VIEW')")
    public ResponseEntity<Response<FilterResponse<RoleDto>>> filter(@Valid @ModelAttribute RoleFilterRequest request) {
        return ResponseUtils.success(roleService.filter(request));
    }

    @GetMapping("/get-member-type-organization")
    @Operation(summary = "Get role member type organization")
    @PreAuthorize("hasAnyAuthority('ORG_MEMBER:VIEW', 'ORGANIZATION')")
    public ResponseEntity<Response<List<RoleDto>>> getRoleMemberTypeOrganization() {
        return ResponseUtils.success(roleService.getByMemberType(MemberType.ORGANIZATION));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Delete roles")
    @PreAuthorize("hasAnyAuthority('USER_GROUP:DELETE')")
    public ResponseEntity<Response<Void>> delete(@RequestBody List<Long> ids) {
        roleService.deleteByIds(ids);
        return ResponseUtils.success(null, localizationUtils.getLocalizedMessage(MessageConstants.DELETE_SUCCESSFULLY));
    }

    @GetMapping("/detail/{id}")
    @Operation(summary = "Get role detail")
    @PreAuthorize("hasAnyAuthority('USER_GROUP:VIEW')")
    public ResponseEntity<Response<RoleDto>> getDetail(@PathVariable("id") Long id) {
        return ResponseUtils.success(roleService.getDetail(id));
    }
}
