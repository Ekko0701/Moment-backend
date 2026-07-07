package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.AppleLoginRequest;
import com.moment.adapter.in.web.dto.EmailLoginRequest;
import com.moment.adapter.in.web.dto.EmailSignupRequest;
import com.moment.adapter.in.web.dto.RefreshRequest;
import com.moment.adapter.in.web.dto.TokenResponse;
import com.moment.application.port.in.AuthUseCase;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthUseCase authUseCase;

    public AuthController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    @PostMapping("/apple")
    ApiResponse<TokenResponse> loginWithApple(@Valid @RequestBody AppleLoginRequest request) {
        AuthUseCase.LoginResult result =
                authUseCase.loginWithApple(request.identityToken(), request.nickname());
        return ApiResponse.ok(TokenResponse.from(result));
    }

    @PostMapping("/email/signup")
    ApiResponse<TokenResponse> signUpWithEmail(@Valid @RequestBody EmailSignupRequest request) {
        AuthUseCase.LoginResult result =
                authUseCase.signUpWithEmail(request.email(), request.password(), request.nickname());
        return ApiResponse.ok(TokenResponse.from(result));
    }

    @PostMapping("/email/login")
    ApiResponse<TokenResponse> loginWithEmail(@Valid @RequestBody EmailLoginRequest request) {
        AuthUseCase.TokenPair tokens = authUseCase.loginWithEmail(request.email(), request.password());
        return ApiResponse.ok(TokenResponse.from(tokens));
    }

    @PostMapping("/refresh")
    ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(TokenResponse.from(authUseCase.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(@AuthenticationPrincipal UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        authUseCase.logout(userId);
        return ApiResponse.ok();
    }
}
