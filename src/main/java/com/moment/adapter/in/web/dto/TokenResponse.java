package com.moment.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moment.application.port.in.AuthUseCase.LoginResult;
import com.moment.application.port.in.AuthUseCase.TokenPair;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(String accessToken, String refreshToken, Boolean isNewUser) {

    public static TokenResponse from(LoginResult result) {
        return new TokenResponse(result.tokens().accessToken(), result.tokens().refreshToken(),
                result.isNewUser());
    }

    public static TokenResponse from(TokenPair tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), null);
    }
}
