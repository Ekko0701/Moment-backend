package com.moment.adapter.in.web;

import com.moment.adapter.in.web.dto.AppleLoginRequest;
import com.moment.adapter.in.web.dto.EmailLoginRequest;
import com.moment.adapter.in.web.dto.EmailSignupRequest;
import com.moment.adapter.in.web.dto.RefreshRequest;
import com.moment.adapter.in.web.dto.TokenResponse;
import com.moment.adapter.in.web.security.AuthRateLimiter;
import com.moment.application.port.in.AuthUseCase;
import com.moment.common.exception.BusinessException;
import com.moment.common.exception.ErrorCode;
import com.moment.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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
    private final AuthRateLimiter rateLimiter;

    public AuthController(AuthUseCase authUseCase, AuthRateLimiter rateLimiter) {
        this.authUseCase = authUseCase;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/apple")
    ApiResponse<TokenResponse> loginWithApple(@Valid @RequestBody AppleLoginRequest request,
            HttpServletRequest http) {
        rateLimiter.check("apple:" + clientIp(http));
        AuthUseCase.LoginResult result =
                authUseCase.loginWithApple(request.identityToken(), request.nickname());
        return ApiResponse.ok(TokenResponse.from(result));
    }

    @PostMapping("/email/signup")
    ApiResponse<TokenResponse> signUpWithEmail(@Valid @RequestBody EmailSignupRequest request,
            HttpServletRequest http) {
        rateLimiter.check("signup:" + clientIp(http));
        AuthUseCase.LoginResult result =
                authUseCase.signUpWithEmail(request.email(), request.password(), request.nickname());
        return ApiResponse.ok(TokenResponse.from(result));
    }

    @PostMapping("/email/login")
    ApiResponse<TokenResponse> loginWithEmail(@Valid @RequestBody EmailLoginRequest request,
            HttpServletRequest http) {
        // IP와 계정 이메일 양쪽으로 제한해 무차별 로그인·BCrypt CPU 소진을 완화한다.
        rateLimiter.check("login-ip:" + clientIp(http));
        rateLimiter.check("login-email:" + request.email());
        AuthUseCase.TokenPair tokens = authUseCase.loginWithEmail(request.email(), request.password());
        return ApiResponse.ok(TokenResponse.from(tokens));
    }

    @PostMapping("/refresh")
    ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request,
            HttpServletRequest http) {
        rateLimiter.check("refresh:" + clientIp(http));
        return ApiResponse.ok(TokenResponse.from(authUseCase.refresh(request.refreshToken())));
    }

    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return http.getRemoteAddr();
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
