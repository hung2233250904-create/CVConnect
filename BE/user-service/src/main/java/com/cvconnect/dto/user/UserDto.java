package com.cvconnect.dto.user;

import com.cvconnect.dto.orgMember.OrgMemberDto;
import com.cvconnect.dto.role.RoleDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nmquan.commonlib.dto.BaseDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto extends BaseDto<Instant> {
    private String username;
    private String password;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String address;
    private LocalDate dateOfBirth;
    private Long avatarId;
    private Boolean isEmailVerified;
    private String accessMethod;

    private List<RoleDto> roles;
    private List<UserDetailDto> userDetails;
    private String avatarUrl;
    private Long orgId;
    private String inviter;

    // add
    private List<AccessMethodDto> accessMethods;
    private OrgMemberDto orgMember;
    private String dateOfBirthStr;
    private String verifyEmailStr;
    private String activeStr;
    private String rolesStr;
    private String createdAtStr;
    private String updatedAtStr;

    public UserDto configResponse() {
        this.setIsDeleted(null);
        this.setPassword(null);
        return this;
    }
}
