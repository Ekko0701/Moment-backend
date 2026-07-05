package com.moment.application.port.in;

import java.util.UUID;

public interface AuthUseCase {

    /** Apple 로그인 — 미가입 사용자는 자동 가입(핸들 자동 생성) 후 토큰을 발급한다 (F-01). */
    LoginResult loginWithApple(String identityToken, String nickname);

    /** 리프레시 토큰 회전 — 기존 토큰 폐기 후 새 쌍을 발급한다 (F-01-04). */
    TokenPair refresh(String refreshToken);

    /** 로그아웃 — 해당 사용자의 리프레시 토큰을 모두 폐기한다 (F-01-06). */
    void logout(UUID userId);

    record LoginResult(TokenPair tokens, boolean isNewUser) {
    }

    record TokenPair(String accessToken, String refreshToken) {
    }
}
