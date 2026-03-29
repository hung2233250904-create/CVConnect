package com.cvconnect.controller;

import com.cvconnect.constant.Messages;
import com.cvconnect.dto.auth.*;
import com.cvconnect.enums.UserErrorCode;
import com.cvconnect.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import nmquan.commonlib.dto.response.Response;
import nmquan.commonlib.exception.AppException;
import nmquan.commonlib.exception.ErrorCode;
import nmquan.commonlib.utils.JwtUtils;
import nmquan.commonlib.utils.LocalizationUtils;
import nmquan.commonlib.utils.ResponseUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private AuthService authService;
    @Autowired
    private LocalizationUtils localizationUtils;

    // public API
    @PostMapping("/login")
    @Operation(summary = "Login API", description = "Authenticate user and return access token")
    public ResponseEntity<Response<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest,
                                                         HttpServletResponse httpServletResponse) {
        return ResponseUtils.success(authService.login(loginRequest, httpServletResponse),
                localizationUtils.getLocalizedMessage(Messages.LOGIN_SUCCESS));
    }

    // public API
    @PostMapping("/refresh")
    @Operation(summary = "Refresh Token API")
    public ResponseEntity<Response<RefreshTokenResponse>> refreshToken(HttpServletRequest httpServletRequest
            , HttpServletResponse httpServletResponse) {
        return ResponseUtils.success(authService.refreshToken(httpServletRequest, httpServletResponse));
    }

    // public API
    @PostMapping("/logout")
    @Operation(summary = "Logout API")
    public ResponseEntity<Response<Void>> logout(HttpServletRequest httpServletRequest
            , HttpServletResponse httpServletResponse) {
        authService.logout(httpServletRequest, httpServletResponse);
        return ResponseUtils.success(null);
    }

    // public API
    @PostMapping("/register-candidate")
    @Operation(summary = "Register Candidate API")
    public ResponseEntity<Response<RegisterCandidateResponse>> registerCandidate(@Valid @RequestBody RegisterCandidateRequest request) {
        return ResponseUtils.success(authService.registerCandidate(request),
                localizationUtils.getLocalizedMessage(Messages.REGISTER_SUCCESS));
    }

    // public API
    @PostMapping("/register-org-admin")
    @Operation(summary = "Register Org Admin API")
    public ResponseEntity<Response<RegisterOrgAdminResponse>> registerOrgAdmin(@Valid @RequestPart("request") RegisterOrgAdminRequest request,
                                                                               @RequestPart(value = "logo", required = false) MultipartFile logo,
                                                                               @RequestPart(value = "coverPhoto", required = false) MultipartFile coverPhoto) {
        if(logo == null) {
            throw new AppException(UserErrorCode.LOGO_REQUIRE);
        }
        return ResponseUtils.success(authService.registerOrgAdmin(request, logo, coverPhoto),
                localizationUtils.getLocalizedMessage(Messages.REGISTER_SUCCESS));
    }

    // public API
    @PostMapping("/verify")
    @Operation(summary = "Verify Token API")
    public ResponseEntity<Response<VerifyResponse>> verify(@RequestBody VerifyRequest verifyRequest) {
        return ResponseUtils.success(authService.verify(verifyRequest));
    }

    // public API
    @PostMapping("/verify-token")
    @Operation(summary = "Verify Token API")
    public ResponseEntity<Response<VerifyResponse>> verifyToken(HttpServletRequest httpServletRequest) {
        String token = JwtUtils.getToken(httpServletRequest);
        VerifyRequest verifyRequest = new VerifyRequest();
        verifyRequest.setToken(token);
        return ResponseUtils.success(authService.verify(verifyRequest));
    }

    // public API
    @GetMapping("/request-resend-verify-email")
    @Operation(summary = "Request Resend Verify Email API")
    public ResponseEntity<Response<RequestResendVerifyEmailResponse>> requestResendVerifyEmail(@RequestParam("identifier") String identifier) {
        return ResponseUtils.success(authService.requestResendVerifyEmail(identifier),
                localizationUtils.getLocalizedMessage(Messages.REQUEST_RESEND_VERIFY_EMAIL_SUCCESS));
    }

    // public API
    @PutMapping("/verify-email/{token}")
    @Operation(summary = "Verify Email API")
    public ResponseEntity<Response<VerifyEmailResponse>> verifyEmail(@PathVariable("token") String token) {
        return ResponseUtils.success(authService.verifyEmail(token), localizationUtils.getLocalizedMessage(Messages.VERIFY_EMAIL_SUCCESS));
    }

    // public API
    @GetMapping("/request-reset-password")
    @Operation(summary = "Request Reset Password API")
    public ResponseEntity<Response<RequestResetPasswordResponse>> requestResetPassword(@RequestParam("identifier") String identifier) {
        return ResponseUtils.success(authService.requestResetPassword(identifier),
                localizationUtils.getLocalizedMessage(Messages.REQUEST_RESET_PASSWORD_SUCCESS));
    }

    // public API
    @PutMapping("/reset-password")
    @Operation(summary = "Reset Password API")
    public ResponseEntity<Response<ResetPasswordResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        return ResponseUtils.success(authService.resetPassword(resetPasswordRequest),
                localizationUtils.getLocalizedMessage(Messages.RESET_PASSWORD_SUCCESS));
    }
}
